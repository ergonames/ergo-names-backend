package scenarios


import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.{SQSEvent}
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import play.api.libs.json._
import play.api.libs.functional.syntax._

import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoClientException, InputBox, NetworkType, Parameters, OutBox}
import org.ergoplatform.P2PKAddress

import org.ergoplatform.appkit.config.ErgoToolConfig
import utils.ErgoNamesUtils

import scala.collection.JavaConverters._

object Config {

  implicit val configReads = Json.reads[Config]
  def load(pathToConfigFile: String) = {
    val content = scala.io.Source.fromFile(pathToConfigFile).mkString
    Json.parse(content).as[Config]
  }
}

case class Config(queueUrl:String, dry: Boolean)

case class MintRequestSqsMessage(tokenDescription: String, mintRequestBoxId: String)

case class MintingTxArgs(inputs: List[InputBox], outputs: List[OutBox], fee: Long, receiverAddress: P2PKAddress)

trait Minter{
  implicit val mintRequestSqsMessageReads = Json.reads[MintRequestSqsMessage]
  implicit val mintRequestSqsMessageWrites = Json.writes[MintRequestSqsMessage]

  def createTx(ctx: BlockchainContext,
    inputs: java.util.List[InputBox],
    senderAddress: Address,
    mintRequestBox: InputBox,
    ergoNamesStandardTokenDescription: String,  networkType: NetworkType) = {

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

        val inputsWithMintBox =  List(mintRequestBox) ++ (inputs).asScala

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
      val (tx, _) = createTx(ctx, inputs.asJava, senderProver.getAddress(),mintRequestBox, ergoNamesStandardTokenDescription, networkType)

        val signedTx = senderProver.sign(tx)
        val txId = ctx.sendTransaction(signedTx)
        signedTx.toJson(true)
    })
    txJson
  }

  def getSqsClient(region: String) = {
    val sqsClient = AmazonSQSClientBuilder.standard().withRegion(region).build()
    (sqsClient)
  }

 /*
  This function is an entrypoint for an AWS lambda consuming events form sqs.
   - aws feeds a SQSEvent to this function.
   - a SQSEvent contains multiple SQS messages
   - this function expects each sqs message to be a json string describe by MintRequestSqsMessage
   - this function parses each json sqs message into a MintRequestSqsMessage object
   - then it feeds each MintRequestSqsMessage to the function which mints a domain
 */ 
  def lambdaEventHandler(sqsEvent: SQSEvent, context: Context) : Unit = {
        val config = Config.load("config.json")
        val sqsClient = getSqsClient("us-west-2")
        val conf: ErgoToolConfig = ErgoToolConfig.load("ergo_node_config.json")
        val networkType = if (conf.getNode.getNetworkType == "TESTNET") NetworkType.TESTNET else NetworkType.MAINNET
       val mintingContractAddress = conf.getParameters.get("mintingContractAddress")
       val sqsMessages = sqsEvent.getRecords().asScala
    val mintRequests = sqsMessages.map{ m =>
      val parsed =  Json.parse(m.getBody()).as[MintRequestSqsMessage]
      (m, parsed)
    }
                                     
       mintRequests.map{
         case (message, mintRequest) =>
           // ToDo handle failures here so that a single request failure does not taint the entire batch of sqs messages
             println("mintRequest")
             println(mintRequest)
             if (!config.dry){
               // ToDo avoid creating an ergoclinet per message
               processMintingRequest(conf, networkType, mintingContractAddress,
               mintRequest.mintRequestBoxId,
               mintRequest.tokenDescription)
             }
             sqsClient.deleteMessage(config.queueUrl, message.getReceiptHandle())
             print("deleted message")
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
