package com.github.zorechka.service

import com.github.zorechka.{Dep, ForkData}
import com.github.zorechka.bazel.BazelDepsCheck
import com.github.zorechka.clients.{Http4sClient, MavenCentralClient}
import zio.{RIO, ZIO}
import zio.console.{Console, putStrLn}

trait ThirdPartyDepsAnalyzer {
  val analyzer: ThirdPartyDepsAnalyzer.Service
}

object ThirdPartyDepsAnalyzer {
  trait Service {
    def findLatest(forkData: ForkData): ZIO[MavenCentralClient with Http4sClient with Console, Throwable, List[Dep]]
  }

  trait Live extends ThirdPartyDepsAnalyzer {
    override val analyzer: Service = new Service {
      override def findLatest(forkData: ForkData): ZIO[MavenCentralClient with Http4sClient with Console, Throwable, List[Dep]] = {
        for {
          deps <- BazelDepsCheck.foundDeps(forkData.forkDir)
          _ <- putStrLn(s"Found ${deps.size} in ${forkData.repo}")
          latestVersions <- latestVersions(deps)
          updatedDeps = latestVersions.filter {
            latestDep => deps
              .find(_.mapKey() == latestDep.mapKey())
              .exists(current => isNewer(latestDep, current))
          }
        } yield updatedDeps
      }

      private def latestVersions(deps: Seq[Dep]): ZIO[MavenCentralClient with Http4sClient, Throwable, List[Dep]] = {
        ZIO.foreach(deps)(dep => ZIO.accessM[MavenCentralClient with Http4sClient] {
          _.client.allVersions(dep).map(listOfDeps => if (listOfDeps.isEmpty) dep else listOfDeps.max)
        })
      }

      private def isNewer(latest: Dep, current: Dep): Boolean = Ordering[Dep].gt(latest, current)
    }
  }

  def findLatest(forkData: ForkData): RIO[ThirdPartyDepsAnalyzer with MavenCentralClient with Http4sClient with Console, List[Dep]] = {
    ZIO.accessM[ThirdPartyDepsAnalyzer with MavenCentralClient with Http4sClient with Console](_.analyzer.findLatest(forkData))
  }
}