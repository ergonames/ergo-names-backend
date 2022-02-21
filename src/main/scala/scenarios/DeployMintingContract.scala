package scenarios

import contracts.ErgoNamesMintingContract
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit._
import utils.ErgoNamesUtils

object DeployMintingContract {
  def deployMintingContract(conf: ErgoToolConfig, networkType: NetworkType): String = {
    val ergoClient = ErgoNamesUtils.buildErgoClient(conf.getNode, networkType)

    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {
        val senderProver = ErgoNamesUtils.buildProver(ctx, conf.getNode)

        val totalToSpend = Parameters.MinChangeValue + Parameters.MinFee
        val boxesToSpend = ErgoNamesUtils.getBoxesToSpendFromWallet(ctx, totalToSpend)
        if (!boxesToSpend.isPresent)
          throw new ErgoClientException(s"Not enough coins in the wallet to pay $totalToSpend", null)

        val contractBox: OutBox = ErgoNamesUtils.buildContractBox(
          ctx,
          amountToSend = Parameters.MinChangeValue,
          script = ErgoNamesMintingContract.getScript,
          ergoNamesPk = senderProver.getP2PKAddress.pubkey)

        val tx: UnsignedTransaction = ErgoNamesUtils.buildUnsignedTx(
          ctx,
          inputs = boxesToSpend.get,
          outputs = contractBox,
          fee = Parameters.MinFee,
          changeAddress = senderProver.getP2PKAddress)

        val signedTx = senderProver.sign(tx)
        val txId = ctx.sendTransaction(signedTx)
        signedTx.toJson(true)
    })
    txJson
  }

  def main(args: Array[String]) : Unit = {
    val conf: ErgoToolConfig = ErgoToolConfig.load("config.json")
    val networkType = if (conf.getNode.getNetworkType == "TESTNET") NetworkType.TESTNET else NetworkType.MAINNET
    val txJson = deployMintingContract(conf, networkType)
    print(txJson)
  }
}
