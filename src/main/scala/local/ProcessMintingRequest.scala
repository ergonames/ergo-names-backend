package local

import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.config.ErgoToolConfig
import services.Minter
import utils.ErgoNamesUtils

object ProcessMintingRequest {
  def main(args: Array[String]): Unit = {
    val ergoConfig = ErgoToolConfig.load("ergo_node_config.json")
    val ergoClient = ErgoNamesUtils.buildErgoClient(ergoConfig.getNode, ergoConfig.getNode.getNetworkType)
    val nodeService = ErgoNamesUtils.buildNodeService(ergoConfig)
    val walletService = ErgoNamesUtils.buildNewWalletApiService(ergoConfig)
    val minter = new Minter()

    val txId = ergoClient.execute((ctx: BlockchainContext) => {
      val prover = ErgoNamesUtils.buildProver(ctx, ergoConfig.getNode)
      val mintingRequestBoxId = ergoConfig.getParameters.get("mintRequestBoxId")
      val royalty = ergoConfig.getParameters.get("royaltyPercentage").toInt
      minter.mint(mintingRequestBoxId, royalty, ctx, prover, nodeService, walletService)
    })

    println(txId)
  }
}
