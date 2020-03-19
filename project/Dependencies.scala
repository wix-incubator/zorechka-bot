package com.github.zorechka
import sbt._

object Dependencies {
  val CatsVersion = "2.0.0"
  val CatsEffectVersion = "2.0.0"
  val CatsMtlVersion = "0.5.0"
  val CirceVersion = "0.10.0-M1"
  val MonixVersion = "3.0.0-M3"
  val ScalaZVersion = "7.3.0-M28"
  val ZIOVersion = "1.0.0-RC17"
  val ZIOInteropVersion = "2.0.0.0-RC10"
  val ShapelessVersion = "2.3.3"
  val FS2Version = "1.0.4"
  val Http4sVersion = "0.20.0-RC1"
  val DoobieVersion = "0.8.6"
  val MavenArtifactVersion = "3.6.0"

  object C {
    val config = "com.github.pureconfig" %% "pureconfig" % "0.10.2"

    // -- Logging --
    val logback = "ch.qos.logback" % "logback-classic" % "1.1.3"
    val scalaloging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"

    // -- json/circe --
    val circe_all: Seq[ModuleID] = Seq(
      "io.circe" %% "circe-core" % CirceVersion,
      "io.circe" %% "circe-generic" % CirceVersion,
      "io.circe" %% "circe-jawn" % CirceVersion,
      "io.circe" %% "circe-yaml" % "0.8.0"
    )

    val cats_all: Seq[ModuleID] = Seq(
      "org.typelevel" %% "cats-core" % CatsVersion,
      "org.typelevel" %% "cats-effect" % CatsEffectVersion,
      "org.typelevel" %% "cats-mtl-core" % CatsMtlVersion
    )

    // fs2
    val fs2 = "co.fs2" %% "fs2-core" % FS2Version
    val monix = "io.monix" %% "monix" % MonixVersion
    val shapeless = "com.chuusai" %% "shapeless" % ShapelessVersion

    // http4s
    val http4sBlazeClient = "org.http4s" %% "http4s-blaze-client" % Http4sVersion
    val http4sCirce = "org.http4s" %% "http4s-circe" % Http4sVersion
    val http4sDsl = "org.http4s" %% "http4s-dsl" % Http4sVersion
    val http4s_all: Seq[ModuleID] = Seq(http4sBlazeClient, http4sCirce, http4sDsl)

    // doobie
    val doobieCore = "org.tpolecat" %% "doobie-core" % DoobieVersion
    val doobieH2 = "org.tpolecat" %% "doobie-h2" % DoobieVersion
    val doobieHikari = "org.tpolecat" %% "doobie-hikari" % DoobieVersion
    val doobie_all: Seq[ModuleID] = Seq(doobieCore, doobieH2, doobieHikari)

    val mysql = "mysql" % "mysql-connector-java" % "5.1.34"

    // scalaz
    val scalaz = "org.scalaz" %% "scalaz-core" % ScalaZVersion
    val scalazZio = "dev.zio" %% "zio" % ZIOVersion
    val scalazZioInteropCats = "dev.zio" %% "zio-interop-cats" % ZIOInteropVersion
    val scalazZioTest = "dev.zio" %% "zio-test" % ZIOVersion % "test"
    val scalazZioTestSbt = "dev.zio" %% "zio-test-sbt" % ZIOVersion % "test"
    
    // maven ver parsing
    val mavenArtifact = "org.apache.maven" % "maven-artifact" % MavenArtifactVersion

    val simulacrum = "com.github.mpilquist" %% "simulacrum" % "0.12.0"
    val amm = "com.lihaoyi" % "ammonite" % "1.1.2" % "test" cross CrossVersion.full

    val flyway = "org.flywaydb" % "flyway-core" % "6.3.1"
  }

  object T {
    val scalacheck = "org.scalacheck" %% "scalacheck" % "1.13.4" % "test"
    val scalatest = "org.scalatest" %% "scalatest" % "3.0.1" % "test"
    val specs2 = "org.specs2"  %% "specs2-core"  % "4.3.3" % "test"
  }
}