package com.wix.zorechka.repos

import doobie.hikari.HikariTransactor
import org.flywaydb.core.Flyway
import zio.blocking.Blocking
import zio.{Task, ZIO}

object FlywayMigrator {

  def migrate(dbTransactor: HikariTransactor[Task])(implicit rt: zio.Runtime[Blocking]): Task[Unit] = dbTransactor.configure { dataSource =>
    ZIO.effect {
      println("Starting Flyway migration")
      Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()
      println("Finished Flyway migration")
    }
  }
}
