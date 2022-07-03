package handlers

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import models.MintRequestSqsMessage
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import play.api.libs.json.{Json, OWrites, Reads}
import services.Minter
import utils.{AwsHelper, ConfigManager, ErgoNamesUtils}

import java.io.StringReader
import scala.collection.JavaConverters._

class MintingHandler {
  implicit val mintRequestSqsMessageReads: Reads[MintRequestSqsMessage] = Json.reads[MintRequestSqsMessage]
  implicit val mintRequestSqsMessageWrites: OWrites[MintRequestSqsMessage] = Json.writes[MintRequestSqsMessage]

  def handle(sqsEvent: SQSEvent, context: Context): Unit = {
    // TODO: Build logger. Probably using some general utility.
    val awsRegion = AwsHelper.getRegion
    if (awsRegion == null)
      throw new Exception("Did not find 'awsRegion' in environment variables or in local config file")

    val secretName = ConfigManager.get("secretName")
    if (secretName == null)
      throw new Exception("Did not find 'secretName' in environment variables or in local config file")

    val secretString = AwsHelper.getSecretFromSecretsManager(secretName, awsRegion)
    val ergoConfig = ErgoToolConfig.load(new StringReader(secretString))
    val ergoClient = ErgoNamesUtils.buildErgoClient(ergoConfig.getNode, ergoConfig.getNode.getNetworkType)

    val queueUrl = ConfigManager.get("mintRequestsQueueUrl")
    if (queueUrl == null)
      throw new Exception("Did not find 'mintRequestsQueue' in environment variables or in local config file")

    val sqsClient = AwsHelper.getSqsClient(awsRegion)
    val sqsMessages = sqsEvent.getRecords.asScala
    val mintRequests = sqsMessages.map{ rawMessage =>
      val parsedMessage = Json.parse(rawMessage.getBody).as[MintRequestSqsMessage]
      (rawMessage, parsedMessage)
    }

    val dry = ConfigManager.get("dry")
    if (dry == null)
      throw new Exception("Could not find config flag 'dry'")
    val dryMsg = if (dry.toBoolean)
                    "Running in dry mode - will not process mint request or attempt to query node"
                 else
                    "NOT running in dry mode - will attempt to process minting request and query node"

    val minter = new Minter()
    val txIds = ergoClient.execute((ctx: BlockchainContext) => {
      val prover = ErgoNamesUtils.buildProver(ctx, ergoConfig.getNode)
      val nodeService = ErgoNamesUtils.buildNodeService(ergoConfig)

      mintRequests.map{
        case (rawMessage, mintRequest) =>
          // TODO: Account for failures
          val txId = minter.mint(mintRequest.mintRequestBoxId, ctx, prover, nodeService)
          sqsClient.deleteMessage(queueUrl, rawMessage.getReceiptHandle)
          txId
      }.toList
    })

    println(s"Finished processing ${mintRequests.length} mint requests.")
    println(txIds)
  }
}
