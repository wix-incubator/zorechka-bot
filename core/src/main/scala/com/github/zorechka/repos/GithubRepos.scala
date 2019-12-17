package com.github.zorechka.repos

import java.io.File
import java.nio.file.Files

import com.github.zorechka.HasAppConfig
import zio.{RIO, ZIO}

import collection.JavaConverters._

case class GitRepo(owner: String, name: String, url: String)

trait GithubRepos {
  val repos: GithubRepos.Service
}

object GithubRepos {
  trait Service {
    def reposToCheck(): RIO[HasAppConfig, List[GitRepo]]
  }

  trait Live extends GithubRepos {
    val repos: GithubRepos.Service = new GithubRepos.Service {
      private val regex = """-\s+(.+)/(.+)""".r

      override def reposToCheck(): RIO[HasAppConfig, List[GitRepo]] = for {
        cfg <- ZIO.access[HasAppConfig](_.cfg.config)
        result <- ZIO.effect {
          Files
            .readAllLines(new File(cfg.reposFile).toPath).asScala
            .collect {
              case regex(owner, repo) => GitRepo(owner, repo, s"git@github.com:$owner/$repo.git")
            }
            .toList
        }
      } yield result
    }
  }
}