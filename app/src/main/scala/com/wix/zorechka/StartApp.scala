package com.wix.zorechka

import java.nio.file.{Files, Path}

import com.wix.zorechka.clients._
import com.wix.zorechka.repos.{DbTransactor, FlywayMigrator, GitRepo, GithubRepos, UnusedDepCache}
import com.wix.zorechka.service.{ResultNotifier, ThirdPartyDepsAnalyzer, UnusedDepsAnalyser}
import doobie.hikari.HikariTransactor
import doobie.util.transactor
import org.http4s.client.Client
import zio.blocking.Blocking
import zio.console.{Console, putStrLn}
import zio.internal.PlatformLive
import zio.{Exit, Reservation, Runtime, Task, ZIO}

case class InitAppState(config: AppConfig,
                        dbTransactor: HikariTransactor[Task],
                        http4sClient: Client[Task],
                        release: Exit[Any, Any] => ZIO[Blocking, Nothing, Unit],
                       )

object StartApp extends App {
  // Init cfg and db first
  val initState = Runtime(new Console.Live with HasAppConfig.Live with Blocking.Live, PlatformLive.Default)
    .unsafeRunSync(initApp())
    .getOrElse(err => throw err.squash)

  type AppEnv = Console with GithubRepos with GithubClient with MavenCentralClient
    with ThirdPartyDepsAnalyzer with ResultNotifier with UnusedDepsAnalyser with BazelClient
    with BuildozerClient with UnusedDepCache with Blocking

  val env: AppEnv = new Console.Live with Blocking.Live
    with GithubRepos.Live with GithubClient.Live
    with MavenCentralClient.Live with BuildozerClient.Live
    with BazelClient.Live with ResultNotifier.PrintPullRequestInfo // CreatePullRequest
    with ThirdPartyDepsAnalyzer.Live with UnusedDepsAnalyser.Live with UnusedDepCache.MysqlUnusedDepCache {
      override protected val tnx: transactor.Transactor[Task] = initState.dbTransactor
      override protected val httpClient: Client[Task] = initState.http4sClient
  }

  Runtime(env, PlatformLive.Default).unsafeRunSync(
    buildApp(initState)
  )

  private def initApp(): ZIO[HasAppConfig with Blocking, Throwable, InitAppState] =
    ZIO.runtime[Blocking].flatMap { implicit rt =>
      for {
        cfg <- HasAppConfig.loadConfig()
        dbReservation <- DbTransactor.newMysqlTransactor(cfg.db).reserve
        transactor <- dbReservation.acquire
        _ <- FlywayMigrator.migrate(transactor)
        httpClientReservation <- Http4sClient.newHttpClient.reserve
        httpClient <- httpClientReservation.acquire
      } yield InitAppState(cfg, transactor, httpClient, (exit: Exit[Any, Any]) =>
        for {
          _ <- dbReservation.release(exit)
          _ <- httpClientReservation.release(exit)
        } yield ()
      )
    }

  private def buildApp(initAppState: InitAppState): ZIO[AppEnv, Throwable, Unit] = for {
    _ <- putStrLn("Starting bot")
    cfg = initAppState.config

    githubRepos <- GithubRepos.repos(cfg.reposFile)
    _ <- putStrLn("Has following repos: " + githubRepos.mkString("\n"))
    _ <- ZIO.collectAll(githubRepos.map(checkRepo))
    _ <- putStrLn("Finishing bot, releasing resources")
    _ <- initAppState.release(Exit.Success(()))
  } yield ()

  private def checkRepo(repo: GitRepo) = {
    for {
      forkDir <- ZIO(Files.createTempDirectory(s"repos-${repo.owner}-${repo.name}"))
      _ <- putStrLn(s"Forking in: ${forkDir.toAbsolutePath}")
      repoPath = forkDir.resolve(repo.name)
      _ <- GithubClient.cloneRepo(repo, forkDir)
      forkData = ForkData(repo, repoPath)
      updatedDeps <- ThirdPartyDepsAnalyzer.findLatest(forkData)
      unusedDeps <- UnusedDepsAnalyser.findUnused(forkData)
      _ <- ResultNotifier.notify(forkData.forkDir, updatedDeps,  unusedDeps)
    } yield ()
  }

//  private def checkRepo(repo: GitRepo) = {
//    for {
//      forkDir <- ZIO(Files.createTempDirectory(s"repos-${repo.owner}-${repo.name}"))
//      _ <- putStrLn(s"Forking in: ${forkDir.toAbsolutePath}")
//      repoPath = Path.of("/var/folders/st/2qj3mn41327b1ynd4jjfxxg978_y4f/T/repos-wix-private-strategic-products18061679584814895349/strategic-products")
//      //      repoPath = forkDir.resolve(repo.name)
////      _ <- GithubClient.cloneRepo(repo, forkDir)
//      forkData = ForkData(repo, repoPath)
//      //      updatedDeps <- ThirdPartyDepsAnalyzer.findLatest(forkData)
//      unusedDeps <- UnusedDepsAnalyser.findUnused(forkData)
//      _ <- ResultNotifier.notify(forkData.forkDir, List.empty,  unusedDeps)
//    } yield ()
//  }
}
