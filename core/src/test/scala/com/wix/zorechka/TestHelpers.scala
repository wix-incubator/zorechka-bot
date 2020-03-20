package com.wix.zorechka

import com.wix.zorechka.clients.Http4sClient
import org.http4s.client.Client
import zio.{Runtime, Task, ZIO}
import zio.blocking.Blocking

object TestHelpers {

  def httpClient: Client[Task] = Runtime.default
    .unsafeRunSync(
      ZIO.runtime[Blocking].flatMap { implicit rt =>
        for {
          httpClientReservation <- Http4sClient.newHttpClient.reserve
          httpClient <- httpClientReservation.acquire
        } yield httpClient
      }.provideLayer(Blocking.live)
    )
    .getOrElse(err => throw err.squash)

}
