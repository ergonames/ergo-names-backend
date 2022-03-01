package utils

import org.ergoplatform.{ErgoAddress, P2PKAddress}
import org.ergoplatform.appkit.config.ErgoNodeConfig
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import sigmastate.basics.DLogProtocol.ProveDlog
import sigmastate.eval.Colls
import sigmastate.serialization.ErgoTreeSerializer
import special.collection.CollOverArray

import java.util.Optional
import java.util.stream.Collectors

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
      .build()
  }

  def getBoxesToSpendFromWallet(ctx: BlockchainContext, totalToSpend: Long): Optional[java.util.List[InputBox]] = {
    val wallet: ErgoWallet = ctx.getWallet
    wallet.getUnspentBoxes(totalToSpend)
  }

  def buildContractBox(ctx: BlockchainContext, amountToSend: Long, script: String, ergoNamesPk: ProveDlog): (OutBox, ErgoContract) = {
    val compiledContract: ErgoContract = ctx.compileContract(
        ConstantsBuilder.create().item("ergoNamesPk", ergoNamesPk).build(),
        script)

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
    val expectedReceiverAddress = ErgoValue.of(Colls.fromArray(receiverAddress.getErgoAddress.script.bytes), ErgoType.byteType)

    ctx.newTxBuilder.outBoxBuilder
      .value(paymentAmount)
      .contract(new ErgoTreeContract(mintingContractAddress.getErgoAddress.script))
      .registers(expectedRoyalty, expectedTokenName, expectedPaymentAmount, expectedReceiverAddress)
      .build()
  }

  def getMintRequestBox(ctx: BlockchainContext, contractAddress: Address, mintRequestBoxId: String): InputBox = {
    val matches: java.util.List[InputBox] = ctx.getUnspentBoxesFor(contractAddress, 0, 20)
      .stream()
      .filter(_.getId == ErgoId.create(mintRequestBoxId))
      .collect(Collectors.toList())

    matches.get(0)
  }

  def issuanceBoxArgs(ctx:BlockchainContext, networkType: NetworkType, value: Long, mintRequestBox: InputBox, tokenDescription: String) = {
     val R5_tokenNameBytes = mintRequestBox.getRegisters.get(1).getValue.asInstanceOf[CollOverArray[Byte]].toArray
    val R7_receiverAddressBytes = mintRequestBox.getRegisters.get(3).getValue.asInstanceOf[CollOverArray[Byte]].toArray
    val deserializedReceiverAddress = ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(R7_receiverAddressBytes)
    val proposedReceiverAddress = Address.fromErgoTree(deserializedReceiverAddress, networkType)

    val token = new ErgoToken(mintRequestBox.getId, 1)
    val proposedTokenName = new String(R5_tokenNameBytes)
    val proposedTokenDescription = tokenDescription
    val tokenDecimals = 0
    val contract = new ErgoTreeContract(proposedReceiverAddress.getErgoAddress.script)
    (ctx, token, proposedTokenName, proposedTokenDescription, tokenDecimals, value, contract)
  }

  def buildBoxWithTokenToMint(
    ctx: BlockchainContext,
    token: ErgoToken,
    proposedTokenName: String,
    proposedTokenDescription: String,
    tokenDecimals: Int,
    value: Long,
    contract: ErgoTreeContract): OutBox = {

    ctx.newTxBuilder.outBoxBuilder
      .mintToken(token, proposedTokenName, proposedTokenDescription, tokenDecimals)
      .value(value)
      .contract(contract)
      .build()
  }

  def buildPaymentCollectionBox(ctx: BlockchainContext, mintingRequestBox: InputBox, ergoNamesP2KAddress: P2PKAddress): OutBox = {
    val R6_expectedPaymentAmount = mintingRequestBox.getRegisters.get(2).getValue.asInstanceOf[Long]
    val collectionAmount = R6_expectedPaymentAmount - Parameters.MinFee

    ctx.newTxBuilder.outBoxBuilder
      .value(collectionAmount)
      .contract(new ErgoTreeContract(ergoNamesP2KAddress.script))
      .build()
  }
}
