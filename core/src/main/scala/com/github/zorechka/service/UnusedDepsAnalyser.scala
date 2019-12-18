package com.github.zorechka.service

import java.nio.file.Path

import com.github.zorechka.ForkData
import com.github.zorechka.clients.{BazelClient, BuildPackage, BuildTarget, BuildozerClient}
import zio.{RIO, ZIO}
import zio.console.Console
import zio.console._

case class UnusedDep(target: BuildTarget, dep: String)

case class BuildTargetUnusedDeps(buildPackage: BuildPackage, deps: List[UnusedDep])

trait UnusedDepsAnalyser {
  val unusedDepsAnalyser: UnusedDepsAnalyser.Service
}

object UnusedDepsAnalyser {

  trait Service {
    def findUnused(forkData: ForkData): RIO[BuildozerClient with BazelClient with BuildozerClient with Console, List[BuildTargetUnusedDeps]]
  }

  trait Live extends UnusedDepsAnalyser {
    override val unusedDepsAnalyser: Service = new Service {
      def findUnused(forkData: ForkData): RIO[BuildozerClient with BazelClient with Console, List[BuildTargetUnusedDeps]] = for {
        targets <- BazelClient.allBuildTargets(forkData.forkDir)
        _ <- putStrLn(s"Found ${targets.length} build targets")
        unusedDeps <- ZIO.collectAll(targets.take(5).map(target => checkPackage(forkData.forkDir, target)))
      } yield unusedDeps
    }

    private def checkPackage(forkDir: Path, buildPackage: BuildPackage): RIO[BuildozerClient with BazelClient with Console, BuildTargetUnusedDeps] = for {
      targets <- BuildozerClient.packageDeps(forkDir, buildPackage)
      _ <- putStrLn(s"Found ${targets.length} targets in build package: $buildPackage")
      depsWithUsage <- ZIO.collectAll { targets.flatMap { (target: BuildTarget) =>
        target.deps.map { dep =>
          for {
            _ <- BuildozerClient.deleteDep(forkDir, target, dep)
            isUnused <- BazelClient.buildTarget(forkDir, target).foldM(
              _ => BuildozerClient.addDep(forkDir, target, dep) *> ZIO.succeed(false),
              _ => BuildozerClient.addDep(forkDir, target, dep) *> ZIO.succeed(true)
            )
          } yield isUnused -> UnusedDep(target, dep)
        }
      }}
    } yield BuildTargetUnusedDeps(buildPackage, depsWithUsage.filter(_._1).map(_._2))
  }

  def findUnused(forkData: ForkData): RIO[UnusedDepsAnalyser with BuildozerClient with BazelClient with Console, List[BuildTargetUnusedDeps]] =
    ZIO.accessM[UnusedDepsAnalyser with BuildozerClient with BazelClient with Console](_.unusedDepsAnalyser.findUnused(forkData))
}
