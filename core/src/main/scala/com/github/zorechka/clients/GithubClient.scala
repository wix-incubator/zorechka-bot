package com.github.zorechka.clients

import java.nio.file.Path

import com.github.zorechka.repos.GitRepo
import com.github.zorechka.utils.RunProcess
import scalaz.zio.{Task, TaskR, ZIO}

case class ClientOutput(out: List[String])

trait GithubClient[-R] {
  val githubClient: GithubClient.Service[R]
}

object GithubClient {
  trait Service[-R] {
    def cloneRepo(repo: GitRepo, destinationDir: Path): TaskR[R, ClientOutput]
    def createBranch(workDir: Path, branchName: String): TaskR[R, ClientOutput]
    def stageAllChanges(workDir: Path): TaskR[R, ClientOutput]
    def commit(workDir: Path, commitMsg: String): TaskR[R, ClientOutput]
    def push(workDir: Path, branchName: String): TaskR[R, ClientOutput]
  }

  trait Live extends GithubClient[Any] {
    val githubClient = new GithubClient.Service[Any] {
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
