package utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.{ClassTagExtensions, DefaultScalaModule}
import models.ErgoNamesConfig
import play.api.libs.json.{Json, Reads}

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

  def getJsonConfigAsTypedObject: ErgoNamesConfig = {
    try {
      val content = scala.io.Source.fromFile(pathToConfigFile).mkString
      Json.parse(content).as[ErgoNamesConfig]
    } catch {
      case e: FileNotFoundException => null
    }
  }
}

