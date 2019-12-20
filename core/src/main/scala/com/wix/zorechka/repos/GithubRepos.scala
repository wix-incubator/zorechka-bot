package com.wix.zorechka.repos

import java.io.File
import java.nio.file.Files

import com.wix.zorechka.HasAppConfig
import zio.{RIO, ZIO}

import collection.JavaConverters._

case class GitRepo(owner: String, name: String, url: String)

trait GithubRepos {
  val repos: GithubRepos.Service
}

object GithubRepos {
  trait Service {
    def repos(): RIO[HasAppConfig, List[GitRepo]]
  }

  trait Live extends GithubRepos {
    val repos: GithubRepos.Service = new GithubRepos.Service {
      override def repos(): RIO[HasAppConfig, List[GitRepo]] = for {
        cfg <- ZIO.access[HasAppConfig](_.cfg.config)
        result <- ZIO.effect {
          Files
            .readAllLines(new File(cfg.reposFile).toPath).asScala
            .map(_.trim.split(" ").toList)
            .collect {
              case ownerRepo :: Nil =>
                val (owner :: repo :: Nil) = ownerRepo.split("/").toList
                GitRepo(owner, repo, s"https://github.com/$owner/$repo.git") // https://github.com/wix-private/strategic-products.git
              case ownerRepo :: token :: Nil =>
                val (owner :: repo :: Nil) = ownerRepo.split("/").toList
                GitRepo(owner, repo, s"https://$token@github.com/$owner/$repo.git")
            }
            .toList
        }
      } yield result
    }
  }

  // helpers
  def repos(): RIO[GithubRepos with HasAppConfig, List[GitRepo]] = ZIO.accessM(env => env.repos.repos())
}