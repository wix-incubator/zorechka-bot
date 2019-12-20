package com.wix.zorechka

import java.nio.file.Files

import com.wix.zorechka.clients._
import com.wix.zorechka.repos.{GitRepo, GithubRepos}
import com.wix.zorechka.service.{ResultNotifier, ThirdPartyDepsAnalyzer, UnusedDepsAnalyser}
import zio.console.{Console, putStrLn}
import zio.internal.PlatformLive
import zio.{Runtime, ZIO}

object StartApp extends App {
  type AppEnv = Console with GithubRepos with GithubClient with Http4sClient with MavenCentralClient
    with HasAppConfig with ThirdPartyDepsAnalyzer with ResultNotifier with UnusedDepsAnalyser with BazelClient
    with BuildozerClient

  val env = new Console.Live
    with HasAppConfig.Live
    with GithubRepos.Live with GithubClient.Live
    with Http4sClient.Live with MavenCentralClient.Live with BuildozerClient.Live
    with BazelClient.Live with ResultNotifier.PrintPullRequestInfo // CreatePullRequest
    with ThirdPartyDepsAnalyzer.Live with UnusedDepsAnalyser.Live

  Runtime(env, PlatformLive.Default)
    .unsafeRunSync(buildApp(args.toList))

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
//      repoPath = Path.of("/var/folders/st/2qj3mn41327b1ynd4jjfxxg978_y4f/T/repos-wix-private-strategic-products11113169125862272842/strategic-products")
      repoPath = forkDir.resolve(repo.name)
      _ <- GithubClient.cloneRepo(repo, forkDir)
      forkData = ForkData(repo, repoPath)
      updatedDeps <- ThirdPartyDepsAnalyzer.findLatest(forkData)
//      unusedDeps <- UnusedDepsAnalyser.findUnused(forkData)
      _ <- ResultNotifier.notify(forkData.forkDir, updatedDeps, /* unusedDeps */ List.empty)
    } yield ()
  }
}
