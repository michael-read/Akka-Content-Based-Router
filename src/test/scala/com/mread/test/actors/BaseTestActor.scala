package com.mread.test.actors

/*
 * Copyright 2016 Michael W. Read <gitos@twisted-grip.com>
 */

import akka.actor.{Actor, ActorLogging, ActorRef, Status}
import play.api.libs.json.JsValue
import play.api.libs.json.JsValue.jsValueToJsLookup

import play.api.libs.json.{JsObject, JsString, JsValue, JsArray, Json, JsUndefined, JsDefined, JsResultException}

import com.fasterxml.jackson.core.JsonParseException

trait BaseTestActor extends Actor with ActorLogging {
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
          case "getName" =>
            origin ! self.path.toString()
          case "ask" =>
            origin ! self.path.toString() + " says: 'what do you want?'"
          case "tell" =>
            log.info(self.path.toString() + " says: 'tell me:'" + msg)
          case _ =>
        }

      case None =>
        log.info(s"${self.path} received an invalid message.")            
        origin ! Status.Failure(new IllegalStateException("unreconized message received."))
    }
  }
}