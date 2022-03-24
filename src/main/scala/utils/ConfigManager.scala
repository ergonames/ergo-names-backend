package utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}

import java.io.FileNotFoundException

object ConfigManager {
  val pathToLocalConfig: String = "config.json"

  def get(key: String): String = {
    val env = sys.env.getOrElse(key, null)
    if (env != null) return env

    val local = getJsonConfigAsMap.getOrElse(key, null)
    local
  }

  def getJsonConfigAsMap: Map[String, String] = {
    try {
      val jsonContent = scala.io.Source.fromFile(pathToLocalConfig)
      val mapper = new ObjectMapper() with ClassTagExtensions
      mapper.registerModule(DefaultScalaModule)
      mapper.readValue[Map[String, String]](jsonContent.reader())
    } catch {
      case e: FileNotFoundException => null
    }
  }
}
