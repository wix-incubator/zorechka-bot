package com.github.zorechka

import java.nio.file.{Files, Path}

import com.github.zorechka.clients.{GithubClient, Http4sClient, MavenCentralClient}
import com.github.zorechka.dependency.BazelDepsCheck
import com.github.zorechka.repos.{GitRepo, GithubRepos}
import scalaz.zio.console._
import scalaz.zio.internal.PlatformLive
import scalaz.zio.{Runtime, ZIO}

// Avoid `<:<` due to ambiguous implicits caused by Predef.$conforms$ very high priority in implicit scope
trait Has[-A, +B] extends (A => B)

object Has {
  implicit def has[A, B](implicit ev: A <:< B): Has[A, B] = ev(_)
}

object StartApp extends App {
  trait AppEnv[R] extends Console with GithubRepos[R] with GithubClient[R] with Http4sClient[R] with MavenCentralClient[R] with HasAppConfig
  trait AppEnvImpl extends AppEnv[AppEnvImpl]

  val env: AppEnvImpl = new AppEnvImpl
    with Console.Live with HasAppConfig.Live
    with GithubRepos.Live with GithubClient.Live
    with Http4sClient.Live with MavenCentralClient.Live[AppEnvImpl]

  Runtime(env, PlatformLive.Default).unsafeRunSync[Nothing, Int](runApp(args.toList))

  case class ForkData(forkDir: Path, deps: List[Dep])

  def runApp[R <: Console: ? Has AppEnv[R]](args: List[String]): ZIO[R, Nothing, Int] = {
    val res = for {
      _ <- putStrLn("Starting bot")

      githubRepos <- foundRepos
      _ <- putStrLn("Has following repos: " + githubRepos.mkString("\n"))

      repoWithDeps <- ZIO.collectAll(githubRepos.map {
        repo =>
          val forkDir = Files.createTempDirectory(s"repos-${repo.owner}-${repo.name}")
          for {
            _ <- putStrLn(s"Forking in: ${forkDir.toAbsolutePath}")
            githubClient <- ZIO.access[R](_.githubClient)
            out <- githubClient.cloneRepo(repo, forkDir)
            deps <- ZIO(BazelDepsCheck.foundDeps(forkDir.resolve(repo.name)))
            _ <- putStrLn(s"Found ${deps.size} in $repo")
          } yield repo -> ForkData(forkDir.resolve(repo.name), deps)
      }).map(_.toMap)
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
      _ <- putStrLn("Finish bot")
    } yield ()

    res.fold(_ => 1, _ => 0)
  }

  def isNewer(latest: Dep, current: Dep): Boolean = Ordering[Dep].gt(latest, current)

  def foundRepos[R: ? Has GithubRepos[R]]: ZIO[R, Throwable, List[GitRepo]] = {
    ZIO.accessM(env => env.repos.reposToCheck())
  }

  def foundLatest[R: ? Has MavenCentralClient[R]](deps: Seq[Dep]): ZIO[R, Throwable, List[Dep]] = {
    ZIO.foreach(deps)(dep => ZIO.accessM[R] {
      _.client.allVersions(dep).map(listOfDeps => if (listOfDeps.isEmpty) dep else listOfDeps.max)
    })
  }

  def createNewVersionPullRequest[R: ? Has GithubClient[R]](repo: GitRepo, forkDir: Path, deps: List[Dep]): ZIO[R, Throwable, Unit] = {
    val (depsDesc, branch) = branchName(deps)

    for {
      _ <- ZIO.accessM[R](_.githubClient.createBranch(forkDir, branch))
      _ <- ZIO.effect(BazelDepsCheck.applyDepUpdates(forkDir, deps))
      _ <- ZIO.accessM[R](_.githubClient.stageAllChanges(forkDir))
      _ <- ZIO.accessM[R](_.githubClient.commit(forkDir, s"zorechka found new versions for deps: $depsDesc #pr"))
      _ <- ZIO.accessM[R](_.githubClient.push(forkDir, branch))
    } yield ()
  }

  private def branchName(deps: List[Dep]) = {
    val depsSample = deps.map(_.branchKey()).take(3).mkString("_")
    val depsDesc = (if (depsSample.length > 90) depsSample.substring(0, 90) else depsSample) + (if (deps.size > 3) s"_and_${deps.size - 3}_more" else "")
    (depsDesc, s"feature/update-deps-$depsDesc")
  }
}
