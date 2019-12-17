package com.github.zorechka.bazel

import java.nio.file.{Files, Path}

import com.github.zorechka.Dep
import com.github.zorechka.clients.process.RunProcess.execCmd
import zio.Task

import collection.JavaConverters._

object BazelDepsCheck {
  def foundDeps(repoDir: Path): Task[List[Dep]] = {
    val cmd = List("bazel", "query", "--noimplicit_deps", "--wix_nocache","--keep_going", "deps(kind(scala_library, deps(//...)), 1)", "--output", "build")
    for {
      exec <- execCmd(cmd, repoDir)
    } yield parseQueryOutput(exec.value).filterNot(isIgnored)
  }

  def applyDepUpdates(repoDir: Path, deps: List[Dep]): Unit = {
    val regex = """artifact = "(.+)",""".r
    deps.foreach { dep =>
      val file = repoDir
        .resolve("third_party")
        .resolve(dep.groupId.replaceAll("\\.", "_") + ".bzl")

      if (file.toFile.exists()) {
        println(s"Rewriting deps for ${file.toAbsolutePath} to $dep")

        val lines = Files.readAllLines(file)
        val result = lines.asScala.map { line =>
          regex.findFirstMatchIn(line) match {
            case Some(m) if line.contains(s"${dep.groupId}:${dep.artifactId}:") =>
              line.replace(m.group(1), s"${dep.groupId}:${dep.artifactId}:${dep.version}")
            case _ => line
          }
        }
        Files.write(file, result.asJava)
      }
    }
  }

  def parseQueryOutput(lines: List[String]): List[Dep] = {
    val regex = """jars = \["@(.+)//:(.+)-(.+)\.jar"\]""".r

    val deps = lines.flatMap { line =>
      regex.findFirstMatchIn(line).map { m =>
        Dep(
          // cleanup group name by replacing '_' with '.' where needed
          m.group(1).replace("_" + m.group(2)
            .replace('.', '_')
            .replace('-', '_'), ""
          ).replace('_', '.'),
          m.group(2),
          m.group(3))
      }
    }

    deps
  }

  // TODO
  private def isIgnored(dep: Dep): Boolean = {
    dep.groupId.startsWith("com.wixpress")
  }
}
