package utils

import contracts.ErgoNamesMintingContract
import models.AppKitWorkaround.NewBoxesRequestHolder
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.{ErgoNodeConfig, ErgoToolConfig}
import org.ergoplatform.appkit.impl.{ErgoTreeContract, InputBoxImpl}
import org.ergoplatform.restapi.client.{ApiClient, UtxoApi, WalletApi}
import org.ergoplatform.{ErgoAddress, P2PKAddress}
import services.AppKitWorkaround.NewWalletApi
import sigmastate.basics.DLogProtocol.ProveDlog
import sigmastate.serialization.ErgoTreeSerializer
import special.collection.CollOverArray

import scala.collection.JavaConverters._

object ErgoNamesUtils {
  def buildErgoClient(nodeConf: ErgoNodeConfig, networkType: NetworkType): ErgoClient = {
    RestApiErgoClient.create(nodeConf, RestApiErgoClient.getDefaultExplorerUrl(networkType))
  }

  def buildProver(ctx: BlockchainContext, nodeConf: ErgoNodeConfig): ErgoProver = {
    ctx.newProverBuilder
      .withMnemonic(
        SecretString.create(nodeConf.getWallet.getMnemonic),
        SecretString.create(nodeConf.getWallet.getPassword)
      )
      .withEip3Secret(0)
      .withEip3Secret(1)
      .build()
  }

  def getUnspentBoxesFromWallet(conf: ErgoToolConfig, totalToSpend: Long): java.util.List[InputBox] = {
    val walletService = buildNewWalletApiService(conf)

    val getWalletBoxesRequest = new NewBoxesRequestHolder()
      .targetBalance(totalToSpend)
      // targetAssets cannot be empty or node API will reject request
      .targetAssets(new java.util.HashMap[String, java.lang.Long]())
    val response = walletService.walletBoxesCollect(getWalletBoxesRequest).execute()

    if (!response.isSuccessful)
      throw new Exception(s"Something went wrong when trying to get boxes from wallet for a total amount of $totalToSpend. ${response.message()}")

    if (response.body() == null)
      throw new Exception(s"Not enough boxes in wallet to cover balance of $totalToSpend")

    val walletBoxes = response.body()
      .getBoxes
      .asScala
      .map(output => new InputBoxImpl(output).asInstanceOf[InputBox])
      .asJava

    walletBoxes
  }

  def buildContractBox(ctx: BlockchainContext, amountToSend: Long, script: String, ergoNamesPk: ProveDlog): (OutBox, ErgoContract) = {
    val compiledContract: ErgoContract = ErgoNamesMintingContract.getContract(ctx, ergoNamesPk)

    val b = ctx.newTxBuilder.outBoxBuilder
      .value(amountToSend)
      .contract(compiledContract)
      .build()

    (b, compiledContract)
  }

  def buildUnsignedTx(ctx: BlockchainContext, inputs: java.util.List[InputBox], outputs: OutBox, fee: Long, changeAddress: ErgoAddress): UnsignedTransaction = {
    ctx.newTxBuilder
      .boxesToSpend(inputs)
      .outputs(outputs)
      .fee(fee)
      .sendChangeTo(changeAddress)
      .build()
  }

  def buildMintingRequestBox(ctx: BlockchainContext, mintingContractAddress: Address, royalty: Int, tokenName: String, paymentAmount: Long, receiverAddress: Address): OutBox = {
    val expectedRoyalty = ErgoValue.of(royalty)
    val expectedTokenName = ErgoValue.of(tokenName.getBytes)
    val expectedPaymentAmount = ErgoValue.of(paymentAmount)
    val expectedReceiverAddress = ErgoValue.of(receiverAddress.getErgoAddress.script.bytes)

    ctx.newTxBuilder.outBoxBuilder
      .value(paymentAmount + Parameters.MinFee + Parameters.MinChangeValue)
      .contract(new ErgoTreeContract(mintingContractAddress.getErgoAddress.script, ctx.getNetworkType))
      .registers(expectedRoyalty, expectedTokenName, expectedPaymentAmount, expectedReceiverAddress)
      .build()
  }

  def getMintRequestBox(ctx: BlockchainContext, contractAddress: Address, mintRequestBoxId: String): InputBox = {
    // TODO: Add some error handling here in case box is not found.
    //  Make sure to have proper logging around this case.
    //  If a box is not found, it might be because the tx that created it has not been confirmed yet.
    //  In such a case, we'd just need to wait for confirmation.
    //  If we throw an exception, the message will be retied N amount of times before going into the DLQ.
    //  We can tweak redrive policy and message delay configs to give a tx enough time to confirm.
    //  If after X minutes of waiting and retrying the box still can be found, we can let the message go to the DLQ to inspect further.
    // this might need to iterate boxes at the contract address if there are a lot of unspent boxes (unprocessed mint requests)
//    val matches: java.util.List[InputBox] = ctx.getUnspentBoxesFor(contractAddress, 0, 20)
//      .stream()
//      .filter(_.getId == ErgoId.create(mintRequestBoxId))
//      .collect(Collectors.toList())

    // This throws an ErgoClientException with message 'Cannot load UTXO box $boxId' if the tx that issued the box has not been confirmed.
    // Consider using this method instead, catching the exception and just raising another with a more detailed message.
//    Exception in thread "main" org.ergoplatform.appkit.ErgoClientException: Cannot load UTXO box 2da5b5d90bcdc867ba96d629c97f4bed884f17d97d928bcd603f0bb090934b55
    try {
      val boxMatch = ctx.getBoxesById(mintRequestBoxId)
      boxMatch(0)
    } catch {
      case e: ErgoClientException =>
        val exceptionMessage = s"${e.getMessage}. This may mean the tx that issued it has not been confirmed yet. Wait 1 or 2 minutes and try again."
        throw new Exception(exceptionMessage)
    }

//    matches.get(0)
  }

  def issuanceBoxArgs(networkType: NetworkType, value: Long, mintRequestBox: InputBox, tokenDescription: String) = {
    val R5_tokenNameBytes = mintRequestBox.getRegisters.get(1).getValue.asInstanceOf[CollOverArray[Byte]].toArray
    val R7_receiverAddressBytes = mintRequestBox.getRegisters.get(3).getValue.asInstanceOf[CollOverArray[Byte]].toArray
    val deserializedReceiverAddress = ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(R7_receiverAddressBytes)
    val proposedReceiverAddress = Address.fromErgoTree(deserializedReceiverAddress, networkType)

    val proposedTokenName = new String(R5_tokenNameBytes)
    val proposedTokenDescription = tokenDescription
    val tokenDecimals = 0
    val token = new Eip4Token(mintRequestBox.getId.toString, 1, proposedTokenName, proposedTokenDescription, tokenDecimals)
    // issuanceBoxContract
    val contract = new ErgoTreeContract(proposedReceiverAddress.getErgoAddress.script, networkType)
    (token, proposedTokenName, proposedTokenDescription, tokenDecimals, value, contract)
  }

  def buildBoxWithTokenToMint(
    ctx: BlockchainContext,
    token: Eip4Token,
    value: Long,
    contract: ErgoTreeContract): OutBox = {

    ctx.newTxBuilder.outBoxBuilder
      .mintToken(token)
      .value(value)
      .contract(contract)
      .build()
  }

  def buildPaymentCollectionBox(ctx: BlockchainContext, mintingRequestBox: InputBox, ergoNamesP2KAddress: P2PKAddress): OutBox = {
    val R6_expectedPaymentAmount = mintingRequestBox.getRegisters.get(2).getValue.asInstanceOf[Long]
    val collectionAmount = R6_expectedPaymentAmount - Parameters.MinFee

    ctx.newTxBuilder.outBoxBuilder
      .value(collectionAmount)
      .contract(new ErgoTreeContract(ergoNamesP2KAddress.script, ctx.getNetworkType))
      .build()
  }


  ////////////////
  def buildNodeService(conf: ErgoToolConfig): UtxoApi = {
    val nodeClient = new ApiClient(conf.getNode.getNodeApi.getApiUrl, "ApiKeyAuth", conf.getNode.getNodeApi.getApiKey)
    nodeClient.createService(classOf[UtxoApi])
  }

  def buildWalletApiService(conf: ErgoToolConfig): WalletApi = {
    val walletService = new ApiClient(conf.getNode.getNodeApi.getApiUrl, "ApiKeyAuth", conf.getNode.getNodeApi.getApiKey)
    walletService.createService(classOf[WalletApi])
  }

  def buildNewWalletApiService(conf: ErgoToolConfig): NewWalletApi = {
    val walletService = new ApiClient(conf.getNode.getNodeApi.getApiUrl, "ApiKeyAuth", conf.getNode.getNodeApi.getApiKey)
    walletService.createService(classOf[NewWalletApi])
  }

  def getUnspentBoxFromMempool(ctx: BlockchainContext, nodeService: UtxoApi, boxId: String): InputBox = {
    val response = nodeService.getBoxWithPoolById(boxId).execute()
    if (!response.isSuccessful)
      throw new Exception(s"Something went wrong when trying to get box $boxId from mempool. ${response.message()}")

    if (response.body() == null)
      return null

    val inputBox = new InputBoxImpl(response.body()).asInstanceOf[InputBox]
    inputBox
  }

  def getUnspentBoxFromUtxoSet(ctx: BlockchainContext, nodeService: UtxoApi, boxId: String): InputBox = {
    val response = nodeService.getBoxById(boxId).execute()
    if (!response.isSuccessful)
      throw new Exception(s"Something went wrong when trying to get box $boxId from utxo set. ${response.message()}")

    if (response.body() == null)
      return null

    val utxo = new InputBoxImpl(response.body()).asInstanceOf[InputBox]
    utxo
  }
}
