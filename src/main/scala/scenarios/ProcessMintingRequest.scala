package scenarios


import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import models.{MintRequestSqsMessage, MintingTxArgs}
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import play.api.libs.json._
import utils.{AwsHelper, ConfigManager, ErgoNamesUtils}

import java.io.StringReader
import scala.collection.JavaConverters._

trait Minter {
  implicit val mintRequestSqsMessageReads: Reads[MintRequestSqsMessage] = Json.reads[MintRequestSqsMessage]
  implicit val mintRequestSqsMessageWrites: OWrites[MintRequestSqsMessage] = Json.writes[MintRequestSqsMessage]

  def createTx(ctx: BlockchainContext,
    inputs: java.util.List[InputBox],
    senderAddress: Address,
    mintRequestBox: InputBox,
    ergoNamesStandardTokenDescription: String,  networkType: NetworkType): (UnsignedTransaction, MintingTxArgs) = {

         val (token, tokenName, tokenDesc, tokenDecimals, ergValue, contract) = ErgoNamesUtils.issuanceBoxArgs(
          networkType,
          value = Parameters.MinChangeValue,
          mintRequestBox, // contains some register data to be extracted
          tokenDescription = ergoNamesStandardTokenDescription)

         val issuanceBox = ErgoNamesUtils.buildBoxWithTokenToMint(ctx,
           token, tokenName, tokenDesc,
           tokenDecimals, ergValue, contract)

        val paymentCollectionBox = ErgoNamesUtils.buildPaymentCollectionBox(
          ctx,
          mintRequestBox,
          senderAddress.asP2PK())

        val inputsWithMintBox =  List(mintRequestBox) ++ inputs.asScala

        val tx = ctx.newTxBuilder
          .boxesToSpend(inputsWithMintBox.asJava)
          .outputs(issuanceBox, paymentCollectionBox)
          .fee(Parameters.MinFee)
          .sendChangeTo(senderAddress.asP2PK())
           .build()

      val txArgs = MintingTxArgs(inputsWithMintBox, List(issuanceBox, paymentCollectionBox), Parameters.MinFee, senderAddress.asP2PK())

      (tx, txArgs)
  }

  def processMintingRequest(conf: ErgoToolConfig, networkType: NetworkType, mintContractAddress:String, mintRequestBoxId: String,  ergoNamesStandardTokenDescription: String): String = {
        val ergoClient = ErgoNamesUtils.buildErgoClient(conf.getNode, networkType)
        val mintingContractAddress = Address.create(mintContractAddress)
        val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {
        val senderProver = ErgoNamesUtils.buildProver(ctx, conf.getNode)

        val mintRequestBox = ErgoNamesUtils.getMintRequestBox(ctx, mintingContractAddress, mintRequestBoxId)
        if (mintRequestBox == null)
          throw new ErgoClientException(s"Could not find mint request with box id $mintRequestBoxId", null)

        // Needed to cover tx fee. Or is it needed? Can't we deduct it from mintRequestBox? Try it!
        val boxesToSpend = ErgoNamesUtils.getBoxesToSpendFromWallet(ctx, totalToSpend = Parameters.MinFee)
        if (!boxesToSpend.isPresent)
          throw new ErgoClientException(s"Not enough coins in the wallet to pay ${Parameters.MinFee}", null)

        // TODO: Move to ErgoNamesUtils
        val inputs = List.concat(List(mintRequestBox), boxesToSpend.get.asScala)
        val (tx, _) = createTx(ctx, inputs.asJava, senderProver.getAddress,mintRequestBox, ergoNamesStandardTokenDescription, networkType)

        val signedTx = senderProver.sign(tx)
        val txId = ctx.sendTransaction(signedTx)
        signedTx.toJson(true)
    })
    txJson
  }

  def getSqsClient(region: String): AmazonSQS = {
    val sqsClient = AmazonSQSClientBuilder.standard().withRegion(region).build()
    sqsClient
  }

 /*
  This function is an entrypoint for an AWS lambda consuming events form sqs.
   - aws feeds a SQSEvent to this function.
   - an SQSEvent contains multiple SQS messages
   - this function expects each sqs message to be a json string described by MintRequestSqsMessage
   - this function parses each json sqs message into a MintRequestSqsMessage object
   - then it feeds each MintRequestSqsMessage to the function which mints an ergoname NFT
 */
  def lambdaEventHandler(sqsEvent: SQSEvent, context: Context) : Unit = {
        println("Getting AWS region")
        val awsRegion = AwsHelper.getRegion
        if (awsRegion == null)
          throw new Exception("Could not find aws region")

        println("Getting AWS Secrets Manager, secret name for Ergo node config")
        val secretName = ConfigManager.get("secretName")
        if (secretName == null)
          throw new Exception("Could not find config value 'secretName'")
        println(s"Getting secret $secretName from AWS Secrets Manager")
        val secretString = AwsHelper.getSecretFromSecretsManager(secretName, awsRegion)

        println("Parsing secret string into ErgoToolConfig type")
        val ergoNodeConfig = ErgoToolConfig.load(new StringReader(secretString))

        val networkType = ergoNodeConfig.getNode.getNetworkType
        println(s"Targeting $networkType")

        // TODO: Update minting method to take an ergoClient rather than a config to instantiate one
        println(s"Building Ergo client for interacting with node at ${ergoNodeConfig.getNode.getNodeApi.getApiUrl}")
        val ergoClient = ErgoNamesUtils.buildErgoClient(ergoNodeConfig.getNode, ergoNodeConfig.getNode.getNetworkType)

        val mintingContractAddress = ergoNodeConfig.getParameters.get("mintingContractAddress")
        println(s"Minting Contract Address: $mintingContractAddress")

        val queueUrl = ConfigManager.get("mintRequestsQueueUrl")
        if (queueUrl == null)
          throw new Exception("Could not find config value 'mintRequestsQueue'")

        println(s"Building AWS SQS Client for queue $queueUrl")
        val sqsClient = getSqsClient(awsRegion)

        val sqsMessages = sqsEvent.getRecords.asScala
        println(s"Pulled ${sqsMessages.length} message(s) from queue")
        println("Extracting and parsing mint request(s) from SQS message(s)")
        val mintRequests = sqsMessages.map{ m =>
          val parsed = Json.parse(m.getBody).as[MintRequestSqsMessage]
          (m, parsed)
        }

        println("Checking if Lambda is running in dry mode")
        val dry = ConfigManager.get("dry")
        if (dry == null) throw new Exception("Could not find config flag 'dry'")
        val dryMsg = if (dry.toBoolean)  "Running in dry mode - will not process mint request or attempt to query node"
                     else "NOT Running in dry mode - will attempt to process minting request and query node"
        println(dryMsg)

        mintRequests.foreach{
         case (message, mintRequest) =>
           // ToDo handle failures here so that a single request failure does not taint the entire batch of sqs messages
           println(s"mint request: $mintRequest")
           if (!dry.toBoolean){
            println(s"Attempting to process mint request box ${mintRequest.mintRequestBoxId} issued by mint tx ${mintRequest.mintTxId}")
             // ToDo avoid creating an ergo client per message
             // TODO: Update processMintingRequest to take Address instead of String
             processMintingRequest(
               ergoNodeConfig,
               networkType,
               mintingContractAddress,
               mintRequest.mintRequestBoxId,
               ergoNodeConfig.getParameters.get("ergoNamesTokenDescription"))
           }
           sqsClient.deleteMessage(queueUrl, message.getReceiptHandle)
           print("Deleted message")
       }
  }
}

object ProcessMintingRequest extends Minter {
  def main(args: Array[String]) : Unit = {
    val conf: ErgoToolConfig = ErgoToolConfig.load("config.json")
    val networkType = if (conf.getNode.getNetworkType == "TESTNET") NetworkType.TESTNET else NetworkType.MAINNET
    val tokenDesc = conf.getParameters.get("tokenDescription")
    val mintRequestBoxId = conf.getParameters.get("nftMintRequestBoxId")
    val mintingContractAddress = conf.getParameters.get("mintingContractAddress")
    val txJson = processMintingRequest(conf, networkType, mintingContractAddress, mintRequestBoxId, tokenDesc)
    print(txJson)
  }
}
