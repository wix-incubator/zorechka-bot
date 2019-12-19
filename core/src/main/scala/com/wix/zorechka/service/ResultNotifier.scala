package com.wix.zorechka.service

import java.nio.file.{Files, Path}

import com.wix.zorechka.Dep
import com.wix.zorechka.StartApp.AppEnv
import com.wix.zorechka.clients.{BuildozerClient, GithubClient}
import com.wix.zorechka.clients.BuildozerClient
import zio.console.Console
import zio.{RIO, ZIO}

import collection.JavaConverters._

trait ResultNotifier {
  val notifier: ResultNotifier.Service
}

object ResultNotifier {

  trait Service {
    def notify(forkDir: Path, updatedDeps: List[Dep], unusedDeps: List[BuildTargetUnusedDeps]): RIO[AppEnv, Unit]
  }

  trait CreatePullRequest extends ResultNotifier {
    override val notifier: Service = new Service {
      def notify(forkDir: Path, updatedDeps: List[Dep], unusedDeps: List[BuildTargetUnusedDeps]): ZIO[AppEnv, Throwable, Unit] = {
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

    private def applyUnusedDeps(repoDir: Path, unusedDeps: List[BuildTargetUnusedDeps]): RIO[BuildozerClient, List[Unit]] = {
      ZIO.collectAll {
        unusedDeps.flatMap { unusedDep =>
          unusedDep.deps.map { dep =>
            BuildozerClient.deleteDep(repoDir, dep.target, dep.dep)
          }
        }
      }
    }

    private def applyDepUpdates(repoDir: Path, deps: List[Dep]): Unit = {
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


    private def branchName(deps: List[Dep]) = {
      val depsSample = deps.map(_.branchKey()).take(3).mkString("_")
      val depsDesc = (if (depsSample.length > 90) depsSample.substring(0, 90) else depsSample) + (if (deps.size > 3) s"_and_${deps.size - 3}_more" else "")
      (depsDesc, s"feature/update-deps-$depsDesc")
    }
  }

  trait PrintPullRequestInfo extends ResultNotifier {
    override val notifier: Service = new Service {
      override def notify(forkDir: Path, updatedDeps: List[Dep], unusedDeps: List[BuildTargetUnusedDeps]): RIO[AppEnv, Unit] = {
        ZIO.accessM[Console](_.console.putStrLn(
          s"""
             |Going to update:
             |${updatedDeps.mkString("\n")}
             |
             |Going to remove:
             |${unusedDeps.mkString("\n")}
             |""".stripMargin))
      }
    }
  }

  def notify(forkDir: Path, updatedDeps: List[Dep], unusedDeps: List[BuildTargetUnusedDeps]): ZIO[AppEnv, Throwable, Unit] =
    ZIO.accessM[AppEnv](_.notifier.notify(forkDir, updatedDeps, unusedDeps))

}