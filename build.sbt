import sbt.Keys.scalaVersion
import com.github.zorechka.Dependencies._

name := "zorechka-bot"

val settings = Seq(
  scalaVersion := "2.12.6",
  crossScalaVersions := Seq("2.11.12", "2.12.6", "2.13.0-M4"),
  organization := "com.github.zorechka-bot",
  homepage := Some(url("https://github.com/wix-incubator/zorechka-bot")),
  scmInfo := Some(ScmInfo(url("https://github.com/wix-incubator/zorechka-bot"), "git@github.com:wix-incubator/zorechka-bot.git")),
  licenses := Seq("Apache-2.0" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  publishMavenStyle := true
)

resolvers ++= Seq(
  "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
  "Secured Central Repository" at "https://repo1.maven.org/maven2",
  Resolver.sonatypeRepo("snapshots")
)

publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)

// These options will be used for *all* versions.
scalacOptions ++= Seq(
  "-deprecation"
  , "-unchecked"
  , "-encoding", "UTF-8"
  , "-Xlint"
  , "-Xverify"
  , "-feature"
  ,"-Ypartial-unification"
  ,"-Xfatal-warnings"
  , "-language:_"
  //,"-optimise"
)

javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation", "-source", "1.7", "-target", "1.7")

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6")

lazy val core = project
  .settings(settings)
  .settings(libraryDependencies ++= Seq(
    C.mavenArtifact,
    C.config,
    C.scalazZio,
    C.scalazZioInteropCats,
    T.specs2
  ) ++ C.circe_all ++ C.cats_all ++ C.http4s_all)
                                    
lazy val root = project.in(file("."))
  .aggregate(core)
