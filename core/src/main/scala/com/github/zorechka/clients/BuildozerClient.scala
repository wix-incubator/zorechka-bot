package com.github.zorechka.clients

import java.nio.file.Path

import com.github.zorechka.clients.process.RunProcess
import zio.{RIO, Task, ZIO}

trait BuildozerClient {
  val buildozerClient: BuildozerClient.Service
}

object BuildozerClient {
  trait Service {
    def targetDeps(workDir: Path, target: BuildTarget): Task[List[String]]
    def deleteDep(workDir: Path, target: BuildTarget, dep: String): Task[Unit]
    def addDep(workDir: Path, target: BuildTarget, dep: String): Task[Unit]
  }

  trait Live extends BuildozerClient {
    override val buildozerClient: Service = new Service {
      override def targetDeps(workDir: Path, target: BuildTarget): Task[List[String]] = for {
        output <- RunProcess.execCmd(List("buildozer", "'print label deps'", target.value), workDir)
      } yield output.value.head.split(" ").map(_.stripPrefix("[").stripSuffix("]")).tail.toList

      override def deleteDep(workDir: Path, target: BuildTarget, dep: String): Task[Unit] = {
        RunProcess.execCmd(List("buildozer", s"'remove deps $dep'", target.value), workDir).unit
      }

      override def addDep(workDir: Path, target: BuildTarget, dep: String): Task[Unit] = {
        RunProcess.execCmd(List("buildozer", s"'add deps $dep'", target.value), workDir).unit
      }
    }
  }

  def targetDeps(workDir: Path, target: BuildTarget): RIO[BuildozerClient, List[String]] =
    ZIO.accessM[BuildozerClient](_.buildozerClient.targetDeps(workDir, target))

  def deleteDep(workDir: Path, target: BuildTarget, dep: String): RIO[BuildozerClient, Unit] =
    ZIO.accessM[BuildozerClient](_.buildozerClient.deleteDep(workDir, target, dep))

  def addDep(workDir: Path, target: BuildTarget, dep: String): RIO[BuildozerClient, Unit] =
    ZIO.accessM[BuildozerClient](_.buildozerClient.addDep(workDir, target, dep))
}
