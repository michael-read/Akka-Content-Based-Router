package com.mread.routers

/*
 * Copyright 2016 Michael W. Read <gitos@twisted-grip.com>
 */

import akka.actor.{Actor, ActorLogging, ActorRef, Props}   
import akka.routing.Router
import akka.event.Logging

import com.typesafe.config.{Config}

import com.mread.config.router.RouterSettings

/*
 * In Akka, routers are wrapped by actors. The ConfigurableRoutingLogic class is loaded
 * with a separate JSON configuration file. The specific configuration file is specified 
 * in the base Akka configuration and passed to the ConfigurableRoutingLogic constructor.
 * 
 */

object ClusterRouter {
    def props(config: Config): Props = Props(new ClusterRouter(config))
}

class ClusterRouter(config: Config) extends Actor with ActorLogging {
  val routingLogic = new ConfigurableRouterLogic(config, context, log, 
                       new RouterSettings(config.getString("mread.clusterRouter.configFile"), 
                         config.getLong("mread.clusterRouter.cacheSize")))
  
  val router = new Router(routingLogic)
    
  override def preStart(): Unit = {
    super.preStart()
    log.info(s"Starting ClusterRouter ...")
  }
  
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    message match {
      case Some(msg) =>
        log.error("Restarting ClusterRouter:" + msg)
      case None =>
        log.error("Restarting ClusterRouter")
    }
    super.preRestart(reason, message)
  }

/*
 * Using the Routing logic loaded with the configuration above, "receive" dynamically routes
 * each message to the correct Actor destination  
 */
  def receive = {
    case msg => router.route(msg, sender())
  }

}