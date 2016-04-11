package com.mread.actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.actor.ActorSelection.toScala

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

import play.api.libs.json._

import com.mread.actor.traits.RouteTrait

case class SendMessage()

object MsgGenerator {
    def props(): Props = Props(new MsgGenerator())
}

class MsgGenerator extends Actor with ActorLogging with RouteTrait {

  // abstract fields inherited from RouteTrait
  override lazy val primaryRouter = getConfigOpt("mread.actorRouter.primary")
  override lazy val failOverRouter = getConfigOpt("mread.actorRouter.failover")
  
  // create a scheduler to send a messages every 10 seconds
  val cancellable = context.system.scheduler.schedule(10 second, 10 seconds, self, SendMessage())
  
  val reqPaths = Array("/saveOrder", "/modifyOrder", "/deleteOrder")
  val secGroups = Array("qa", "admin")
  
  val r = Random

  override def preStart(): Unit = {
    super.preStart()
    log.info(s"starting MsgGenerator...")
  }
  
  override def postStop(): Unit = {
    super.postStop()
    log.info(s"stopped MsgGenerator...")
  }  
  
  def receive = {
  
    case SendMessage() =>
      log.debug("SendMessage received.")
      
      // pick a random target path from the Array above
      val i = r.nextInt(reqPaths.size)
      val j = r.nextInt(secGroups.size)
      
      // create JSON message
      val msg : JsValue = JsObject(Seq(
          "sender" -> JsString(self.path.toString()),
          "target" -> JsString(reqPaths.apply(i)),
          "groups" -> JsArray(Seq(
              JsString("orders"),
              JsString(secGroups.apply(j))
          )),
          "message" -> JsObject(Seq(
            "action" -> JsString("identify")
          ))
      ))
      
      routeAskMessage(msg).map { rMsg =>
        log.info("MsgGenerator received a response:" + rMsg)
      }

    
    case msg : String => 
      log.info("MsgGenerator received:" + msg)
      
    case _      => log.info("MsgGenerator received unknown message")
  }
  
  
}
