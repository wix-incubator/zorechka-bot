package com.wix.zorechka.repos

import java.io.File
import java.nio.file.Files

import zio.{RIO, Task, ZIO}

import collection.JavaConverters._

case class GitRepo(owner: String, name: String, url: String)

trait GithubRepos {
  val repos: GithubRepos.Service
}

object GithubRepos {
  trait Service {
    def repos(reposFile: String): Task[List[GitRepo]]
  }

  trait Live extends GithubRepos {
    val repos: GithubRepos.Service = new GithubRepos.Service {
      override def repos(reposFile: String): Task[ List[GitRepo]] = for {
        result <- ZIO.effect {
          Files
            .readAllLines(new File(reposFile).toPath).asScala
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
  def repos(reposFile: String): RIO[GithubRepos, List[GitRepo]] = ZIO.accessM(env => env.repos.repos(reposFile))
}