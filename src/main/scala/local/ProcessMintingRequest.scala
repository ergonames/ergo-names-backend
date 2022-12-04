package local

import org.ergoplatform.appkit.{Address, BlockchainContext}
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.slf4j.LoggerFactory
import services.Minter
import utils.ErgoNamesUtils

object ProcessMintingRequest {
  val logger = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    val configFileName = "ergo_node_config.json"
    logger.debug("Loading {}", configFileName)
    val ergoConfig = ErgoToolConfig.load(configFileName)
    logger.debug("Building Ergo Client")
    val ergoClient = ErgoNamesUtils.buildErgoClient(ergoConfig.getNode, ergoConfig.getNode.getNetworkType)
    logger.debug("Building Node Service")
    val nodeService = ErgoNamesUtils.buildNodeService(ergoConfig)
    logger.debug("Building Wallet Service")
    val walletService = ErgoNamesUtils.buildNewWalletApiService(ergoConfig)
    logger.debug("Instantiating new Minter")
    val minter = new Minter(LoggerFactory.getLogger(classOf[Minter]))

    logger.debug("Initiating blockchain context")
    val txId = ergoClient.execute((ctx: BlockchainContext) => {
      logger.debug("Building Prover")
      val prover = ErgoNamesUtils.buildProver(ctx, ergoConfig.getNode)
      logger.debug("Reading mintRequestBox from Ergo Config")
      val mintingRequestBoxId = ergoConfig.getParameters.get("mintRequestBoxId")
      logger.debug("Reading royaltyPercentage from Ergo Config")
      val royalty = ergoConfig.getParameters.get("royaltyPercentage").toInt
      logger.debug("Reading paymentAddress from Ergo Config")
      val paymentAddressRaw = ergoConfig.getParameters.get("paymentAddress")
      val paymentAddress = Address.create(paymentAddressRaw)

      logger.debug("Initiating mint")
      minter.mint(mintingRequestBoxId, royalty, ctx, prover, paymentAddress, nodeService, walletService)
    })

    println(txId)
  }
}
