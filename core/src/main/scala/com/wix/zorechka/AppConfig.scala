package com.wix.zorechka

import java.util.concurrent.{Executors, ThreadPoolExecutor}

import com.wix.zorechka.HasAppConfig.Cfg
import com.wix.zorechka.utils.concurrent.NamedThreadFactory
import zio.{RIO, Task, ZIO}
import zio.internal.Executor

import scala.concurrent.ExecutionContext

case class AppConfig(reposFile: String, db: DbConfig)

case class DbConfig(url: String, username: String, password: String)

trait HasAppConfig {
  val cfg: Cfg
}

object HasAppConfig {
  trait Cfg {
    val loadConfig: Task[AppConfig]
    val blockingCtx: ExecutionContext
  }

  trait Live extends HasAppConfig {
    import pureconfig.generic.auto._

    val cfg: Cfg = new Cfg {
      override val loadConfig: Task[AppConfig] = Task.effect(pureconfig.loadConfigOrThrow[AppConfig])

      override val blockingCtx: ExecutionContext = {
        val factory = NamedThreadFactory(name = "blocking-pool", daemon = true)
        Executor
          .fromThreadPoolExecutor(_ => Int.MaxValue)(Executors.newCachedThreadPool(factory).asInstanceOf[ThreadPoolExecutor]).asEC
      }
    }
  }

  def loadConfig(): RIO[HasAppConfig, AppConfig] = ZIO.accessM[HasAppConfig](_.cfg.loadConfig)
}
