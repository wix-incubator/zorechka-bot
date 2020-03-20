package com.wix.zorechka.clients

import java.nio.file.Path

import com.wix.zorechka.clients.process.{ClientOutput, RunProcess}
import com.wix.zorechka.repos.GitRepo
import zio.{Has, RIO, Task, ZIO, ZLayer}

object GithubClient {
  type GithubClient = Has[Service]

  trait Service {
    def cloneRepo(repo: GitRepo, destinationDir: Path): Task[ClientOutput]
    def createBranch(workDir: Path, branchName: String): Task[ClientOutput]
    def stageAllChanges(workDir: Path): Task[ClientOutput]
    def commit(workDir: Path, commitMsg: String): Task[ClientOutput]
    def push(workDir: Path, branchName: String): Task[ClientOutput]
  }

  val live = ZLayer.succeed {
    new Service {
      def cloneRepo(repo: GitRepo, destinationDir: Path): Task[ClientOutput] = {
        RunProcess.execCmd(List("git", "clone", "--recursive", repo.url), destinationDir)
      }

      override def createBranch(workDir: Path, branchName: String): Task[ClientOutput] = {
        RunProcess.execCmd(List("git", "checkout", "-b", branchName), workDir)
      }

      override def commit(workDir: Path, commitMsg: String): Task[ClientOutput] = {
        RunProcess.execCmd(List("git", "commit", "-m", commitMsg), workDir)
      }

      override def stageAllChanges(workDir: Path): Task[ClientOutput] = {
        RunProcess.execCmd(List("git", "add", "-A"), workDir)
      }

      override def push(workDir: Path, branchName: String): Task[ClientOutput] = {
        RunProcess.execCmd(List("git", "push", "--set-upstream", "origin", branchName), workDir)
      }
    }
  }

  def cloneRepo(repo: GitRepo, destinationDir: Path): RIO[GithubClient, ClientOutput] =
    ZIO.accessM[GithubClient](_.get.cloneRepo(repo, destinationDir))

  def createBranch(workDir: Path, branchName: String): RIO[GithubClient, ClientOutput] =
    ZIO.accessM[GithubClient](_.get.createBranch(workDir, branchName))

  def stageAllChanges(workDir: Path): RIO[GithubClient, ClientOutput] =
    ZIO.accessM[GithubClient](_.get.stageAllChanges(workDir))

  def commit(workDir: Path, message: String): RIO[GithubClient, ClientOutput] =
    ZIO.accessM[GithubClient](_.get.commit(workDir, message))

  def push(workDir: Path, branchName: String): RIO[GithubClient, ClientOutput] =
    ZIO.accessM[GithubClient](_.get.push(workDir, branchName))
}