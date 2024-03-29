package utils

import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}

object AwsHelper {

  def getSecretFromSecretsManager(secretName: String, region: String): String = {
    val client = AWSSecretsManagerClientBuilder.standard()
      .withRegion(region)
      .build()

    // TODO: Add some error handling
    val getSecretRequest = new GetSecretValueRequest().withSecretId(secretName)
    val getSecretResult = client.getSecretValue(getSecretRequest)
    getSecretResult.getSecretString
  }

  def getSqsClient(region: String): AmazonSQS = {
    AmazonSQSClientBuilder.standard().withRegion(region).build()
  }
}
