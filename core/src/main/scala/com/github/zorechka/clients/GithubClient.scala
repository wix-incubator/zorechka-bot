package com.github.zorechka.clients

import java.nio.file.Path

import com.github.zorechka.repos.GitRepo
import com.github.zorechka.utils.RunProcess
import scalaz.zio.{Task, ZIO}

case class ClientOutput(out: List[String])

trait GithubClient {
  val githubClient: GithubClient.Service
}

object GithubClient {
  trait Service {
    def cloneRepo(repo: GitRepo, destinationDir: Path): Task[ClientOutput]
    def createBranch(workDir: Path, branchName: String): Task[ClientOutput]
    def stageAllChanges(workDir: Path): Task[ClientOutput]
    def commit(workDir: Path, commitMsg: String): Task[ClientOutput]
    def push(workDir: Path, branchName: String): Task[ClientOutput]
  }

  trait Live extends GithubClient {
    val githubClient: GithubClient.Service = new GithubClient.Service {
      def cloneRepo(repo: GitRepo, destinationDir: Path): Task[ClientOutput] = ZIO.effect {
        ClientOutput(RunProcess.execCmd(List("git", "clone", "--recursive", repo.url), destinationDir))
      }

      override def createBranch(workDir: Path, branchName: String): Task[ClientOutput] = ZIO.effect {
        ClientOutput(RunProcess.execCmd(List("git", "checkout", "-b", branchName), workDir))
      }

      override def commit(workDir: Path, commitMsg: String): Task[ClientOutput] = ZIO.effect {
        ClientOutput(RunProcess.execCmd(List("git", "commit", "-m", commitMsg), workDir))
      }

      override def stageAllChanges(workDir: Path): Task[ClientOutput] = ZIO.effect {
        ClientOutput(RunProcess.execCmd(List("git", "add", "-A"), workDir))
      }

      override def push(workDir: Path, branchName: String): Task[ClientOutput] = ZIO.effect {
        ClientOutput(RunProcess.execCmd(List("git", "push", "--set-upstream", "origin", branchName), workDir))
      }
    }
  }
}

object GithubClientLive extends GithubClient.Live