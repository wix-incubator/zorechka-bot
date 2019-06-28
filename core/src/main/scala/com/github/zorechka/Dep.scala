package com.github.zorechka

import org.apache.maven.artifact.versioning.ComparableVersion

case class Dep(groupId: String, artifactId: String, version: String) {
  def mapKey(): String = s"$groupId:$artifactId"
  def branchKey(): String = s"$artifactId-$version"
  def fullKey(): String = s"$artifactId-$version"
}

object Dep {
  implicit val ordering: Ordering[Dep] = Ordering.by[Dep, ComparableVersion] {
    dep => new ComparableVersion(dep.version)
  }
}
