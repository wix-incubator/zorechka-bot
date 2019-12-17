package com.github.zorechka

import java.nio.file.{Files, Path}

import com.github.zorechka.clients.{GithubClient, Http4sClient, MavenCentralClient}
import com.github.zorechka.repos.{GitRepo, GithubRepos}
import com.github.zorechka.service.{ResultNotifier, ThirdPartyDepsAnalyzer, UnusedDepsAnalyser}
import zio.{Runtime, ZIO}
import zio.console._
import zio.internal.PlatformLive

object StartApp extends App {
  type AppEnv = Console with GithubRepos with GithubClient with Http4sClient with MavenCentralClient
    with HasAppConfig with ThirdPartyDepsAnalyzer with ResultNotifier

  val env = new Console.Live
    with HasAppConfig.Live
    with GithubRepos.Live with GithubClient.Live
    with Http4sClient.Live with MavenCentralClient.Live
    with ThirdPartyDepsAnalyzer.Live with ResultNotifier.CreatePullRequest


  Runtime(env, PlatformLive.Default)
    .unsafeRunSync(buildApp(args.toList))

  case class ForkData(repo: GitRepo, forkDir: Path)

  def buildApp(args: List[String]): ZIO[AppEnv, Throwable, Unit] = for {
    _ <- putStrLn("Starting bot")

    githubRepos <- GithubRepos.repos()
    _ <- putStrLn("Has following repos: " + githubRepos.mkString("\n"))
    _ <- ZIO.collectAll(githubRepos.map(checkRepo))
    _ <- putStrLn("Finish bot")
  } yield ()

  private def checkRepo(repo: GitRepo) = {
    val forkDir = Files.createTempDirectory(s"repos-${repo.owner}-${repo.name}")
    for {
      _ <- putStrLn(s"Forking in: ${forkDir.toAbsolutePath}")
      // repoPath = Path.of("/var/folders/st/2qj3mn41327b1ynd4jjfxxg978_y4f/T/repos-wix-private-strategic-products11113169125862272842/strategic-products")
      repoPath = forkDir.resolve(repo.name)
      _ <- GithubClient.cloneRepo(repo, forkDir)
      forkData = ForkData(repo, repoPath)
      updatedDeps <- ThirdPartyDepsAnalyzer.findLatest(forkData)
      unusedDeps <- UnusedDepsAnalyser.findUnused(forkData)
      _ <- ResultNotifier.notify(forkData, updatedDeps, unusedDeps)
    } yield ()
  }
}