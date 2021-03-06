package com.wix.zorechka.clients

import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import zio.blocking.Blocking
import zio.{Task, ZManaged}

import scala.language.higherKinds
import zio.interop.catz._

object Http4sClient {
  def newHttpClient(implicit rt: zio.Runtime[Blocking]): ZManaged[Blocking, Throwable, Client[Task]] = {
    rt.environment.blocking.blockingExecutor.toManaged_.flatMap { implicit blocking =>
      BlazeClientBuilder[Task](blocking.asEC).resource.toManaged
    }
  }
}
