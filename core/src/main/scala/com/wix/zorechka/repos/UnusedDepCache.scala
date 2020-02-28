package com.wix.zorechka.repos

import doobie.util.transactor.Transactor
import doobie.util.update.Update0
import doobie.implicits._
import zio.{RIO, Task, ZIO}
import zio.interop.catz._

trait UnusedDepCache {
  val cache: UnusedDepCache.Service
}

object UnusedDepCache {
  trait Service {
    def isCached(githubRepo: String, target: String, hash: String): Task[Boolean]
    def cache(githubRepo: String, target: String, hash: String): Task[Unit]
  }

  trait MysqlUnusedDepCache extends UnusedDepCache {
    protected val tnx: Transactor[Task]

    override val cache: Service = new Service {
      def createTable: Task[Unit] = SQL.createTable.run
        .transact(tnx)
        .foldM(err => Task.fail(err), _ => Task.succeed(()))

      override def isCached(githubRepo: String, target: String, hash: String): Task[Boolean] = SQL.isCached(githubRepo, target, hash)
        .option
        .transact(tnx)
        .map(_.exists(_ == 1))

      override def cache(githubRepo: String, target: String, hash: String): Task[Unit] =
        SQL.insertOrUpdate(githubRepo, target, hash).run
          .transact(tnx)
          .foldM(err => Task.fail(err), _ => Task.succeed(()))
    }
  }

  object SQL {
    def createTable: Update0 = sql"""CREATE TABLE IF NOT EXISTS `unused_deps` (
      `github_repo` varchar(1000) NOT NULL,
      `build_target` varchar(1000) NOT NULL,
      `hash` varchar(256) NOT NULL,
      PRIMARY KEY (`github_repo`, `build_target`)
    ) ENGINE=InnoDB DEFAULT CHARSET=latin1;""".update

    def insertOrUpdate(githubRepo: String, target: String, hash: String): doobie.Update0 =
      sql"""INSERT INTO `unused_deps` (`github_repo`, `build_target`, `hash`) VALUES ($githubRepo, $target, $hash)
           |ON DUPLICATE KEY UPDATE hash = VALUES(hash)
           |""".stripMargin.update

    def isCached(githubRepo: String, target: String, hash: String): doobie.Query0[Int] = {
      sql"""SELECT 1 FROM `unused_deps` WHERE github_repo = $githubRepo AND build_target = $target AND hash = $hash""".query[Int]
    }
  }

  // helpers
  def isCached(githubRepo: String, target: String, hash: String): RIO[UnusedDepCache, Boolean] =
    ZIO.accessM(env => env.cache.isCached(githubRepo, target, hash))

  def cache(githubRepo: String, target: String, hash: String): RIO[UnusedDepCache, Unit] =
    ZIO.accessM(env => env.cache.cache(githubRepo, target, hash))
}