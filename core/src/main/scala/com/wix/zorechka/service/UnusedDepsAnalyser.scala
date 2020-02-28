package com.wix.zorechka.service

import java.nio.file.Path

import com.wix.zorechka.ForkData
import com.wix.zorechka.clients.{BazelClient, BuildPackage, BuildTarget, BuildozerClient}
import com.wix.zorechka.repos.UnusedDepCache
import zio.{RIO, ZIO}
import zio.console.Console
import zio.console._

case class TargetDep(target: BuildTarget, dep: String)

case class PackageDeps(buildPackage: BuildPackage, deps: List[TargetDep])

trait UnusedDepsAnalyser {
  val unusedDepsAnalyser: UnusedDepsAnalyser.Service
}

object UnusedDepsAnalyser {

  trait Service {
    def findUnused(forkData: ForkData): RIO[BuildozerClient with BazelClient with BuildozerClient with Console with UnusedDepCache, List[PackageDeps]]
  }

  trait Live extends UnusedDepsAnalyser {
    override val unusedDepsAnalyser: Service = new Service {
      def findUnused(forkData: ForkData): RIO[BuildozerClient with BazelClient with Console with UnusedDepCache, List[PackageDeps]] = for {
        targets <- BazelClient.allBuildTargets(forkData.forkDir)
        _ <- putStrLn(s"Found ${targets.length} build targets")
        unusedDeps <- ZIO.collectAll(targets.take(5).map(target => checkPackage(forkData.forkDir, target)))
      } yield unusedDeps
    }

    private def checkPackage(forkDir: Path, buildPackage: BuildPackage): RIO[BuildozerClient with BazelClient with Console with UnusedDepCache, PackageDeps] = for {
      targets <- BuildozerClient.packageDeps(forkDir, buildPackage)
      _ <- putStrLn(s"Found ${targets.length} targets in build package: $buildPackage")

      targetWithCacheStatus <- ZIO.foreach(targets) { target =>
        UnusedDepCache.isCached(target.target).map(_ -> target)
      }
      uncachedTargets = targetWithCacheStatus.filter(!_._1).map(_._2)

      depsWithUsage <- ZIO.collectAll {
        for {
          target <- uncachedTargets
          dep <- target.deps
        } yield {
          for {
            _ <- BuildozerClient.deleteDep(forkDir, target, dep)
            isUnused <- BazelClient.buildTarget(forkDir, target).foldM(
              _ => BuildozerClient.addDep(forkDir, target, dep) *> ZIO.succeed(false),
              _ => BuildozerClient.addDep(forkDir, target, dep) *> ZIO.succeed(true)
            )
          } yield isUnused -> TargetDep(target, dep)
        }
      }
    } yield PackageDeps(buildPackage, depsWithUsage.filter(_._1).map(_._2))
  }

  def findUnused(forkData: ForkData): RIO[UnusedDepsAnalyser with BuildozerClient with BazelClient with Console with UnusedDepCache, List[PackageDeps]] =
    ZIO.accessM[UnusedDepsAnalyser with BuildozerClient with BazelClient with Console with UnusedDepCache](_.unusedDepsAnalyser.findUnused(forkData))
}
