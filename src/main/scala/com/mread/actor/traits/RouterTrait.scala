package com.mread.actor.traits

/*
 * Copyright 2016 Michael W. Read <gitos@twisted-grip.com>
 */

import play.api.libs.json.JsValue

import akka.actor.{Actor, ActorRef, ActorLogging}
import akka.util.Timeout
import akka.pattern.ask

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Try, Success, Failure}

import com.typesafe.config.ConfigException.Missing

trait RouteTrait extends Actor {
  
  implicit val timeout = Timeout(5 seconds)
  
  // abstract fields: must have override for these in the class
  val primaryRouter: Option[String] 
  val failOverRouter: Option[String]
  
  @volatile var router : Option[ActorRef] = None
  @volatile var foRouter : Option[ActorRef] = None  
  
/**
 * 
 * Helper function to load configuration Option  
 */
  def getConfigOpt(name : String) : Option[String] = {
    try {
      Some(context.system.settings.config.getString(name))
    }
    catch {
      case ex : Missing =>
        None
    }
  }
  
/**
 * INTERNAL API.
 *   
 * find the ActorRef for the named router  
 */
  private def resolveRouter(name : Option[String]) : Future[ActorRef] = {
    name match {
      case Some(rname) =>
        context.system.actorSelection(rname).resolveOne()  
      case None =>
        Future.failed[ActorRef](new IllegalStateException("Invalid router configuration."))
    }
  }

/**
 * INTERNAL API.
 * 
 * Used while yielding the primary router for resolveRouter  
 */
  private def forPrimaryResult(rtr : ActorRef, result : String) : String = {
    router = Some(rtr)
    result
  }

/**
 * INTERNAL API.
 *   
 * Used while yielding the fail-over router for resolveRouter
 */
  private def forFailOverResult(rtr : ActorRef, result : String) : String = {
    foRouter = Some(rtr)
    result
  }  

/**
 * INTERNAL API.
 * 
 * Route the ASK message (Any) through the primary router
 */    
  private def routeOnPrimary(message : Any) : Future[String] = {
    router match { 
      case Some(rtr) =>
        (rtr ? message).mapTo[String]
        
      case None =>
        for {
          rtr <- resolveRouter(primaryRouter)
          result <- (rtr ? message).mapTo[String] 
        } yield forPrimaryResult(rtr, result)
    }    
  }  

/**
 * INTERNAL API.
 * 
 * Route the ASK message (Any) through the fail-over router
 */
  private def routeFailover(message : Any) : Future[String] = {    
    foRouter match { 
      case Some(rtr) =>
        (rtr ? message).mapTo[String]
        
      case None =>
        for {
          rtr <- resolveRouter(failOverRouter)
          result <- (rtr ? message).mapTo[String] 
        } yield forFailOverResult(rtr, result)
    }    
  }
  
/**
 * INTERNAL API.
 * 
 * Route the TELL message (Any) as a tell through the primary router
 */  
  private def tellMessage(router : Option[ActorRef], name : Option[String], message : Any) = {
    router match { 
      case Some(rtr) =>
        rtr ! message
        
      case None =>
        for {
          rtr <- resolveRouter(name)
        } yield rtr ! message
    }             
  }
  
/**
 * Route the ASK message (Any) first through the primary router. If an exception
 * is thrown in the future that's not an IllegalStateException (programming error) then 
 * route the message through the fail-over router.
 */      
  def routeAskMessage(message : Any) : Future[String] = {   
    failOverRouter match {
      case Some(failoverRtr) =>
        routeOnPrimary(message).recoverWith {
          case e: Exception if !e.isInstanceOf[IllegalStateException] =>
            routeFailover(message)
        }
      case None =>
        routeOnPrimary(message)
    }
  }
  
/**
 * Route the TELL message (Any) first through the primary router. If an exception
 * is thrown in the future that's not an IllegalStateException (programming error) then 
 * route the message through the fail-over router.
 */       
  def routeTellMessage(message : Any) {
    failOverRouter match {
      case Some(failoverRtr) =>
       try {
         tellMessage(router, primaryRouter, message)
       }
       catch {
         case e: Exception if !e.isInstanceOf[IllegalStateException] =>
           tellMessage(foRouter, failOverRouter, message)  
       }
      case None =>
        tellMessage(router, primaryRouter, message)
    }   
  }
}