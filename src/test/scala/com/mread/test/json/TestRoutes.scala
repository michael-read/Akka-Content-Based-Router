package com.mread.test.json

/*
 * Copyright 2016 Michael W. Read <gitos@twisted-grip.com>
 */

import test.BaseTests
import com.mread.config.router.ActorRoutesWrapper.RouteConfigFile

import play.api.libs.json.{Json, JsResult, JsSuccess, JsError}

import scala.io._

class TestRoutes extends BaseTests {

    describe("Load TestRoutes.json") {
      val configRoutes = Source.fromFile("src/test/resources/TestRoutes.json").mkString
      
      val jsonConfig = Json.parse(configRoutes)
      
      val configResult : JsResult[RouteConfigFile] = jsonConfig.validate[RouteConfigFile]
      
      it("the configResult must be a JsSuccess") {
        configResult match {
          case s: JsSuccess[RouteConfigFile] =>
            assert( true == true)
            
            val config = Json.toJson(s.get)
            println("result:" + Json.prettyPrint(config))
            
          case e: JsError => 
            println("Validate RouteConfigFile Errors: " + Json.prettyPrint(Json.parse(JsError.toJson(e).toString())))
            assert( true == false)
        }      
      }
      
    }
}