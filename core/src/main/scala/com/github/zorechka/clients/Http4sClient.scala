package com.github.zorechka.clients

import com.github.zorechka.HasAppConfig
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.{Request, Response}
import scalaz.zio.internal.PlatformLive
import scalaz.zio.interop.catz._
import scalaz.zio.{Runtime, Task, TaskR, ZIO}

import scala.language.higherKinds

trait Http4sClient[-R] {
  val http4sClient: Http4sClient.Service[R]
}

object Http4sClient {
  trait Service[-R] {
    def runRequest[T](req: Request[Task])(handler: Response[Task] => Task[T]): TaskR[R, T]
  }

  trait Live extends Http4sClient[HasAppConfig] {
    val http4sClient = new Http4sClient.Service[HasAppConfig] {

      override def runRequest[T](req: Request[Task])(handler: Response[Task] => Task[T]): TaskR[HasAppConfig, T] = {
        ZIO.accessM {
          cfg =>
            val ec = cfg.cfg.blockingCtx
            implicit val rt: Runtime[Any] = Runtime((), PlatformLive.fromExecutionContext(ec))

            BlazeClientBuilder[Task](ec).resource.use(_.fetch(req)(handler))
        }
      }

    }
  }
}
