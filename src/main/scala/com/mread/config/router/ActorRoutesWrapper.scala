package com.mread.config.router

/*
 * Copyright 2016 Michael W. Read <gitos@twisted-grip.com>
 */

import play.api.libs.json.{JsPath, Reads, Writes}
import play.api.libs.functional.syntax._

object ActorRoutesWrapper {
 
  case class ActorRoute (actorName: String, version: String, groups: Option[List[String]], nodePath: Option[String])
  case class ActorPath (path: String, routes: List[ActorRoute])
  case class RouteConfigFile (version: Option[String], notes: Option[String], paths: List[ActorPath])
  
  implicit val actorRouteReads: Reads[ActorRoute] = (
    (JsPath \ "actorName").read[String] and
    (JsPath \ "version").read[String] and
    (JsPath \ "groups").readNullable[List[String]] and
    (JsPath \ "nodePath").readNullable[String]
  )(ActorRoute.apply _)

  implicit val actorRouteWrites: Writes[ActorRoute] = (
    (JsPath \ "actorName").write[String] and
    (JsPath \ "version").write[String] and
    (JsPath \ "groups").writeNullable[List[String]] and
    (JsPath \ "nodePath").writeNullable[String]    
  )(unlift(ActorRoute.unapply))

  implicit val actorPathReads: Reads[ActorPath] = (
    (JsPath \ "path").read[String] and
    (JsPath \ "routes").read[List[ActorRoute]]
  )(ActorPath.apply _)

  implicit val actorPathWrites: Writes[ActorPath] = (
    (JsPath \ "path").write[String] and
    (JsPath \ "routes").write[List[ActorRoute]]   
  )(unlift(ActorPath.unapply))
  
  implicit val configReads: Reads[RouteConfigFile] = (
    (JsPath \ "version").readNullable[String] and
    (JsPath \ "notes").readNullable[String] and
    (JsPath \ "paths").read[List[ActorPath]]
  )(RouteConfigFile.apply _)

  implicit val configWrites: Writes[RouteConfigFile] = (
    (JsPath \ "version").writeNullable[String] and
    (JsPath \ "notes").writeNullable[String] and
    (JsPath \ "paths").write[List[ActorPath]]   
  )(unlift(RouteConfigFile.unapply))  
}
