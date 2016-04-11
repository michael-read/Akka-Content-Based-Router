package com.mread.test.router

/*
 * Copyright 2016 Michael W. Read <gitos@twisted-grip.com>
 */

import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import akka.testkit.{ TestActors, TestKit, ImplicitSender }

import com.typesafe.config.ConfigFactory

import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import play.api.libs.json.{JsBoolean, JsValue, JsObject, JsNumber, JsString, JsArray, Json, JsPath, JsResult, JsSuccess, JsError, Reads}

import com.mread.routers.ClusterRouter
import com.mread.test.actors.{TestOne, TestOneTwo, TestTwo, TestTwoTwo, TestThree, TestThreeTwo, TestFour, TestFive}
import com.mread.actor.traits.RouteTrait
import com.mread.test.actor.traits.TestTraitActor

class RoutingLogic (_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
  
  implicit val timeout = Timeout(30 seconds)
  
  val config = ConfigFactory.load("test-application.conf")
   
  def this() = this(ActorSystem("TestCluster", ConfigFactory.load("test-application.conf")))
   
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
  
  val router = system.actorOf(ClusterRouter.props(config), "clusterRouter") 
  
  // create worker actors
  system.actorOf(Props[TestOne], "testone")
  system.actorOf(Props[TestOneTwo], "testonetwo")  
  system.actorOf(Props[TestTwo], "testtwo")
  system.actorOf(Props[TestTwoTwo], "testtwotwo")  
  system.actorOf(Props[TestThree], "testthree")
  system.actorOf(Props[TestThreeTwo], "testthreetwo")  
  system.actorOf(Props[TestFour], "testfour")
  system.actorOf(Props[TestFive], "testfive")  
  
  val testTraitActor = system.actorOf(Props[TestTraitActor])
  
  "A Routing Actor" must {
    
    "retrieve the an actor name for the /One target with a QA login" in {
    
      val msg : JsValue = JsObject(Seq(
        "target" -> JsString("/One"),
        "userid" -> JsString("TestRouterLogic"),
        "groups" -> JsArray(Seq(JsString("qa"))),
        "message" -> JsObject(Seq(
          "action" -> JsString("getName")
        ))
      ))    
                      
      router ! msg
      expectMsg("akka://TestCluster/user/testonetwo")      
      
    }

    "retrieve the an actor name for the /One target w/ the default route" in {
    
      val msg : JsValue = JsObject(Seq(
        "target" -> JsString("/One"),
        "userid" -> JsString("TestRouterLogic"),
        "groups" -> JsArray(Seq()),
        "message" -> JsObject(Seq(
          "action" -> JsString("getName")
        ))
      ))    
      
      router ! msg
      expectMsg("akka://TestCluster/user/testone")      
    }
    
    "retrieve the an actor name for the /Four target w/ the default route" in {
    
      val msg : JsValue = JsObject(Seq(
        "target" -> JsString("/Four"),
        "userid" -> JsString("TestRouterLogic"),
        "groups" -> JsArray(Seq()),
        "message" -> JsObject(Seq(
          "action" -> JsString("getName")
        ))
      ))    
      
      router ! msg
      expectMsg("akka://TestCluster/user/testfour")      
    }    
    
    "retrieve the an actor name for the /Five target w/ through TestTraitActor via Tell trait" in {
      
      val msg : JsValue = JsObject(Seq(
        "target" -> JsString("/Five"),
        "userid" -> JsString("TestRouterLogic"),
        "groups" -> JsArray(Seq()),
        "message" -> JsObject(Seq(
          "action" -> JsString("tell")
        ))
      ))    
      
      testTraitActor ! msg
      // no return from tell...
      assert(true)      
  
    }
    
    "retrieve the an actor name for the /Five target w/ through TestTraitActor via Ask trait" in {
    
      val msg : JsValue = JsObject(Seq(
        "target" -> JsString("/Five"),
        "userid" -> JsString("TestRouterLogic"),
        "groups" -> JsArray(Seq()),
        "message" -> JsObject(Seq(
          "action" -> JsString("ask")
        ))
      ))    
      
      val result = Await.result(testTraitActor ? msg, timeout.duration).asInstanceOf[String]   
      assert(result.length() > 0)      
      assert(result.contains("what do you want?"))
      println("testTraitActor response:" + result)
  
    }    
    
  }
}