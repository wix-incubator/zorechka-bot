package com.github.zorechka.service

import com.github.zorechka.{Dep, StartApp}
import com.github.zorechka.StartApp.AppEnv
import zio.RIO

trait UnusedDepsAnalyser {
  val unusedDepsAnalyser: UnusedDepsAnalyser.Service
}

object UnusedDepsAnalyser {
  def findUnused(forkData: StartApp.ForkData): RIO[AppEnv, List[Dep]] = ???

  trait Service {

  }

  trait Live extends UnusedDepsAnalyser {
    override val unusedDepsAnalyser: Service = new Service {

    }
  }
}
