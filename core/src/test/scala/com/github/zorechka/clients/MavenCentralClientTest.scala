package com.github.zorechka.clients

import com.github.zorechka.{Dep, HasAppConfig}
import org.specs2.mutable.Specification
import scalaz.zio.internal.PlatformLive
import scalaz.zio.{DefaultRuntime, Runtime}

class MavenCentralClientTest extends Specification with DefaultRuntime {
  "MavenCentralClient" should {
    "return list of deps for an artifact" in {
      val rt = Runtime(new HasAppConfig.Live with Http4sClient.Live, PlatformLive.Default)

      val searchDep = Dep("org.scalacheck", "scalacheck_2.12", "1.10.0")
      val mavenCentral = new MavenCentralClient.Live[HasAppConfig.Live with Http4sClient.Live] {}
      val call = mavenCentral.client.allVersions(searchDep)

      val result = rt.unsafeRunSync(call)
      result.succeeded === true
      result.fold(_ => List.empty, identity) === List("1.14.0", "1.13.5", "1.12.6", "1.13.4", "1.11.6").map {
        version => searchDep.copy(version = version)
      }
    }
  }
}
