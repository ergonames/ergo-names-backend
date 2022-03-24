package scenarios

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import utils.ErgoNamesUtils

object SubmitMintingRequest {

  def createTx(ctx: BlockchainContext,
    boxesToSpend:  java.util.List[InputBox],
    mintingContractAddress: Address,
    royaltyPercentage: Int,
    tokenName: String,
    paymentAmount: Long,
    nftReceiverAddress: Address, senderAddress: Address) = {

         val mintingRequestBox = ErgoNamesUtils.buildMintingRequestBox(
          ctx,
          mintingContractAddress,
          royaltyPercentage,
          tokenName,
          paymentAmount,
          nftReceiverAddress)

        val tx = ErgoNamesUtils.buildUnsignedTx(
          ctx,
          inputs = boxesToSpend,
          outputs = mintingRequestBox,
          fee = Parameters.MinFee,
          changeAddress = senderAddress.asP2PK())
        tx
  }


  def submitMintingRequest(conf: ErgoToolConfig, networkType: NetworkType): String = {
    val ergoClient = ErgoNamesUtils.buildErgoClient(conf.getNode, networkType)

    val mintingContractAddress = Address.create(conf.getParameters.get("mintingContractAddress"))
    val royaltyPercentage = conf.getParameters.get("royaltyPercentage").toInt
    val tokenName = conf.getParameters.get("tokenName")
    val paymentAmount = conf.getParameters.get("paymentAmount").toLong
    val nftReceiverAddress = Address.create(conf.getParameters.get("nftReceiverAddress"))

    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {
        val senderProver = ErgoNamesUtils.buildProver(ctx, conf.getNode)

        val totalToSpend = paymentAmount + Parameters.MinFee
        val boxesToSpend = ErgoNamesUtils.getBoxesToSpendFromWallet(ctx, totalToSpend)
        if (!boxesToSpend.isPresent)
          throw new ErgoClientException(s"Not enough coins in the wallet to pay $totalToSpend", null)

      val tx = createTx(ctx, boxesToSpend.get, mintingContractAddress, royaltyPercentage, tokenName, paymentAmount, nftReceiverAddress, senderProver.getAddress())

        val signedTx = senderProver.sign(tx)
        val txId = ctx.sendTransaction(signedTx)
        signedTx.toJson(true)
    })
    txJson
  }

  def main(args: Array[String]) : Unit = {
    val conf: ErgoToolConfig = ErgoToolConfig.load("config.json")
    val networkType = if (conf.getNode.getNetworkType == "TESTNET") NetworkType.TESTNET else NetworkType.MAINNET
    val txJson = submitMintingRequest(conf, networkType)
    print(txJson)
  }
}
