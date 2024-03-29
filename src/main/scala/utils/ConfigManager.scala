package utils

import models.ErgoNamesConfig
import play.api.libs.json.{Json, Reads}
import scala.util.{Try,Success,Failure}
import java.io.FileNotFoundException

object ConfigManager {
  val pathToConfigFile: String = "config.json"
  implicit val configReads: Reads[ErgoNamesConfig] = Json.reads[ErgoNamesConfig]

  def fromEnv():  Try[ErgoNamesConfig] = {
    Try({
       val mintRequestsQueueUrl = sys.env.get("mintRequestsQueueUrl").get
       val dry = sys.env.get("dry").get.toBoolean
       val secretName = sys.env.get("secretName").get
       val awsRegion = sys.env.get("awsRegion") match {
         case Some(r) => r
         case None => sys.env.get("AWS_REGION").get  
       }
      val svgServiceUrl = sys.env.get("svgServiceUrl").get
       ErgoNamesConfig(mintRequestsQueueUrl, dry, secretName, awsRegion, svgServiceUrl)
     }
    )
  }

  def fromFile(): Try[ErgoNamesConfig] =  {
    Try({
      val content = scala.io.Source.fromFile(pathToConfigFile).mkString
      Json.parse(content).as[ErgoNamesConfig]
    })
  }

  def getConfig(): Try[ErgoNamesConfig] = {
    // try to read config from env
    // otherwise revert to try to read config from file
    // fail if a valid config can't be created from either
    fromEnv() match {
      case Success(c) => Success(c)
      case Failure(r) => {
        println("reading config from file..")
        fromFile()
      }
    }
  }
}

