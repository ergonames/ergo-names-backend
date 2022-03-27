package scenarios


import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.sqs.{AmazonSQS, AmazonSQSClientBuilder}
import models.{ErgoNamesConfig, MintRequestSqsMessage, MintingTxArgs}
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import play.api.libs.json._
import scala.util.{Try,Success,Failure}
import utils.{AwsHelper, ConfigManager, ErgoNamesUtils}

import java.io.StringReader
import scala.collection.JavaConverters._

trait Minter {
  implicit val mintRequestSqsMessageReads: Reads[MintRequestSqsMessage] = Json.reads[MintRequestSqsMessage]
  implicit val mintRequestSqsMessageWrites: OWrites[MintRequestSqsMessage] = Json.writes[MintRequestSqsMessage]

  def createTx(ctx: BlockchainContext,
               boxesToCoverTxFees: java.util.List[InputBox],
               senderAddress: Address,
               mintRequestBox: InputBox,
               ergoNamesStandardTokenDescription: String): (UnsignedTransaction, MintingTxArgs) = {
          println("Building issuance box arguments")
          val (token, tokenName, tokenDesc, tokenDecimals, ergValue, contract) = ErgoNamesUtils.issuanceBoxArgs(
            ctx.getNetworkType,
            value = Parameters.MinChangeValue,
            mintRequestBox, // contains some register data to be extracted
            tokenDescription = ergoNamesStandardTokenDescription)

          println("Building issuance box")
          val issuanceBox = ErgoNamesUtils.buildBoxWithTokenToMint(ctx,
            token, tokenName, tokenDesc,
            tokenDecimals, ergValue, contract)

          println("Building payment collection box")
          val paymentCollectionBox = ErgoNamesUtils.buildPaymentCollectionBox(
            ctx,
            mintRequestBox,
            senderAddress.asP2PK())

          val inputsWithMintBox =  List(mintRequestBox) ++ (boxesToCoverTxFees).asScala

          println("Building minting tx")
          val tx = ctx.newTxBuilder
            .boxesToSpend(inputsWithMintBox.asJava)
            .outputs(issuanceBox, paymentCollectionBox)
            .fee(Parameters.MinFee)
            .sendChangeTo(senderAddress.asP2PK())
            .build()

          val txArgs = MintingTxArgs(inputsWithMintBox, List(issuanceBox, paymentCollectionBox), Parameters.MinFee, senderAddress.asP2PK())

          (tx, txArgs)
    }

  // TODO: Consider passing ErgoClient and ErgoProver
  def processMintingRequest(conf: ErgoToolConfig, mintContractAddress:String, mintRequestBoxId: String,  ergoNamesStandardTokenDescription: String): String = {
        val ergoClient = ErgoNamesUtils.buildErgoClient(conf.getNode, conf.getNode.getNetworkType)
        val mintingContractAddress = Address.create(mintContractAddress)
        val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {
            println("Building prover")
            val senderProver = ErgoNamesUtils.buildProver(ctx, conf.getNode)

            println(s"Fetching mint request box with id $mintRequestBoxId from contract address $mintingContractAddress")
            val mintRequestBox = ErgoNamesUtils.getMintRequestBox(ctx, mintingContractAddress, mintRequestBoxId)
            if (mintRequestBox == null)
              throw new ErgoClientException(s"Could not find mint request with box id $mintRequestBoxId", null)

            println(s"Getting ${Parameters.MinFee} nanoergs from wallet to cover tx fee")
            // TODO: Alternatively, consider including tx fee as part of payment.
            //  This way, we don't need to fetch boxes from the ErgoNames wallet, which would result in one less call to the node.
            val boxesToCoverTxFees = ErgoNamesUtils.getBoxesToSpendFromWallet(ctx, totalToSpend = Parameters.MinFee)
            if (!boxesToCoverTxFees.isPresent)
              throw new ErgoClientException(s"Not enough coins in the wallet to pay ${Parameters.MinFee}", null)

            // TODO: Consider passing concatenated inputs to createTx. Would reduce number of params AND wouldnt need to concat them again later
            val (tx, _) = createTx(ctx, boxesToCoverTxFees.get, senderProver.getAddress, mintRequestBox, ergoNamesStandardTokenDescription)

            println("Signing minting tx")
            val signedTx = senderProver.sign(tx)
            println("Sending signed minting to node")
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

        // Getting a config from env or from file
        val config: ErgoNamesConfig = ConfigManager.getConfig match {
          case Success(c) => c
          case Failure(x) => throw new Exception(x)
        }

        println(s"Getting secret $config.secretName from AWS Secrets Manager")
        val secretString = AwsHelper.getSecretFromSecretsManager(config.secretName, config.awsRegion)
        println("Parsing secret string into ErgoToolConfig type")
        val ergoNodeConfig = ErgoToolConfig.load(new StringReader(secretString))

        val networkType = ergoNodeConfig.getNode.getNetworkType
        println(s"Targeting $networkType")

        // TODO: Update minting method to take an ergoClient rather than a config to instantiate one
        println(s"Building Ergo client for interacting with node at ${ergoNodeConfig.getNode.getNodeApi.getApiUrl}")
        val ergoClient = ErgoNamesUtils.buildErgoClient(ergoNodeConfig.getNode, ergoNodeConfig.getNode.getNetworkType)

        val mintingContractAddress = ergoNodeConfig.getParameters.get("mintingContractAddress")
        println(s"Minting Contract Address: $mintingContractAddress")

        val queueUrl = config.mintRequestsQueueUrl
        println(s"Building AWS SQS Client for queue $queueUrl")
        val sqsClient = getSqsClient(config.awsRegion)

        val sqsMessages = sqsEvent.getRecords.asScala
        println(s"Pulled ${sqsMessages.length} message(s) from queue")
        println("Extracting and parsing mint request(s) from SQS message(s)")
        val mintRequests = sqsMessages.map{ m =>
          val parsed = Json.parse(m.getBody).as[MintRequestSqsMessage]
          (m, parsed)
        }

        println("Checking if Lambda is running in dry mode")
   
        val dryMsg = if (config.dry)  "Running in dry mode - will not process mint request or attempt to query node"
                     else "NOT Running in dry mode - will attempt to process minting request and query node"
        println(dryMsg)

        mintRequests.foreach{
         case (message, mintRequest) =>
           // ToDo handle failures here so that a single request failure does not taint the entire batch of sqs messages
           println(s"mint request: $mintRequest")
           if (!config.dry){
              println(s"Attempting to process mint request box ${mintRequest.mintRequestBoxId} issued by mint tx ${mintRequest.mintTxId}")
              // ToDo avoid creating an ergo client per message
              // TODO: Update processMintingRequest to take Address instead of String
              val txJson = processMintingRequest(
                ergoNodeConfig,
                mintingContractAddress,
                mintRequest.mintRequestBoxId,
                ergoNodeConfig.getParameters.get("ergoNamesTokenDescription"))

              println("Successfully submitted minting tx to node")
              println(txJson)
           }

           sqsClient.deleteMessage(queueUrl, message.getReceiptHandle)
           print("Deleted message")
       }
  }
}

object ProcessMintingRequest extends Minter {
  def main(args: Array[String]) : Unit = {
    val conf: ErgoToolConfig = ErgoToolConfig.load("ergo_node_config.json")
    val networkType = conf.getNode.getNetworkType
    val tokenDesc = conf.getParameters.get("tokenDescription")
    val mintRequestBoxId = conf.getParameters.get("mintRequestBoxId")
    val mintingContractAddress = conf.getParameters.get("mintingContractAddress")
    val txJson = processMintingRequest(conf, mintingContractAddress, mintRequestBoxId, tokenDesc)
    print(txJson)
  }
}
