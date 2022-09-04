package local

import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.config.ErgoToolConfig
import services.MintingRequestService
import utils.ErgoNamesUtils

object SubmitMintingRequestLatest {
  def main(args: Array[String]): Unit = {
    val ergoConfig = ErgoToolConfig.load("ergo_node_config.json")
    val ergoClient = ErgoNamesUtils.buildErgoClient(ergoConfig.getNode, ergoConfig.getNode.getNetworkType)
    val mintingRequestService = new MintingRequestService()

    val txJson = ergoClient.execute((ctx: BlockchainContext) => {
      val prover = ErgoNamesUtils.buildProver(ctx, ergoConfig.getNode)
      mintingRequestService.submit(ctx, prover, ergoConfig)
    })

    println(txJson)
  }
}
