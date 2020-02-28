package com.wix.zorechka.repos

import cats.effect.Blocker
import com.wix.zorechka.DbConfig
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import zio.blocking.Blocking
import zio.{Managed, Task}
import zio.interop.catz._

object DbTransactor {

  def newMysqlTransactor(cfg: DbConfig)(implicit rt: zio.Runtime[Blocking]): Managed[Throwable, HikariTransactor[Task]] = {
      val res = for {
        connectEC  <- ExecutionContexts.fixedThreadPool[Task](2)
        transactEC <- ExecutionContexts.cachedThreadPool[Task]
        xa <- HikariTransactor.newHikariTransactor[Task](
          "com.mysql.jdbc.Driver",
          cfg.url, //s"jdbc:mysql://${dbConfig.host}:${config.port}/${config.mysql.schema}",
          cfg.username,
          cfg.password,
          connectEC,
          Blocker.liftExecutionContext(transactEC)
        )
      } yield xa

      res.toManaged
    }
}
