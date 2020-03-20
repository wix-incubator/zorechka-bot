package com.wix.zorechka

import java.util.concurrent.{Executors, ThreadPoolExecutor}

import com.wix.zorechka.utils.concurrent.NamedThreadFactory
import zio.{Has, RIO, Task, ZIO, ZLayer}
import zio.internal.Executor

import scala.concurrent.ExecutionContext

case class AppConfig(reposFile: String, db: DbConfig)

case class DbConfig(url: String, username: String, password: String)

object HasAppConfig {

  type HasAppConfig = Has[Cfg]

  trait Cfg {
    val loadConfig: Task[AppConfig]
    val blockingCtx: ExecutionContext
  }

  val live = ZLayer.succeed {
    new Cfg {
      override val loadConfig: Task[AppConfig] = Task.effect(pureconfig.loadConfigOrThrow[AppConfig])

      override val blockingCtx: ExecutionContext = {
        val factory = NamedThreadFactory(name = "blocking-pool", daemon = true)
        Executor
          .fromThreadPoolExecutor(_ => Int.MaxValue)(Executors.newCachedThreadPool(factory).asInstanceOf[ThreadPoolExecutor]).asEC
      }
    }
  }

  def loadConfig(): RIO[HasAppConfig, AppConfig] = ZIO.accessM[HasAppConfig](_.get.loadConfig)
}
