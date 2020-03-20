package com.wix.zorechka.clients

import com.wix.zorechka.Dep
import org.http4s.{EntityDecoder, Header, Headers, Method, Request, Uri}
import zio.{Has, Task, ZIO, ZLayer}
import zio.interop.catz._
import io.circe.generic.auto._
import org.http4s.circe.jsonOf
import org.http4s.client.Client

object MavenCentralClient {
  type MavenCentralClient = Has[Service]

  trait Service {
    protected val httpClient: Client[Task]
    def allVersions(dep: Dep): Task[List[Dep]]
  }

  final case class Live(httpClient: Client[Task]) extends Service {

    case class Response(response: InnerResponse)
    case class InnerResponse(docs: Seq[Document])
    case class Document(v: String)
    implicit val decoder: EntityDecoder[Task, Response] = jsonOf[Task, Response]

    override def allVersions(dep: Dep): Task[List[Dep]] = {
      ZIO.accessM {
        client =>
          val uri = Uri
            .unsafeFromString("http://search.maven.org/solrsearch/select")
            .withQueryParam("rows", "10")
            .withQueryParam("core", "gav")
            .withQueryParam("q", s""" g:"${dep.groupId}" AND a:"${dep.artifactId}" """)
          println(s"Maven search: ${uri.renderString}")

          val request = Request[Task](Method.GET, uri, headers = Headers.of(Header("Accept", "application/json")))

          httpClient.fetch(request)(response => response.as[Response]).map {
            _.response.docs.map(_.v).map(v => Dep(dep.groupId, dep.artifactId, v)).toList
          }
      }
    }
  }

  val live: ZLayer[Any, Nothing, Has[Live]] = ZLayer.fromFunction {
    case (httpClient: Client[Task]) =>
      Live(httpClient)
  }
}
