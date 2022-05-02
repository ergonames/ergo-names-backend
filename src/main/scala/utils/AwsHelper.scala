package utils

import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest

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
}
