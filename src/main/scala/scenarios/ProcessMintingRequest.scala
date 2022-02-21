package scenarios

import org.ergoplatform.appkit.{Address, BlockchainContext, ErgoClientException, InputBox, NetworkType, Parameters}
import org.ergoplatform.appkit.config.ErgoToolConfig
import utils.ErgoNamesUtils

import scala.collection.JavaConverters._

object ProcessMintingRequest {
  def processMintingRequest(conf: ErgoToolConfig, networkType: NetworkType): String = {
    val ergoClient = ErgoNamesUtils.buildErgoClient(conf.getNode, networkType)

    val mintingContractAddress = Address.create(conf.getParameters.get("mintingContractAddress"))
    val mintRequestBoxId = conf.getParameters.get("nftMintRequestBoxId")
    val ergoNamesStandardTokenDescription = conf.getParameters.get("tokenDescription")

    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {
        val senderProver = ErgoNamesUtils.buildProver(ctx, conf.getNode)

        val mintRequestBox = ErgoNamesUtils.getMintRequestBox(ctx, mintingContractAddress, mintRequestBoxId)
        if (mintRequestBox == null)
          throw new ErgoClientException(s"Could not find mint request with box id $mintRequestBoxId", null)

        // Needed to cover tx fee. Or is it needed? Can't we deduct it from mintRequestBox? Try it!
        val boxesToSpend = ErgoNamesUtils.getBoxesToSpendFromWallet(ctx, totalToSpend = Parameters.MinFee)
        if (!boxesToSpend.isPresent)
          throw new ErgoClientException(s"Not enough coins in the wallet to pay ${Parameters.MinFee}", null)

        val issuanceBox = ErgoNamesUtils.buildBoxWithTokenToMint(
          ctx,
          networkType,
          value = Parameters.MinChangeValue,
          mintRequestBox, // contains some register data to be extracted
          tokenDescription = ergoNamesStandardTokenDescription)

        val paymentCollectionBox = ErgoNamesUtils.buildPaymentCollectionBox(
          ctx,
          mintRequestBox,
          senderProver.getP2PKAddress)

        // TODO: Move to ErgoNamesUtils
        val inputs = List.concat(List(mintRequestBox), boxesToSpend.get.asScala)
        val tx = ctx.newTxBuilder
          .boxesToSpend(inputs.asJava)
          .outputs(issuanceBox, paymentCollectionBox)
          .fee(Parameters.MinFee)
          .sendChangeTo(senderProver.getP2PKAddress)
          .build()

        val signedTx = senderProver.sign(tx)
        val txId = ctx.sendTransaction(signedTx)
        signedTx.toJson(true)
    })
    txJson
  }

  def main(args: Array[String]) : Unit = {
    val conf: ErgoToolConfig = ErgoToolConfig.load("config.json")
    val txJson = processMintingRequest(conf, NetworkType.TESTNET)
    print(txJson)
  }
}
