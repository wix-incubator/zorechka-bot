package com.github.zorechka

import java.nio.file.{Files, Path}

import com.github.zorechka.clients.{GithubClient, Http4sClient, MavenCentralClient}
import com.github.zorechka.bazel.BazelDepsCheck
import com.github.zorechka.repos.{GitRepo, GithubRepos}
import zio.{Runtime, ZIO}
import zio.console._
import zio.internal.PlatformLive

object StartApp extends App {
  type AppEnv = Console with GithubRepos with GithubClient with Http4sClient with MavenCentralClient with HasAppConfig

  val env = new Console.Live
    with HasAppConfig.Live
    with GithubRepos.Live with GithubClient.Live
    with Http4sClient.Live with MavenCentralClient.Live

  Runtime(env, PlatformLive.Default).unsafeRunSync(runApp(args.toList))

  case class ForkData(forkDir: Path, deps: List[Dep])

  def runApp(args: List[String]): ZIO[AppEnv, Throwable, Unit] = {


    val res = for {
      _ <- putStrLn("Starting bot")

      githubRepos <- foundRepos()
      _ <- putStrLn("Has following repos: " + githubRepos.mkString("\n"))

      repoWithDeps <- ZIO.collectAll(githubRepos.map {
        repo =>
          val forkDir = Files.createTempDirectory(s"repos-${repo.owner}-${repo.name}")
          for {
            _ <- putStrLn(s"Forking in: ${forkDir.toAbsolutePath}")
            _ <- ZIO.accessM[GithubClient](_.githubClient.cloneRepo(repo, forkDir))
            deps <- ZIO(BazelDepsCheck.foundDeps(forkDir.resolve(repo.name)))
            _ <- putStrLn(s"Found ${deps.size} in $repo")
          } yield repo -> ForkData(forkDir.resolve(repo.name), deps)
      }).map(_.toMap)

      _ <- if (repoWithDeps.nonEmpty) {
        for {
          latest <- foundLatest(repoWithDeps.flatMap(_._2.deps).toSeq)

          repoWithLatest = {
            val latestByKey = latest.map(dep => dep.mapKey() -> dep).toMap
            repoWithDeps.mapValues {
              case ForkData(_, deps) =>
                val depsKeys = deps.map(_.mapKey()).toSet
                latestByKey.filterKeys(depsKeys.contains).values.toList
            }
          }

          repoWithUpdated = {
            repoWithLatest.map {
              case (repo, latestDeps) => repo -> latestDeps.filter {
                latestDep => repoWithDeps(repo).deps
                  .find(_.mapKey() == latestDep.mapKey())
                  .exists(current => isNewer(latestDep, current))
              }
            }
          }

          _ <- ZIO.foreach(repoWithUpdated) {
            case (repo, updatedDeps) => createNewVersionPullRequest(repo, repoWithDeps(repo).forkDir, updatedDeps)
          }
        } yield ()
      } else ZIO.unit

      _ <- putStrLn("Finish bot")
    } yield ()

    res
  }

  def isNewer(latest: Dep, current: Dep): Boolean = Ordering[Dep].gt(latest, current)

  def foundRepos(): ZIO[GithubRepos with HasAppConfig, Throwable, List[GitRepo]] = {
    ZIO.accessM(env => env.repos.reposToCheck())
  }

  def foundLatest(deps: Seq[Dep]): ZIO[MavenCentralClient with Http4sClient, Throwable, List[Dep]] = {
    ZIO.foreach(deps)(dep => ZIO.accessM[MavenCentralClient with Http4sClient] {
      _.client.allVersions(dep).map(listOfDeps => if (listOfDeps.isEmpty) dep else listOfDeps.max)
    })
  }

  def createNewVersionPullRequest(repo: GitRepo, forkDir: Path, deps: List[Dep]): ZIO[GithubClient, Throwable, Unit] = {
    val (depsDesc, branch) = branchName(deps)

    for {
      _ <- ZIO.accessM[GithubClient](_.githubClient.createBranch(forkDir, branch))
      _ <- ZIO.effect(BazelDepsCheck.applyDepUpdates(forkDir, deps))
      _ <- ZIO.accessM[GithubClient](_.githubClient.stageAllChanges(forkDir))
      _ <- ZIO.accessM[GithubClient](_.githubClient.commit(forkDir, s"zorechka found new versions for deps: $depsDesc #pr"))
//      _ <- ZIO.accessM[GithubClient](_.githubClient.push(forkDir, branch))
    } yield ()
  }

  private def branchName(deps: List[Dep]) = {
    val depsSample = deps.map(_.branchKey()).take(3).mkString("_")
    val depsDesc = (if (depsSample.length > 90) depsSample.substring(0, 90) else depsSample) + (if (deps.size > 3) s"_and_${deps.size - 3}_more" else "")
    (depsDesc, s"feature/update-deps-$depsDesc")
  }
}