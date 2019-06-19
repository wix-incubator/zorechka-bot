package com.github.zorechka.repos

import java.io.File
import java.nio.file.Files

import com.github.zorechka.HasAppConfig
import scalaz.zio.{TaskR, ZIO}

import scala.collection.JavaConverters._

case class GitRepo(owner: String, name: String, url: String)

trait GithubRepos[-R] {
  val repos: GithubRepos.Service[R]
}

object GithubRepos {
  trait Service[-R] {
    def reposToCheck(): TaskR[R, List[GitRepo]]
  }

  trait Live extends GithubRepos[HasAppConfig] {
    val repos = new GithubRepos.Service[HasAppConfig] {
      private val regex = """-\s+(.+)/(.+)""".r

      override def reposToCheck(): TaskR[HasAppConfig, List[GitRepo]] = for {
        cfg <- ZIO.access[HasAppConfig](_.cfg.config)
        result <- ZIO.effect {
          Files
            .readAllLines(new File(cfg.reposFile).toPath).asScala
            .collect {
              case regex(owner, repo) => GitRepo(owner, repo, s"https://github.com/$owner/$repo")
            }
            .toList
        }
      } yield result
    }
  }
}
