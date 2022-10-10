package handlers

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import models.MintingRequestSqsMessage
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import play.api.libs.json.{Json, OWrites, Reads}
import services.Minter
import utils.{AwsHelper, ErgoNamesUtils}

import java.io.StringReader
import scala.collection.JavaConverters._

class MintingHandler {
  implicit val mintRequestSqsMessageReads: Reads[MintingRequestSqsMessage] = Json.reads[MintingRequestSqsMessage]
  implicit val mintRequestSqsMessageWrites: OWrites[MintingRequestSqsMessage] = Json.writes[MintingRequestSqsMessage]

  def handle(sqsEvent: SQSEvent, context: Context): Unit = {
    val config = ErgoNamesUtils.getConfig

    // TODO: Build logger. Probably using some general utility.
    if (config.awsRegion == null)
      throw new Exception("Did not find 'awsRegion' in environment variables or in local config file")

    if (config.secretName == null)
      throw new Exception("Did not find 'secretName' in environment variables or in local config file")

    val secretString = AwsHelper.getSecretFromSecretsManager(config.secretName, config.awsRegion)
    val ergoConfig = ErgoToolConfig.load(new StringReader(secretString))
    val ergoClient = ErgoNamesUtils.buildErgoClient(ergoConfig.getNode, ergoConfig.getNode.getNetworkType)

    val queueUrl = config.mintRequestsQueueUrl
    if (queueUrl == null)
      throw new Exception("Did not find 'mintRequestsQueue' in environment variables or in local config file")

    val sqsClient = AwsHelper.getSqsClient(config.awsRegion)
    val sqsMessages = sqsEvent.getRecords.asScala
    val mintRequests = sqsMessages.map{ rawMessage =>
      val parsedMessage = Json.parse(rawMessage.getBody).as[MintingRequestSqsMessage]
      (rawMessage, parsedMessage)
    }

    val dryMsg = if (config.dry)
                    "Running in dry mode - will not process mint request or attempt to query node"
                 else
                    "NOT running in dry mode - will attempt to process minting request and query node"

    val minter = new Minter()
    val txIds = ergoClient.execute((ctx: BlockchainContext) => {
      val prover = ErgoNamesUtils.buildProver(ctx, ergoConfig.getNode)
      val nodeService = ErgoNamesUtils.buildNodeService(ergoConfig)
      val walletService = ErgoNamesUtils.buildNewWalletApiService(ergoConfig)

      val royalty = ergoConfig.getParameters.get("royaltyPercetage").toInt

      mintRequests.map{
        case (rawMessage, mintRequest) =>
          // TODO: Account for failures
          val txId = minter.mint(mintRequest.mintingRequestBoxId, royalty, ctx, prover, nodeService, walletService)
          sqsClient.deleteMessage(queueUrl, rawMessage.getReceiptHandle)
          txId
      }.toList
    })

    println(s"Finished processing ${mintRequests.length} mint requests.")
    println(txIds)
  }
}
