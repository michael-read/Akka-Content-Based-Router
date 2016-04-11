organization := "com.mread"

name := "Router"

version := "1.0"

scalaVersion := "2.11.7"

resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"

//val akkaVersion = "2.3.12"
val akkaVersion = "2.4.3"
val playVersion = "2.4.4"

javaOptions ++= Seq(
	"-Dcom.sun.management.jmxremote.port=9999",
	"-Dcom.sun.management.jmxremote.authenticate=false",
	"-Dcom.sun.management.jmxremote.ssl=false"
)
libraryDependencies ++= Seq(
	"com.typesafe.play" %% "play-json" % playVersion,
	"com.typesafe.akka" %% "akka-cluster" % akkaVersion,
	"com.github.cb372" %% "scalacache-guava" % "0.6.4",	
	
	// test stuff
	"org.scalatest" %% "scalatest" % "2.2.4" % "test",
	"com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
)
