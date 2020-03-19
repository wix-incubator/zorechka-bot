package com.wix.zorechka.clients

import com.wix.zorechka.{Dep, TestHelpers}
import org.http4s.client.Client
import zio.blocking.Blocking
import zio.Task
import zio.test._

object MavenCentralClientSpec extends DefaultRunnableSpec(
  suite("MavenCentralClientTest")(
    testM("return list of deps for an artifact") {

      val env = new Blocking.Live with MavenCentralClient.Live {
        override protected val httpClient: Client[Task] = TestHelpers.httpClient
      }

      val searchDep = Dep("org.scalacheck", "scalacheck_2.12", "1.10.0")

      for {
        result <- env.client.allVersions(searchDep)
      } yield assert(result, Assertion.equalTo(List("1.14.3", "1.14.2", "1.14.1", "1.14.1-RC2",
        "1.14.1-RC1", "1.14.0", "1.13.5", "1.12.6", "1.13.4", "1.11.6").map {
        version => searchDep.copy(version = version)
      }))

    }
  )
)

