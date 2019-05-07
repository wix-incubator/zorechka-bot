package com.github.zorechka.clients

import cats.effect.Resource
import com.github.zorechka.HasAppConfig
import org.http4s.client.Client
import org.http4s.{Request, Response}
import org.http4s.client.blaze.BlazeClientBuilder
import scalaz.zio
import scalaz.zio.internal.PlatformLive
import scalaz.zio.{Runtime, Task}

import scala.language.higherKinds
import scalaz.zio.interop.catz._

trait Http4sClient {
  val http4sClient: Http4sClient.Service
}

object Http4sClient {
  trait Service {
    def runRequest[T](req: Request[Task])(handler: Response[Task] => Task[T]): Task[T]
  }

  trait Live extends Http4sClient { this: HasAppConfig =>
    val http4sClient: Service = new Http4sClient.Service {
      implicit val rt: zio.Runtime[Any] = Runtime((), PlatformLive.fromExecutionContext(cfg.blockingCtx))
      private val http4sClient: Resource[Task, Client[Task]] = BlazeClientBuilder[Task](cfg.blockingCtx).resource

      override def runRequest[T](req: Request[Task])(handler: Response[Task] => Task[T]): Task[T] = {
        http4sClient.use(_.fetch(req)(handler))
      }
    }
  }
}
