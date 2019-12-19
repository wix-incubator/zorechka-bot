//package com.github.zorechka.bazel
//
//import com.github.zorechka.Dep
//import com.github.zorechka.utils.Resources
//import org.specs2.mutable.Specification
//
//class BazelDepsCheckTest extends Specification {
//  "BazelDepsCheck" should {
//    "parse empty query output correctly" in {
//      BazelDepsCheck.parseQueryOutput(List.empty) === List.empty
//    }
//
//    "parse non-empty query output correctly" in {
//      val expected = List(
//        Dep("org.scalacheck", "scalacheck_2.12", "1.14.0"),
//        Dep("com.github.mpilquist", "simulacrum_2.12", "0.12.0"),
//        Dep("com.google.protobuf","protobuf-java","3.6.1")
//      )
//
//      BazelDepsCheck.parseQueryOutput(Resources.readAllLines("bazel_test_output")) === expected
//    }
//  }
//}
