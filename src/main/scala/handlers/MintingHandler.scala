package handlers

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import models.MintingRequestSqsMessage
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.slf4j.{LoggerFactory, MDC}
import play.api.libs.json.{Json, OWrites, Reads}
import services.Minter
import utils.{AwsHelper, ErgoNamesUtils}
import com.github.dwickern.macros.NameOf._

import java.io.StringReader
import scala.collection.JavaConverters._

class MintingHandler {
  val logger = LoggerFactory.getLogger(getClass)

  implicit val mintRequestSqsMessageReads: Reads[MintingRequestSqsMessage] = Json.reads[MintingRequestSqsMessage]
  implicit val mintRequestSqsMessageWrites: OWrites[MintingRequestSqsMessage] = Json.writes[MintingRequestSqsMessage]

  def handle(sqsEvent: SQSEvent, context: Context): Unit = {
    logger.debug("Loading ErgoNames config...")
    val config = ErgoNamesUtils.getConfig

    if (config.awsRegion == null)
      throw new Exception("Did not find 'awsRegion' in environment variables or in local config file")

    if (config.secretName == null)
      throw new Exception("Did not find 'secretName' in environment variables or in local config file")

    logger.info("Loading Ergo config from secrets manager")
    val secretString = AwsHelper.getSecretFromSecretsManager(config.secretName, config.awsRegion)
    val ergoConfig = ErgoToolConfig.load(new StringReader(secretString))
    logger.info("Building Ergo client")
    val ergoClient = ErgoNamesUtils.buildErgoClient(ergoConfig.getNode, ergoConfig.getNode.getNetworkType)

    val queueUrl = config.mintRequestsQueueUrl
    if (queueUrl == null)
      throw new Exception("Did not find 'mintRequestsQueue' in environment variables or in local config file")

    logger.debug("Building SQS client")
    val sqsClient = AwsHelper.getSqsClient(config.awsRegion)

    logger.info("Parsing through messages in SQS event payload")
    val sqsMessages = sqsEvent.getRecords.asScala
    val mintRequests = sqsMessages.map{ rawMessage =>
      val parsedMessage = Json.parse(rawMessage.getBody).as[MintingRequestSqsMessage]
      (rawMessage, parsedMessage)
    }
    logger.info("Finished parsing. Ended up with {} mint request(s)", mintRequests.length)

    val dryMsg = if (config.dry)
                    "Running in dry mode - will not process mint request or attempt to query node"
                 else
                    "NOT running in dry mode - will attempt to process minting request and query node"
    logger.info(dryMsg)

    logger.info("Instantiating new Minter")
    val minter = new Minter(LoggerFactory.getLogger(classOf[Minter]))
    logger.info("Initiating blockchain context")
    val submittedTxIds = ergoClient.execute((ctx: BlockchainContext) => {
      logger.debug("Building prover")
      val prover = ErgoNamesUtils.buildProver(ctx, ergoConfig.getNode)
      logger.debug("Building node service")
      val nodeService = ErgoNamesUtils.buildNodeService(ergoConfig)
      logger.debug("Building wallet service")
      val walletService = ErgoNamesUtils.buildNewWalletApiService(ergoConfig)

      val royalty = ergoConfig.getParameters.get("royaltyPercentage").toInt
      logger.info("Royalty percentage set to {}%", royalty / 10)
      val paymentAddressRaw = ergoConfig.getParameters.get("paymentAddress")
      val paymentAddress = Address.create(paymentAddressRaw)
      logger.info("Payment collection address set to {}", paymentAddressRaw)

      logger.info("About to start iterating over {} mint request(s)", mintRequests.length)
      mintRequests.map{
        case (rawMessage, mintRequest) =>
          // TODO: Account for failures
          MDC.put(nameOf(mintRequest.mintingRequestBoxId), mintRequest.mintingRequestBoxId)
          MDC.put(nameOf(mintRequest.paymentTxId), mintRequest.paymentTxId)
          logger.info("Attempting to process mint request")
          val mintTxId = minter.mint(mintRequest.mintingRequestBoxId, royalty, ctx, prover, paymentAddress, nodeService, walletService)
          MDC.put(nameOf(mintTxId), mintTxId)
          logger.info("Finished processing mint request")
          MDC.clear()
          MDC.put(nameOf(rawMessage.getReceiptHandle), rawMessage.getReceiptHandle)
          logger.info("Deleting message from queue")
          sqsClient.deleteMessage(queueUrl, rawMessage.getReceiptHandle)
          MDC.clear()
          mintTxId
      }.toList
    })

    MDC.put(nameOf(submittedTxIds), submittedTxIds.toString)
    logger.info(s"Finished processing ${mintRequests.length} mint request(s)")
  }
}
