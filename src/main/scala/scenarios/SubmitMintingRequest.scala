package scenarios

import contracts.ErgoNamesMintingContract
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import utils.ErgoNamesUtils

object SubmitMintingRequest {

  def createTx(
    ctx: BlockchainContext,
    boxesToSpend:  java.util.List[InputBox],
    mintingContractAddress: Address,
    royaltyPercentage: Int,
    tokenName: String,
    paymentAmount: Long,
    nftReceiverAddress: Address,
    senderAddress: Address): UnsignedTransaction = {

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

    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {
        val mintingContractAddress = ErgoNamesMintingContract.getContractAddress(ctx, conf.getNode.getWallet)
        val royaltyPercentage = conf.getParameters.get("royaltyPercentage").toInt
        val tokenName = conf.getParameters.get("tokenName")
        val paymentAmount = conf.getParameters.get("paymentAmount").toLong
        val nftReceiverAddress = Address.create(conf.getParameters.get("nftReceiverAddress"))

        val senderProver = ErgoNamesUtils.buildProver(ctx, conf.getNode)

        // we are accounting for this tx's fee, the minting tx's fee, AND the min amount of ERG we need to include along with the NFT back to the user
        val totalToSpend = (paymentAmount + Parameters.MinFee) + (Parameters.MinFee + Parameters.MinChangeValue)
        val boxesToSpend = ErgoNamesUtils.getUnspentBoxesFromWallet(conf, totalToSpend)

        val tx = createTx(ctx, boxesToSpend, mintingContractAddress, royaltyPercentage, tokenName, paymentAmount, nftReceiverAddress, senderProver.getEip3Addresses.get(0))

        val signedTx = senderProver.sign(tx)
        val txId = ctx.sendTransaction(signedTx)
        signedTx.toJson(true)
    })
    txJson
  }

  def main(args: Array[String]) : Unit = {
    val conf: ErgoToolConfig = ErgoToolConfig.load("ergo_node_config.json")
    val networkType = conf.getNode.getNetworkType
    val txJson = submitMintingRequest(conf, networkType)
    print(txJson)
  }
}
