package com.github.zorechka.repos

import java.io.File
import java.nio.file.Files

import com.github.zorechka.HasAppConfig
import scalaz.zio.Task
import collection.JavaConverters._

case class GitRepo(owner: String, name: String, url: String)
trait GithubRepos {
  val repos: GithubRepos.Service
}

object GithubRepos {
  trait Service {
    def reposToCheck(): Task[List[GitRepo]]
  }

  trait Live extends GithubRepos { this: HasAppConfig =>
    val repos: GithubRepos.Service = new GithubRepos.Service {
      override def reposToCheck(): Task[List[GitRepo]] = {
        val regex = """-\s+(.+)/(.+)""".r

        cfg.config.map {
          cfg => Files
            .readAllLines(new File(cfg.reposFile).toPath).asScala
            .collect {
              case regex(owner, repo) => GitRepo(owner, repo, s"https://github.com/$owner/$repo")
            }
            .toList
        }
      }
    }
  }
}