package com.mread.test.actor.traits

/*
 * Copyright 2016 Michael W. Read <gitos@twisted-grip.com>
 */

import akka.actor.{Actor, ActorLogging, ActorRef, Status, Props}
import akka.event.Logging
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import play.api.libs.json._
import akka.actor.ActorSelection.toScala

import com.fasterxml.jackson.core.JsonParseException

import com.mread.actor.traits.RouteTrait

object TestTraitActor {
    def props(): Props = Props(new TestTraitActor())
}

/**
 * @author michael
 */
class TestTraitActor extends Actor with ActorLogging with RouteTrait {    
  
  // abstract fields inherited from RouteTrait
  override lazy val primaryRouter = getConfigOpt("mread.actorRouter.primary")
  override lazy val failOverRouter = getConfigOpt("mread.actorRouter.failover")
  
  override def preStart(): Unit = {
    super.preStart()
    log.info(s"starting TestTraitActor...")
  }
  
  override def postStop(): Unit = {
    super.postStop()
    log.info(s"stopped TestTraitActor...")
  }
  
  def receive = {
    case msg : JsValue =>
      forwardMessage(sender, msg)
     
    case jsonMsg : String =>
      parseJsonRequest(sender, jsonMsg)
      
    case _ => log.info("TestTraitActor received unknown message type.")
  }
  
  def parseJsonRequest(origin: ActorRef, request : String) = {
    try {
      val jsValue = Json.parse(request)
      forwardMessage(origin, jsValue)
    }
    catch {
      case ex : JsonParseException =>
        origin ! Status.Failure(ex)
    }
  }
  
  def forwardMessage(origin: ActorRef, msg: JsValue) = {
    log.info(s"TestTraitActor received: ${msg}.")      
    val message = (msg \ "message").as[JsValue]
    val action = (message \ "action").asOpt[String]
    action match {
      case Some(action) => 
        action match {
          case "tell" =>
            routeTellMessage(msg)
            
          case "ask" =>
            val result = routeAskMessage(msg)                
            result.map { rMsg =>
              origin ! rMsg
            }
            
          case _ =>
        }

      //  
      case None =>
        origin ! msg
    }
     
  }

  
}
