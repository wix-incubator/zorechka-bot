package com.wix.zorechka

import com.wix.zorechka.clients.Http4sClient
import org.http4s.client.Client
import zio.{Runtime, Task, ZIO}
import zio.blocking.Blocking
import zio.internal.PlatformLive

object TestHelpers {

  def httpClient: Client[Task] = Runtime(Blocking.Live, PlatformLive.Default)
    .unsafeRunSync(
      ZIO.runtime[Blocking].flatMap { implicit rt =>
        for {
          httpClientReservation <- Http4sClient.newHttpClient.reserve
          httpClient <- httpClientReservation.acquire
        } yield httpClient
      }
    )
    .getOrElse(err => throw err.squash)

}
