ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.3"

lazy val root = (project in file("."))
  .settings(
    name := "password_test"
  )


val PekkoVersion = "1.1.5"
val PekkoHttpVer = "1.2.0"

libraryDependencies ++= Seq(
  "org.apache.pekko" %% "pekko-actor-typed" % PekkoVersion,
  "org.apache.pekko" %% "pekko-stream" % PekkoVersion,
  "org.apache.pekko" %% "pekko-http" % PekkoHttpVer,
  "org.apache.pekko" %% "pekko-http-spray-json" % PekkoHttpVer,
  "org.apache.pekko" %% "pekko-slf4j" % PekkoVersion,
  "ch.qos.logback" % "logback-classic" % "1.5.18",
  "org.typelevel" %% "cats-core"   % "2.13.0",
  "org.typelevel" %% "cats-effect" % "3.6.3",

  
  // Test dependencies
  "org.apache.pekko" %% "pekko-http-testkit" % PekkoHttpVer % Test,
  "org.apache.pekko" %% "pekko-actor-testkit-typed" % PekkoVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "org.scalatestplus" %% "scalacheck-1-18" % "3.2.19.0" % Test

)