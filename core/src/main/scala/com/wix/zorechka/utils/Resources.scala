package com.wix.zorechka.utils

import java.io.File
import java.nio.file.Files

import collection.JavaConverters._

object Resources {
  def readAllLines(filename: String): List[String] = {
    Files.readAllLines(new File(getClass.getClassLoader.getResource(filename).getFile).toPath).asScala.toList
  }
}
