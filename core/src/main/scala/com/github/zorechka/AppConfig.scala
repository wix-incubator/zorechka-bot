package com.github.zorechka

import java.util.concurrent.{Executors, ThreadPoolExecutor}

import com.github.zorechka.HasAppConfig.Cfg
import pureconfig.loadConfig
import scalaz.zio.internal.NamedThreadFactory
import scalaz.zio.internal.PlatformLive.ExecutorUtil
import scalaz.zio.{Task, ZIO}

import scala.concurrent.ExecutionContext

case class AppConfig(reposFile: String)

trait HasAppConfig {
  val cfg: Cfg
}

object HasAppConfig {
  trait Cfg {
    val config: Task[AppConfig]
    val blockingCtx: ExecutionContext
  }

  trait Live extends HasAppConfig {
    import pureconfig.generic.auto._

    val cfg: Cfg = new Cfg {
      override val config: Task[AppConfig] = {
        ZIO
          .fromEither(loadConfig[AppConfig])
          .mapError(failures => new IllegalStateException(s"Can't read file: $failures"))
      }

      override val blockingCtx: ExecutionContext = {
        val factory = new NamedThreadFactory("blocking-pool", true)
        ExecutorUtil
          .fromThreadPoolExecutor(_ => Int.MaxValue)(Executors.newCachedThreadPool(factory).asInstanceOf[ThreadPoolExecutor]).asEC
      }
    }
  }
}
