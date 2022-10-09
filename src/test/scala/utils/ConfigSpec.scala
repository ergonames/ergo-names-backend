package utils

import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpecLike}

import scala.util.{Failure, Success}

class ConfigSpec extends WordSpecLike with Matchers with MockitoSugar {

  // utility to be able to mock env vars during testing
  //https://stackoverflow.com/questions/34028195/how-do-i-test-code-that-requires-an-environment-variable
  def setEnv(key: String, value: String) = {
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val map = field.get(System.getenv()).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]]
    map.put(key, value)
  }

  // utility to be able to mock env vars during testing
  //https://stackoverflow.com/questions/34028195/how-do-i-test-code-that-requires-an-environment-variable
  def delEnv(key: String) = {
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val map = field.get(System.getenv()).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]]
    map.remove(key)
  }

  def setDummyEnv(){
    setEnv("mintRequestsQueueUrl", "ENVQUEUE")
    setEnv("dry", "true")
    setEnv("secretName", "ENVSECRET")
    setEnv("awsRegion", "ENVREGION")
    setEnv("svgServiceUrl", "ENVSERVICEURL")
  }

  def cleanDummyEnv(){
    delEnv("mintRequestsQueueUrl")
    delEnv("dry")
    delEnv("secretName")
    delEnv("awsRegion")
    delEnv("svgServiceUrl")
  }

  "should load config from env" in {
    // a failure if there are not env variables
    assert(ConfigManager.fromEnv() match {
      case Success(c) => false
      case Failure(r) => true  
    })
  }

  "should load config from file " in {
    // a failure if there are not env variables
    val okConfig = ConfigManager.fromFile().toOption.get
    assert(okConfig.mintRequestsQueueUrl=="https://sqs.us-east-2.amazonaws.com/453627713520/mint-requests-queue-testnet")
    assert(okConfig.dry==true)
    assert(okConfig.secretName=="ergo-node-testnet-config")
    assert(okConfig.awsRegion=="us-east-2")
    assert(okConfig.svgServiceUrl=="https://304dkozk9a.execute-api.us-east-2.amazonaws.com")
  }

  "should prefer loading config from env " in {
    setDummyEnv()
    val okConfig = ConfigManager.getConfig().toOption.get
    assert(okConfig.mintRequestsQueueUrl=="ENVQUEUE")
    assert(okConfig.dry)
    assert(okConfig.secretName=="ENVSECRET")
    assert(okConfig.awsRegion=="ENVREGION")
    assert(okConfig.svgServiceUrl=="ENVSERVICEURL")
    cleanDummyEnv()
  }

  "should default to loading config from file if no env" in {
    val okConfig = ConfigManager.getConfig().toOption.get
    assert(okConfig.mintRequestsQueueUrl=="https://sqs.us-east-2.amazonaws.com/453627713520/mint-requests-queue-testnet")
    assert(okConfig.dry==true)
    assert(okConfig.secretName=="ergo-node-testnet-config")
    assert(okConfig.awsRegion=="us-east-2")
    assert(okConfig.svgServiceUrl=="https://304dkozk9a.execute-api.us-east-2.amazonaws.com")
  }
}
