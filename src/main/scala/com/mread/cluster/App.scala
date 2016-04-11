package com.mread.cluster

import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config

import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.ActorRef

import com.mread.routers.ClusterRouter
import com.mread.actors.SaveOrderActor
import com.mread.actors.SaveOrderActor1
import com.mread.actors.SaveOrderActor2
import com.mread.actors.ModifyOrderActor
import com.mread.actors.ModifyOrderActor1
import com.mread.actors.DeleteOrderActor
import com.mread.actors.DeleteOrderActor1
import com.mread.actors.MsgGenerator

/*
 * Create a cluster with two nodes as default. This example was taken from a Activator template.
 */
object App {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty)
      startup(Seq("2551", "2552"))      
    else
      startup(args)
  }
  
  // create internal cluster router and all required actors  
  def createDefaultActors(config: Config, system: ActorSystem) = {
    system.actorOf(ClusterRouter.props(config), "clusterRouter")          
    system.actorOf(Props[SaveOrderActor], "saveOrder")
    system.actorOf(Props[SaveOrderActor1], "saveOrder1")
    system.actorOf(Props[ModifyOrderActor], "modifyOrder")
    system.actorOf(Props[ModifyOrderActor1], "modifyOrder1")
    system.actorOf(Props[DeleteOrderActor], "deleteOrder")
    system.actorOf(Props[DeleteOrderActor1], "deleteOrder1")
  }

  def startup(ports: Seq[String]): Unit = {

    ports foreach { port =>

      // Override the configuration of the port
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
        withFallback(ConfigFactory.load())

      // Create an Akka system
      val system = ActorSystem("AkkaCluster", config)

      // Create an actor that handles cluster domain events
      system.actorOf(Props[ClusterListener], name = "clusterListener")
      
      port match {
        case "2551" => // primary
          // Create just one message generator for demonstration purposes          
          system.actorOf(MsgGenerator.props(), "msgGenerator")        
          createDefaultActors(config, system)          
        case "2552" => // fail-over
          createDefaultActors(config, system)          
        case "2553" => // introduce new node with latest / greatest version
          system.actorOf(Props[SaveOrderActor2], "saveOrder2")          
      }
            
    }
  }

}


