package com.wix.zorechka.repos

import com.zaxxer.hikari.HikariDataSource
import doobie.hikari.HikariTransactor
import org.flywaydb.core.Flyway
import zio.console.{Console, putStrLn}
import zio.{Task, ZIO}

trait FlywayMigrator {
  val flywayMigrator: FlywayMigrator.Service
}

object FlywayMigrator {

  trait Service {
    def migrate(dbTransactor: HikariTransactor[Task]): ZIO[Console, Throwable, Unit]
  }

  trait Live extends FlywayMigrator {

    val flywayMigrator: Service = new Service {
      override def migrate(dbTransactor: HikariTransactor[Task]): ZIO[Console, Throwable, Unit] = for {
        _ <- putStrLn("Starting Flyway migration")
        _ <- dbTransactor.configure(dataSource => loadFlyWayAndMigrate(dataSource))
        _ <- putStrLn("Finished Flyway migration")
      } yield ()
    }

    private def loadFlyWayAndMigrate(dataSource: HikariDataSource) = ZIO.effect {
      Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()
    }
  }

  def migrate(dbTransactor: HikariTransactor[Task]): ZIO[FlywayMigrator with Console, Throwable, Unit] =
    ZIO.accessM[FlywayMigrator with Console](_.flywayMigrator.migrate(dbTransactor))
}
