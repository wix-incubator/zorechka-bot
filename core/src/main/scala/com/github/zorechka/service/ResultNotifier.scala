package com.github.zorechka.service


import java.nio.file.Path

import com.github.zorechka.Dep
import com.github.zorechka.StartApp.AppEnv
import com.github.zorechka.bazel.BazelDepsCheck
import com.github.zorechka.clients.GithubClient
import zio.console.Console
import zio.{RIO, ZIO}

trait ResultNotifier {
  val notifier: ResultNotifier.Service
}

object ResultNotifier {

  trait Service {
    def notify(forkDir: Path, updatedDeps: List[Dep], unusedDeps: List[TargetUnusedDeps]): RIO[AppEnv, Unit]
  }

  trait CreatePullRequest extends ResultNotifier {
    override val notifier: Service = new Service {
      def notify(forkDir: Path, updatedDeps: List[Dep], unusedDeps: List[TargetUnusedDeps]): ZIO[AppEnv, Throwable, Unit] = {
        val (depsDesc, branch) = branchName(updatedDeps)

        for {
          _ <- GithubClient.createBranch(forkDir, branch)
          _ <- ZIO.effect(BazelDepsCheck.applyDepUpdates(forkDir, updatedDeps))
          _ <- GithubClient.stageAllChanges(forkDir)
          _ <- GithubClient.commit(forkDir, s"zorechka found new versions for deps: $depsDesc #pr")
          _ <- GithubClient.push(forkDir, branch)
        } yield ()
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
      override def notify(forkDir: Path, updatedDeps: List[Dep], unusedDeps: List[TargetUnusedDeps]): RIO[AppEnv, Unit] = {
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

  def notify(forkDir: Path, updatedDeps: List[Dep], unusedDeps: List[TargetUnusedDeps]): ZIO[AppEnv, Throwable, Unit] =
    ZIO.accessM[AppEnv](_.notifier.notify(forkDir, updatedDeps, unusedDeps))

}