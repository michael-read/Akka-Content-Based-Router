package com.mread.actors

/*
 * Copyright 2016 Michael W. Read <gitos@twisted-grip.com>
 */

import akka.actor.{Actor, ActorLogging, ActorRef, Status}
import play.api.libs.json.JsValue
import play.api.libs.json.JsValue.jsValueToJsLookup

import play.api.libs.json.{JsObject, JsString, JsValue, JsArray, Json, JsUndefined, JsDefined, JsResultException}

import com.fasterxml.jackson.core.JsonParseException

trait BaseOrderActor extends Actor with ActorLogging {
  override def preStart(): Unit = {
    super.preStart()
    log.info(s"starting ${self.path}...")
  }
  
  override def postStop(): Unit = {
    super.postStop()
    log.info(s"stopped ${self.path}...")
  }  

  def receive = {
    case msg : JsValue =>
      processMessage(sender, msg)
     
    case jsonMsg : String =>
      parseJsonRequest(sender, jsonMsg)
      
    case _ => log.info("${self.path} received unknown message type.")
  }
  
  def parseJsonRequest(origin: ActorRef, request : String) = {
    try {
      val jsValue = Json.parse(request)
      processMessage(origin, jsValue)
    }
    catch {
      case ex : JsonParseException =>
        origin ! Status.Failure(ex)
    }
  }
  
  def processMessage(origin: ActorRef, msg: JsValue) = {
    log.info(s"${self.path} received: ${msg}.")      
    val message = (msg \ "message").as[JsValue]
    val action = (message \ "action").asOpt[String]
    action match {
      case Some(action) => 
        action match {
          case "identify" =>
            origin ! self.path.toString()
          case _ =>
        }

      case None =>
        log.info(s"${self.path} received an invalid message.")            
        origin ! Status.Failure(new IllegalStateException("unreconized message received."))
    }
  }
}