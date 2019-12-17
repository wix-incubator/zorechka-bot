package com.github.zorechka.clients

import com.github.zorechka.Dep
import org.http4s.{EntityDecoder, Header, Headers, Method, Request, Uri}
import zio.{RIO, Task, ZIO}
import zio.interop.catz._
import io.circe.generic.auto._
import org.http4s.circe.jsonOf

trait MavenCentralClient {
  val client: MavenCentralClient.Service
}

object MavenCentralClient {
  trait Service {
    def allVersions(dep: Dep): RIO[Http4sClient, List[Dep]]
  }

  trait Live extends MavenCentralClient {
    val client = new MavenCentralClient.Service {
      case class Response(response: InnerResponse)
      case class InnerResponse(docs: Seq[Document])
      case class Document(v: String)

      implicit val decoder: EntityDecoder[Task, Response] = jsonOf[Task, Response]

      override def allVersions(dep: Dep): RIO[Http4sClient, List[Dep]] = {
        ZIO.accessM {
          client =>
            val uri = Uri
              .unsafeFromString("http://search.maven.org/solrsearch/select")
              .withQueryParam("rows", "10")
              .withQueryParam("core", "gav")
              .withQueryParam("q", s""" g:"${dep.groupId}" AND a:"${dep.artifactId}" """)
            println(s"Maven search: ${uri.renderString}")

            val request = Request[Task](Method.GET, uri, headers = Headers.of(Header("Accept", "application/json")))

            client.http4sClient.runRequest(request) {
              response => response.as[Response]
            }.map {
              _.response.docs.map(_.v).map(v => Dep(dep.groupId, dep.artifactId, v)).toList
            }
        }
      }
    }
  }
}
