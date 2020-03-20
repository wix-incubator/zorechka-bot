package com.wix.zorechka.clients

import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import zio.blocking.Blocking
import zio.{Task, ZIO, ZManaged}

import scala.language.higherKinds
import zio.interop.catz._

object Http4sClient {
  def newHttpClient(implicit rt: zio.Runtime[Blocking]): ZManaged[Blocking, Throwable, Client[Task]] = {
    ZIO.access[Blocking](_.get.blockingExecutor.asEC).toManaged_
      .flatMap { ec =>
        val client = BlazeClientBuilder[Task](executionContext = ec).resource.toManaged

        client
      }
  }
}