package com.wix.zorechka

import java.nio.file.Files

import com.wix.zorechka.HasAppConfig.HasAppConfig
import com.wix.zorechka.clients.BazelClient.BazelClient
import com.wix.zorechka.clients.BuildozerClient.BuildozerClient
import com.wix.zorechka.clients.GithubClient.GithubClient
import com.wix.zorechka.clients.MavenCentralClient.MavenCentralClient
import com.wix.zorechka.clients._
import com.wix.zorechka.repos.FlywayMigrator.FlywayMigrator
import com.wix.zorechka.repos.GithubRepos.GithubRepos
import com.wix.zorechka.repos.UnusedDepCache.UnusedDepCache
import com.wix.zorechka.repos._
import com.wix.zorechka.service.ResultNotifier.ResultNotifier
import com.wix.zorechka.service.ThirdPartyDepsAnalyzer.ThirdPartyDepsAnalyzer
import com.wix.zorechka.service.UnusedDepsAnalyser.UnusedDepsAnalyser
import com.wix.zorechka.service.{ResultNotifier, ThirdPartyDepsAnalyzer, UnusedDepsAnalyser}
import doobie.hikari.HikariTransactor
import org.http4s.client.Client
import zio.blocking.Blocking
import zio.console.{Console, putStrLn}
import zio.{App, Exit, Runtime, Task, ZIO}

case class InitAppState(config: AppConfig,
                        dbTransactor: HikariTransactor[Task],
                        http4sClient: Client[Task],
                        release: Exit[Any, Any] => ZIO[Blocking, Nothing, Unit],
                       )

object StartApp extends App {
  // Init cfg and db first

  type InitStateEnv = HasAppConfig with Blocking with Console with FlywayMigrator

  val initStateLayer = Console.live ++ HasAppConfig.live ++ Blocking.live ++ FlywayMigrator.live

  val initApp: ZIO[Any, Throwable, InitAppState] = buildInitApp().provideLayer(initStateLayer)

  val initState = Runtime.default.unsafeRunSync(initApp)
    .getOrElse(err => throw err.squash)

  type AppEnv = Console with GithubRepos with GithubClient with MavenCentralClient
    with ThirdPartyDepsAnalyzer with ResultNotifier with UnusedDepsAnalyser with BazelClient
    with BuildozerClient with UnusedDepCache with Blocking

  val appLayer = Console.live ++ Blocking.live ++ GithubRepos.live ++ GithubClient.live ++ MavenCentralClient.live ++
    BuildozerClient.live ++ BazelClient.live ++ ResultNotifier.printPullRequestInfo ++ ThirdPartyDepsAnalyzer.live ++
    UnusedDepsAnalyser.live ++ UnusedDepCache.live(initState.dbTransactor)

  val app: ZIO[Any, Throwable, Unit] = buildApp(initState).provideLayer(appLayer)

  Runtime.default.unsafeRunSync(app)

  private def buildInitApp(): ZIO[InitStateEnv, Throwable, InitAppState] =
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
