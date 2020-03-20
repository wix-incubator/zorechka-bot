package com.wix.zorechka.service

import java.nio.file.{Files, Path}

import com.wix.zorechka.Dep
import com.wix.zorechka.clients.BuildozerClient.BuildozerClient
import com.wix.zorechka.clients.GithubClient.GithubClient
import com.wix.zorechka.clients.{BuildozerClient, GithubClient}
import zio.console.Console
import zio._

import collection.JavaConverters._

object ResultNotifier {
  type ResultNotifier = Has[Service]

  trait Service {
    def notify(forkDir: Path, updatedDeps: List[Dep], unusedDeps: List[PackageDeps]): RIO[GithubClient with BuildozerClient with Console, Unit]
  }

  val createPullRequest = ZLayer.succeed {
    new Service {
      def notify(forkDir: Path, updatedDeps: List[Dep], unusedDeps: List[PackageDeps]): ZIO[GithubClient with BuildozerClient with Console, Throwable, Unit] = {
        val (depsDesc, branch) = branchName(updatedDeps)

        for {
          _ <- GithubClient.createBranch(forkDir, branch)
          _ <- ZIO.effect(applyDepUpdates(forkDir, updatedDeps))
          _ <- applyUnusedDeps(forkDir, unusedDeps)
          _ <- GithubClient.stageAllChanges(forkDir)
          _ <- GithubClient.commit(forkDir, s"zorechka found new versions for deps: $depsDesc #pr")
          _ <- GithubClient.push(forkDir, branch)
        } yield ()
      }
    }

    def applyUnusedDeps(repoDir: Path, unusedDeps: List[PackageDeps]): RIO[BuildozerClient, List[Unit]] = {
      ZIO.collectAll {
        unusedDeps.flatMap { unusedDep =>
          unusedDep.deps.map { dep =>
            BuildozerClient.deleteDep(repoDir, dep.target, dep.dep)
          }
        }
      }
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


    def branchName(deps: List[Dep]) = {
      val depsSample = deps.map(_.branchKey()).take(3).mkString("_")
      val depsDesc = (if (depsSample.length > 90) depsSample.substring(0, 90) else depsSample) + (if (deps.size > 3) s"_and_${deps.size - 3}_more" else "")
      (depsDesc, s"feature/update-deps-$depsDesc")
    }
  }

  val printPullRequestInfo = ZLayer.succeed {
    new Service {
      override def notify(forkDir: Path, updatedDeps: List[Dep], unusedDeps: List[PackageDeps]): RIO[GithubClient with BuildozerClient with Console, Unit] = {
        ZIO.accessM[Console](_.get putStrLn
          s"""
             |Going to update:
             |${updatedDeps.mkString("\n")}
             |
             |Going to remove:
             |${unusedDeps.mkString("\n")}
             |""".stripMargin)
      }
    }
  }

  def notify(forkDir: Path, updatedDeps: List[Dep], unusedDeps: List[PackageDeps]): ZIO[ResultNotifier with GithubClient with BuildozerClient with Console, Throwable, Unit] =
    ZIO.accessM[ResultNotifier with GithubClient with BuildozerClient  with Console](_.get.notify(forkDir, updatedDeps, unusedDeps))

}