package com.github.zorechka

import java.util.concurrent.{Executors, ThreadPoolExecutor}

import pureconfig.loadConfig
import scalaz.zio.internal.NamedThreadFactory
import scalaz.zio.internal.PlatformLive.ExecutorUtil
import scalaz.zio.{Task, ZIO}

import scala.concurrent.ExecutionContext

case class AppConfig(reposFile: String)

trait HasAppConfig {
  val config: Task[AppConfig]

  val blockingCtx: ExecutionContext = {
    val factory = new NamedThreadFactory("blocking-pool", true)
    ExecutorUtil
      .fromThreadPoolExecutor(_ => Int.MaxValue)(Executors.newCachedThreadPool(factory).asInstanceOf[ThreadPoolExecutor]).asEC
  }
}

trait HasAppConfigLive extends HasAppConfig {
  import pureconfig.generic.auto._
  override val config: Task[AppConfig] = ZIO
    .fromEither(loadConfig[AppConfig])
    .mapError(failures => new IllegalStateException(s"Can't read file: $failures"))
}
