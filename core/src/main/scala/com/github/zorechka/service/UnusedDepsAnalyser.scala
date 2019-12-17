package com.github.zorechka.service

import java.nio.file.Path

import com.github.zorechka.ForkData
import com.github.zorechka.clients.{BazelClient, BuildTarget, BuildozerClient}
import zio.{RIO, ZIO}
import zio.console.Console
import zio.console._

case class TargetUnusedDeps(target: BuildTarget, deps: List[String])

trait UnusedDepsAnalyser {
  val unusedDepsAnalyser: UnusedDepsAnalyser.Service
}

object UnusedDepsAnalyser {

  trait Service {
    def findUnused(forkData: ForkData): RIO[BuildozerClient with BazelClient with BuildozerClient with Console, List[TargetUnusedDeps]]
  }

  trait Live extends UnusedDepsAnalyser {
    override val unusedDepsAnalyser: Service = new Service {
      def findUnused(forkData: ForkData): RIO[BuildozerClient with BazelClient with Console, List[TargetUnusedDeps]] = for {
        targets <- BazelClient.allBuildTargets(forkData.forkDir)
        _ <- putStrLn(s"Found ${targets.length} build targets")
        unusedDeps <- ZIO.collectAll(targets.map(target => checkTarget(forkData.forkDir, target)))
      } yield unusedDeps
    }

    private def checkTarget(forkDir: Path, target: BuildTarget): RIO[BuildozerClient with BazelClient with Console, TargetUnusedDeps] = for {
      deps <- BuildozerClient.targetDeps(forkDir, target)
      _ <- putStrLn(s"Found ${deps.length} in build target: $target")
      depsWithUsage <- ZIO.collectAll { deps.map { dep => for {
          _ <- BuildozerClient.deleteDep(forkDir, target, dep)
          isUnused <- BazelClient.buildTarget(forkDir, target).foldM(
            _ => BuildozerClient.addDep(forkDir, target, dep) *> ZIO.succeed(false),
            _ => BuildozerClient.addDep(forkDir, target, dep) *> ZIO.succeed(true)
          )
        } yield isUnused -> dep
      }}
    } yield TargetUnusedDeps(target, depsWithUsage.filter(_._1).map(_._2))
  }

  def findUnused(forkData: ForkData): RIO[UnusedDepsAnalyser with BuildozerClient with BazelClient with Console, List[TargetUnusedDeps]] =
    ZIO.accessM[UnusedDepsAnalyser with BuildozerClient with BazelClient with Console](_.unusedDepsAnalyser.findUnused(forkData))
}
