package com.wix.zorechka.clients

import java.nio.file.Path

import com.wix.zorechka.clients.process.RunProcess
import zio.{Has, RIO, Task, ZIO, ZLayer}

object BuildozerClient {
  type BuildozerClient = Has[Service]

  trait Service {
    def packageDeps(workDir: Path, target: BuildPackage): Task[List[BuildTarget]]
    def deleteDep(workDir: Path, target: BuildTarget, dep: String): Task[Unit]
    def addDep(workDir: Path, target: BuildTarget, dep: String): Task[Unit]
  }

  val live = ZLayer.succeed {
    new Service {
      override def packageDeps(workDir: Path, target: BuildPackage): Task[List[BuildTarget]] = for {
        output <- RunProcess.execCmd(List("buildozer", "print label deps", s"${target.value}:*"), workDir)
      } yield output.value
        .filter(!_.contains("has no attribute"))
        .filter(!_.contains("(missing)"))
        .map(_.split(" ").map(_.stripPrefix("[").stripSuffix("]")).filter(_.nonEmpty).toList).map {
        case x :: xs => BuildTarget(x, xs) // TODO: not exhaustive match
      }

      override def deleteDep(workDir: Path, target: BuildTarget, dep: String): Task[Unit] = {
        RunProcess.execCmd(List("buildozer", s"remove deps $dep", target.target), workDir).unit
      }

      override def addDep(workDir: Path, target: BuildTarget, dep: String): Task[Unit] = {
        RunProcess.execCmd(List("buildozer", s"add deps $dep", target.target), workDir).unit
      }
    }
  }

  def packageDeps(workDir: Path, target: BuildPackage): RIO[BuildozerClient, List[BuildTarget]] =
    ZIO.accessM[BuildozerClient](_.get.packageDeps(workDir, target))

  def deleteDep(workDir: Path, target: BuildTarget, dep: String): RIO[BuildozerClient, Unit] =
    ZIO.accessM[BuildozerClient](_.get.deleteDep(workDir, target, dep))

  def addDep(workDir: Path, target: BuildTarget, dep: String): RIO[BuildozerClient, Unit] =
    ZIO.accessM[BuildozerClient](_.get.addDep(workDir, target, dep))
}
