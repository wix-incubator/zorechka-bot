package com.github.zorechka

import java.util.concurrent.{Executors, ThreadPoolExecutor}

import com.github.zorechka.HasAppConfig.Cfg
import scalaz.zio.internal.NamedThreadFactory
import scalaz.zio.internal.PlatformLive.ExecutorUtil

import scala.concurrent.ExecutionContext

case class AppConfig(reposFile: String)

trait HasAppConfig {
  val cfg: Cfg
}

object HasAppConfig {
  trait Cfg {
    val config: AppConfig
    val blockingCtx: ExecutionContext
  }

  trait Live extends HasAppConfig {
    import pureconfig.generic.auto._

    val cfg: Cfg = new Cfg {
      override val config: AppConfig = pureconfig.loadConfigOrThrow[AppConfig]

      override val blockingCtx: ExecutionContext = {
        val factory = new NamedThreadFactory(name = "blocking-pool", daemon = true)
        ExecutorUtil
          .fromThreadPoolExecutor(_ => Int.MaxValue)(Executors.newCachedThreadPool(factory).asInstanceOf[ThreadPoolExecutor]).asEC
      }
    }
  }
}
