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
    def isCached(hash: String): Task[Boolean]
    def cache(): Task[Unit]
  }

  trait MysqlUnusedDepCache extends UnusedDepCache {
    protected val tnx: Transactor[Task]

    override val cache: Service = new Service {
      def createTable: Task[Unit] = SQL.createTable.run
        .transact(tnx)
        .foldM(err => Task.fail(err), _ => Task.succeed(()))

      override def isCached(hash: String): Task[Boolean] = ???

      override def cache(): Task[Unit] = ???
    }
  }

  object SQL {
    def createTable: Update0 = sql"""CREATE TABLE IF NOT EXISTS
       Users (id int PRIMARY KEY, name varchar)""".update
  }

  // helpers
  def isCached(hash: String): RIO[UnusedDepCache, Boolean] = ZIO.accessM(env => env.cache.isCached(hash))
}