package com.wix.zorechka.clients

import java.nio.file.Path

import com.wix.zorechka.Dep
import com.wix.zorechka.clients.process.RunProcess
import com.wix.zorechka.clients.process.RunProcess.execCmd
import zio.{RIO, Task, ZIO}

case class BuildPackage(value: String, buildFileHash: String)

case class BuildTarget(target: String, deps: List[String])

trait BazelClient {
  val bazelClient: BazelClient.Service
}

object BazelClient {
  trait Service {
    def allBuildTargets(workDir: Path): Task[List[BuildPackage]]
    def buildTarget(workDir: Path, target: BuildTarget): Task[Unit]
    def foundDeps(repoDir: Path): Task[List[Dep]]
  }

  trait Live extends BazelClient {
    override val bazelClient: Service = new Service {
      override def allBuildTargets(workDir: Path): Task[List[BuildPackage]] = for {
        output <- RunProcess.execCmd(List("bazel", "query",  "--noshow_progress", "'...'", "--output", "package"), workDir)
        packs = output.value.dropWhile(_.trim.nonEmpty).tail
        buildFileHash <- ZIO.collectAll(packs.map { pack =>
          RunProcess.execCmd(List("sha256sum", s"$pack/BUILD.bazel"), workDir)
            .map { output =>
              output.value.head.split(" ").head
            }.map { hash =>
              BuildPackage(pack, hash)
            }
        })
      } yield buildFileHash

      override def buildTarget(workDir: Path, target: BuildTarget): Task[Unit] = {
        RunProcess.execCmd(List("bazel", "build", target.target), workDir).unit
      }

      override def foundDeps(repoDir: Path): Task[List[Dep]] = {
        val cmd = List("bazel", "query", "--noimplicit_deps","--keep_going", "deps(kind(scala_library, deps(//...)), 1)", "--output", "build")
        for {
          exec <- execCmd(cmd, repoDir)
        } yield parseQueryOutput(exec.value).filterNot(isIgnored)
      }

      private def parseQueryOutput(lines: List[String]): List[Dep] = {
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
  }

  def allBuildTargets(workDir: Path): ZIO[BazelClient, Throwable, List[BuildPackage]] =
    ZIO.accessM[BazelClient](_.bazelClient.allBuildTargets(workDir))

  def buildTarget(workDir: Path, target: BuildTarget): RIO[BazelClient, Unit] =
    ZIO.accessM[BazelClient](_.bazelClient.buildTarget(workDir, target))

  def foundDeps(repoDir: Path): RIO[BazelClient, List[Dep]] =
    ZIO.accessM[BazelClient](_.bazelClient.foundDeps(repoDir))
}
