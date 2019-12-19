package com.wix.zorechka.clients.process

import java.nio.file.Path

import zio.{Task, ZIO}

import scala.collection.mutable.ListBuffer
import scala.sys.process.{Process, ProcessLogger}

case class ClientOutput(value: List[String]) extends AnyVal

object RunProcess {
  def execCmd(command: List[String], workDir: Path, extraEnv: List[(String, String)] = List.empty): Task[ClientOutput] = ZIO.effect {
    val lb = ListBuffer.empty[String]
    val log = new ProcessLogger {
      override def out(s: => String): Unit = {
        println(s)
        lb.append(s)
      }
      override def err(s: => String): Unit = {
        println(s)
        lb.append(s)
      }
      override def buffer[T](f: => T): T = f
    }

    println(command.mkString(" "))
    val exitStatus = Process(command, Some(workDir.toFile), extraEnv: _*).!(log)
    if (exitStatus != 0 && exitStatus != 3)
      throw new IllegalStateException(s"Got status $exitStatus")
    ClientOutput(lb.result())
  }
}
