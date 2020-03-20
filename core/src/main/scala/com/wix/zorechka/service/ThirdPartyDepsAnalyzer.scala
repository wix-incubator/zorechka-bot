package com.wix.zorechka.service

import com.wix.zorechka.clients.BazelClient.BazelClient
import com.wix.zorechka.clients.MavenCentralClient.MavenCentralClient
import com.wix.zorechka.{Dep, ForkData}
import com.wix.zorechka.clients.{BazelClient, MavenCentralClient}
import zio.{Has, RIO, ZIO, ZLayer}
import zio.console.{Console, putStrLn}

object ThirdPartyDepsAnalyzer {
  type ThirdPartyDepsAnalyzer = Has[Service]

  trait Service {
    def findLatest(forkData: ForkData): ZIO[MavenCentralClient with BazelClient with Console, Throwable, List[Dep]]
  }

  val live = ZLayer.succeed {
    new Service {
      override def findLatest(forkData: ForkData): ZIO[MavenCentralClient with BazelClient with Console, Throwable, List[Dep]] = {
        for {
          deps <- BazelClient.foundDeps(forkData.forkDir)
          _ <- putStrLn(s"Found ${deps.size} in ${forkData.repo}")
          latestVersions <- latestVersions(deps)
          updatedDeps = latestVersions.filter {
            latestDep => deps
              .find(_.mapKey() == latestDep.mapKey())
              .exists(current => isNewer(latestDep, current))
          }
        } yield updatedDeps
      }

      private def latestVersions(deps: Seq[Dep]): ZIO[MavenCentralClient, Throwable, List[Dep]] = {
        ZIO.foreach(deps)(dep => ZIO.accessM[MavenCentralClient] {
          _.get.allVersions(dep).map(listOfDeps => if (listOfDeps.isEmpty) dep else listOfDeps.max)
        })
      }

      private def isNewer(latest: Dep, current: Dep): Boolean = Ordering[Dep].gt(latest, current)
    }
  }

  def findLatest(forkData: ForkData): RIO[ThirdPartyDepsAnalyzer with MavenCentralClient with BazelClient with Console, List[Dep]] = {
    ZIO.accessM[ThirdPartyDepsAnalyzer with MavenCentralClient with BazelClient with Console](_.get.findLatest(forkData))
  }
}