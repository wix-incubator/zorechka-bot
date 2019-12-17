package com.github.zorechka.clients

import java.nio.file.Path

import com.github.zorechka.clients.process.RunProcess
import zio.{RIO, Task, ZIO}

case class BuildTarget(value: String) extends AnyVal

trait BazelClient {
  val bazelClient: BazelClient.Service
}

object BazelClient {
  trait Service {
    def allBuildTargets(workDir: Path): Task[List[BuildTarget]]
    def buildTarget(workDir: Path, target: BuildTarget): Task[Unit]
  }

  trait Live extends BazelClient {
    override val bazelClient: Service = new Service {
      override def allBuildTargets(workDir: Path): Task[List[BuildTarget]] = for {
        output <- RunProcess.execCmd(List("bazel", "query", "...", "--output", "build"), workDir)
      } yield output.value.map(BuildTarget)

      override def buildTarget(workDir: Path, target: BuildTarget): Task[Unit] = {
        RunProcess.execCmd(List("bazel", "build", target.value), workDir).unit
      }
    }
  }

  def allBuildTargets(workDir: Path): ZIO[BazelClient, Throwable, List[BuildTarget]] =
    ZIO.accessM[BazelClient](_.bazelClient.allBuildTargets(workDir))

  def buildTarget(workDir: Path, target: BuildTarget): RIO[BazelClient, Unit] =
    ZIO.accessM[BazelClient](_.bazelClient.buildTarget(workDir, target))
}
