package com.wix.zorechka.service

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
        unusedDeps <- ZIO.collectAll(targets.take(5).map(target => checkPackage(forkData, target)))
      } yield unusedDeps
    }

    private def checkPackage(forkData: ForkData, buildPackage: BuildPackage): RIO[BuildozerClient with BazelClient with Console with UnusedDepCache, PackageDeps] = for {
      targets <- BuildozerClient.packageDeps(forkData.forkDir, buildPackage)
      _ <- putStrLn(s"Found ${targets.length} targets in build package: $buildPackage")

      targetWithCacheStatus <- ZIO.foreach(targets) { target =>
        UnusedDepCache.isCached(forkData.repo.url, buildPackage.value, buildPackage.buildFileHash).map(_ -> target)
      }
      uncachedTargets = targetWithCacheStatus.filter(!_._1).map(_._2)

      depsWithUsage <- ZIO.collectAll {
        for {
          target <- uncachedTargets
          dep <- target.deps
        } yield {
          for {
            _ <- BuildozerClient.deleteDep(forkData.forkDir, target, dep)
            isUnused <- BazelClient.buildTarget(forkData.forkDir, target).foldM(
              _ => BuildozerClient.addDep(forkData.forkDir, target, dep) *> ZIO.succeed(false),
              _ => BuildozerClient.addDep(forkData.forkDir, target, dep) *> ZIO.succeed(true)
            )
            _ <- UnusedDepCache.cache(forkData.repo.url, buildPackage.value, buildPackage.buildFileHash)
          } yield isUnused -> TargetDep(target, dep)
        }
      }
    } yield PackageDeps(buildPackage, depsWithUsage.filter(_._1).map(_._2))
  }

  def findUnused(forkData: ForkData): RIO[UnusedDepsAnalyser with BuildozerClient with BazelClient with Console with UnusedDepCache, List[PackageDeps]] =
    ZIO.accessM[UnusedDepsAnalyser with BuildozerClient with BazelClient with Console with UnusedDepCache](_.unusedDepsAnalyser.findUnused(forkData))
}
