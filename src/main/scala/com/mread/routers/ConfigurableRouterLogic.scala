package com.mread.routers

/*
 * Copyright 2016 Michael W. Read <gitos@twisted-grip.com>
 */

import java.io.File
import java.util.concurrent.atomic.AtomicLong

import com.typesafe.config.{Config, ConfigFactory, ConfigValue, ConfigList, ConfigObject}
import akka.actor.{Actor, ActorContext, ActorRef, SupervisorStrategy, ActorSystem, PoisonPill, Deploy, AddressFromURIString}
import akka.routing.{RoutingLogic, Routee, NoRoutee, ActorRefRoutee, Router} 
import akka.event.{Logging, LoggingAdapter}
import akka.actor.Props
import akka.util.Timeout

import play.api.libs.json.{Json, JsResult, JsSuccess, JsError, JsValue}
import com.fasterxml.jackson.core.JsonParseException

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io._
import scala.collection.mutable.{MutableList, Map, Set}
import scala.collection.immutable.IndexedSeq
import scala.util.{Try, Success, Failure}
import scala.concurrent.Future

import scalacache._
import guava._
import com.google.common.cache.CacheBuilder

import com.mread.config.router.RouterSettings
import com.mread.config.router.ActorRoutesWrapper.{RouteConfigFile, ActorPath, ActorRoute}

case class InvalidConfigRouteException(message: String) extends Exception(message)
case class ActorRouteRef(actorRoute: ActorRoute, var routee : Option[ActorRefRoutee])

@SerialVersionUID(1L)
final class ConfigurableRouterLogic(config: Config, context: ActorContext, log : LoggingAdapter, settings: RouterSettings) extends RoutingLogic {
  
  val cfgFileLocation: String = settings.configFileName
  var cfgLastModified: Long = 0l
  
  var actorConfigRoutes : Map[String, List[ActorRouteRef]] = null
  
  val guavaCache = CacheBuilder.newBuilder().maximumSize(settings.cacheSize).build[String, Object]
  implicit val actorRefCache = ScalaCache(GuavaCache(guavaCache))  
  
  // load of routes on start
  loadRoutees
  
  // use akka scheduler to check for a change in the configuration file
  context.system.scheduler.schedule(1 minute, 1 minute) {
    loadRoutees
  }      

/**
 * INTERNAL API.
 * 
 * method to check for changes in configuration file. If change detected try to parse
 * into proper configuration. If successfully parsed, then look up all ActorRefs for 
 * routing destinations. If all actors are found then replace the existing referenced
 * configuration with new model, and then finally clear the router cache.  
 */  
  private def loadRoutees = {
    try {
      val file : File = new File(cfgFileLocation)
      if (!file.lastModified().equals(cfgLastModified)) {
        getRoutesConfigWrapped(file) match {      
          case Success(routes) =>
            
            findRoutees(routes).map { isSuccess =>
              isSuccess match {
                case true =>
                  this.synchronized {
                    actorConfigRoutes = routes
                    removeAll() // clear the actorRefCache
                  }
                  log.info("successfully loaded configured actor routes...")
                  
                case false => 
                  log.error(s"failed to load ${cfgFileLocation} because actors couldn't be found.")                  
              }
            }

          case Failure(ex) => 
            log.error(ex, s"failed to load ${cfgFileLocation} err1: ${ex.getMessage}")
        }
        
        // update the modified signature regardless of success to keep from repeatedly throwing exceptions in the log.
        this.synchronized {        
          cfgLastModified = file.lastModified()
        }
      }
    }
    catch {
      case InvalidConfigRouteException(msg) => log.error(msg)
    }
  }

/**
 * INTERNAL API.
 * 
 * Map future to Future Try  
 */
  private def futureToFutureTry[T](f: Future[T]): Future[Try[T]] = {
    f.map(Success(_)).recover({case e => Failure(e)})
  }
  
/**
 * INTERNAL API.
 * 
 * Find all ActorRefs associated with a specific "target" destination (actor identifier)  
 */
  private def findRoutees(routes:  Map[String, List[ActorRouteRef]]) : Future[Boolean] = {
    implicit val timeout = Timeout(5 seconds)
    val futures : MutableList[Future[Object]] = MutableList.empty
    
    val keys = routes.keys
    keys foreach { key =>
      val actorRoutes = routes get key
      actorRoutes match {
        case Some(actors) =>
          actors foreach { actorRouteRef =>
        
            actorRouteRef.actorRoute.nodePath match {
              case Some(nodePath) =>
                val f = context.actorSelection(nodePath).resolveOne()
                f onSuccess {
                    case ref => 
                      actorRouteRef.synchronized {
                        actorRouteRef.routee = Some(ActorRefRoutee(ref))
                      }
                      log.info("findRoutees found specified actor for:" + nodePath)
                }
                f onFailure {
                    case ex: Exception => 
                      log.error(ex, "findRoutees couldn't find specified actor for:" + nodePath)
                }
                futures += f
              
              case None => // this shouldn't happen as error should have been detected during parsing
                log.error("nodePath not specified for key:" + key + "actor name:" + actorRouteRef.actorRoute.actorName) 
            }                
          }
          
        case None => // do nothing
      }
    }
        
    // wrap futures with Try
    val listOfFutureTrys = futures.map(futureToFutureTry(_))
    
    // use Future.sequence, to give you a Future[List[Try[T]]]
    val futureListOfTrys = Future.sequence(listOfFutureTrys)
    
    //Then filter:
    
    //val futureListOfSuccesses = futureListOfTrys.map(_.filter(_.isSuccess))
    
    //identify the specific failures:
    val futureListOfFailures = futureListOfTrys.map(_.filter(_.isFailure))   
    futureListOfFailures.map[Boolean] { failures =>
      failures.size == 0
    }
  }
  
/**
 * INTERNAL API.
 * 
 * wrap parsed router configuration with Try  
 */  
  private def getRoutesConfigWrapped(file : File) : Try[Map[String, List[ActorRouteRef]]] = Try(getRoutesConfig(file))  
  
/**
 * INTERNAL API.
 * 
 * read the routes from a configuration file and then use Play's Json parser to convert into JSON  
 */  
  private def getRoutesConfig(file: File) : Map[String, List[ActorRouteRef]] = {
    val configRoutes = Source.fromFile(file).mkString
    val jsonConfig = Json.parse(configRoutes)
    
    val ret = Map[String, List[ActorRouteRef]]()
    
    val configResult : JsResult[RouteConfigFile] = jsonConfig.validate[RouteConfigFile]
    configResult match {
      case s: JsSuccess[RouteConfigFile] =>
        val pathSet = Set[String]() // hold paths - to check for dups
        val paths = s.get.paths

        paths foreach { path => 
          
          // validate that we don't have a duplicate path
          if (pathSet contains path.path) {
            throw InvalidConfigRouteException(s"Duplicate path (${path}) encountered.")
          }
          else {
            pathSet += path.path
          }
          
          val actorNameSet = Set[String]() // hold actorNames - to check for dups
          val versionSet = Set[String]()  // hold versions to detect duplicate versions
          val groupSet = Set[String]()  // hold groups to detect duplicate groups
      
          val pathRoutes : MutableList[ActorRouteRef] = MutableList.empty
          
          path.routes foreach { actorRoute =>
            
            // validate that we don't have a duplicate actorName
            if (actorNameSet contains actorRoute.actorName) {
              throw InvalidConfigRouteException(s"Duplicate actorName (${actorRoute.actorName}) encountered on reqPath: ${path.path}.")
            }
            else {
              actorNameSet += actorRoute.actorName
            }

            // validate that we don't have a duplicate version #
            if (versionSet contains actorRoute.version) {
              throw InvalidConfigRouteException(s"Duplicate version (${actorRoute.version}) encountered on reqPath: ${path.path}.")
            }
            else {
              versionSet += actorRoute.version
            }

            // validate that we don't have a duplicate group list
            var gString: String = null
            actorRoute.groups match {
              case Some(grps) =>
                gString = grps.toString()
              case None =>
                gString = ""
            }
            if (groupSet contains gString) {
              throw InvalidConfigRouteException(s"Duplicate groups (${gString}) encountered on reqPath: ${path.path}.")
            }
            else {
              groupSet += gString
            }       
            
            // validate that there is a nodePath specified
            actorRoute.nodePath match {
              case Some(np) => // do nothing
              case None =>
                throw InvalidConfigRouteException(s"Missing nodePath encountered on reqPath: ${path.path}.")
            }
            
            pathRoutes += ActorRouteRef(actorRoute, None)
            
            println("actorRoute:" + actorRoute.actorName)
          }
          
          ret += (path.path -> pathRoutes.toList) 
        }
        
      case e: JsError => 
        val err = "Validate RouteConfigFile Errors: " + Json.prettyPrint(Json.parse(JsError.toJson(e).toString()))
        throw InvalidConfigRouteException(err)
    }      
    ret
  }
  
/**
 * INTERNAL API.
 * 
 * used by routing logic to identify which target to use based upon the security groups provided  
 */  
 private def lookupRoutee(routes: List[ActorRouteRef], groups: Option[Array[String]]) : Option[Routee] = {
    var routee : Option[Routee] = None
    var default : Option[Routee] = None
    var matches : Int = 0
    groups match {
      case Some(grps) => 
        routes.foreach { route =>
          route.actorRoute.groups match {
            case Some(g) =>
              if (g.toArray[String].diff(grps).size == 0) {  // all groups in the route must exist in the groups param
                if (g.size > matches) {
                  routee = route.routee
                  matches = g.size
                }
              }
            case None =>
              default = route.routee
          }          
        }
 
        if (routee == None && default != None) {
          routee = default
        }
        else {
          val i = routes.iterator   
          while (i.hasNext && routee == None) {
            val route = i.next()
            route.actorRoute.groups match {
              case Some(g) =>
              case None =>
                routee = route.routee
            }
          }
        }
        
      case None =>  // find the first route that doesn't have any groups defined
        val i = routes.iterator
        while (i.hasNext && routee == None) {
          val route = i.next()
          route.actorRoute.groups match {
            case Some(grp) =>
            case None =>
              routee = route.routee
          }
        }
    }
    routee
  }  
  
/**
 * INTERNAL API.
 * 
 * used by routing logic: using standardized message format this function extracts 
 * the "target" actor, and security groups provided in the message. First, the routing logic
 * checks the router cache to see if this combination as been seen before and used it
 * if available. If not in the cache, then the routing logic try to find the combination
 * in the configuration. If found then the result is cached for the future, and then the 
 * message is routed to it's destination.  
 */   
  private def doFindRoutee(msg: JsValue, routees: IndexedSeq[Routee]): Routee = {
    val target = (msg \ "target").asOpt[String]
    val groups = (msg \ "groups").asOpt[Array[String]]
//  log.debug("doFindRoutee target:" + target.get + " groups:" + groups.get.toString)
    target match {
      case Some(t) =>           
        val routes = actorConfigRoutes get t
        (routes, groups) match {
          case (Some(rs), Some(g)) =>
            // need to block here because we can't return a future       
            val optRoutee : Option[Routee] = getSync(t, g)
            optRoutee match {
              case Some(routee) =>
                log.debug("routee retrieved from cache:" + t)
              routee
            case None =>
              val routee = lookupRoutee(rs, groups)
              routee match {
                case Some(routee) =>
                  put(t, g)(routee)                  
                  log.debug("routee cached:" + t + ":" + g)  
                  routee
                case None =>
                  NoRoutee
              }
            }
          
          case (_, _) =>
            log.info("1 - NoRoutee found for received message to route:" + msg)                 
            NoRoutee              
        }
       
      case None =>
        log.info("0 - NoRoutee found for received message to route:" + msg)             
        NoRoutee    
    }  
  }
  
/*
 * this is the standard API for routing logic. In this case we're supporting two
 * message types. The first is Play's JsValue, and the second is a JSON string.  
 */
  override def select(message: Any, routees: IndexedSeq[Routee]): Routee = {
//  log.debug(this.cfgFileLocation + ": received message to route:" + message)
    message match {
      case msg : JsValue =>
        doFindRoutee(msg, routees)
        
      case msg : String =>
        try {
          val jsValue = Json.parse(msg)
          doFindRoutee(jsValue, routees)
        }
        catch {
          case ex : JsonParseException =>
            log.info("JsonParseException encountered on message received:" + message)
            NoRoutee
        }       

      case _ =>
        log.info("Unknown message type encountered for the message:" + message)
        NoRoutee
    }
  }  
  
}