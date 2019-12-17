package com.github.zorechka.service


import com.github.zorechka.Dep
import com.github.zorechka.StartApp.{AppEnv, ForkData}
import com.github.zorechka.bazel.BazelDepsCheck
import com.github.zorechka.clients.GithubClient
import zio.{RIO, ZIO}


trait ResultNotifier {
  val notifier: ResultNotifier.Service
}

object ResultNotifier {

  trait Service {
    def notify(forkData: ForkData, updatedDeps: List[Dep], unusedDeps: List[Dep]): RIO[AppEnv, Unit]
  }

  trait CreatePullRequest extends ResultNotifier {
    override val notifier: Service = new Service {
      def notify(forkData: ForkData, updatedDeps: List[Dep], unusedDeps: List[Dep]): ZIO[AppEnv, Throwable, Unit] = {
        val (depsDesc, branch) = branchName(updatedDeps)

        for {
          _ <- GithubClient.createBranch(forkData.forkDir, branch)
          _ <- ZIO.effect(BazelDepsCheck.applyDepUpdates(forkData.forkDir, updatedDeps))
          _ <- GithubClient.stageAllChanges(forkData.forkDir)
          _ <- GithubClient.commit(forkData.forkDir, s"zorechka found new versions for deps: $depsDesc #pr")
          //      _ <-GithubClient.push(forkDir, branch)
        } yield ()
      }
    }

    private def branchName(deps: List[Dep]) = {
      val depsSample = deps.map(_.branchKey()).take(3).mkString("_")
      val depsDesc = (if (depsSample.length > 90) depsSample.substring(0, 90) else depsSample) + (if (deps.size > 3) s"_and_${deps.size - 3}_more" else "")
      (depsDesc, s"feature/update-deps-$depsDesc")
    }
  }

  def notify(forkData: ForkData, updatedDeps: List[Dep], unusedDeps: List[Dep]) =
    ZIO.accessM[AppEnv](_.notifier.notify(forkData, updatedDeps, unusedDeps))

}