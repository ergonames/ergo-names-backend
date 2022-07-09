package services

import models.MintRequestArgs
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit._
import org.ergoplatform.restapi.client.UtxoApi
import sigmastate.serialization.ErgoTreeSerializer
import special.collection.CollOverArray
import utils.ErgoNamesUtils

import scala.collection.JavaConverters._

class Minter(/*networkType: NetworkType = NetworkType.TESTNET*/) {
  def mint(boxId: String, ctx: BlockchainContext, prover: ErgoProver, nodeService: UtxoApi): String = {
    // GET INPUT(S)
    val mintRequestInBox = getMintRequestBox(ctx, nodeService, boxId)

    // BUILD OUTPUTS
    val txFee = Parameters.MinFee
    val nftIssuanceBoxValue = Parameters.MinChangeValue
    val paymentCollectionBoxValue = mintRequestInBox.getValue - txFee - nftIssuanceBoxValue

    // OUTPUT 1
    val nftIssuanceOutBox = {
      // TODO: Get standard token description from config or something
      // TODO: Generate asset type, image hash and url
      val mintRequestArgs = extractArgsFromMintRequestBox(mintRequestInBox, ctx.getNetworkType)
      val eip4CompliantRegisters = buildEIP4CompliantRegisters(mintRequestArgs.tokenName, "token description", 0, "assetType", "imageHash", "imageUrl")
      // TODO: Figure out how to pass R4-R9 and use them properly inside this method
      val nft = buildNft(mintRequestInBox.getId.toString, mintRequestArgs.tokenName)

      buildNftIssuanceOutBox(ctx, nftIssuanceBoxValue, mintRequestArgs, nft, eip4CompliantRegisters)
    }

    // OUTPUT 2
    val paymentCollectionOutBox = buildPaymentCollectionOutBox(ctx, paymentCollectionBoxValue, prover.getAddress)

    // BUILD UNSIGNED TX
    val inputs = List(mintRequestInBox)
    val outputs = List(nftIssuanceOutBox, paymentCollectionOutBox)
    val unsignedTx = buildUnsignedTx(ctx, inputs, outputs, Parameters.MinFee, prover.getP2PKAddress)

    // SIGN AND SUBMIT TX
    val signedTx = prover.sign(unsignedTx)
    val txId = ctx.sendTransaction(signedTx)
    txId
  }

  def getMintRequestBox(ctx: BlockchainContext, nodeService: UtxoApi, boxId: String): InputBox = {
    //   getFromMempool, if null getFromOnChainUtxoSet
    val mempoolBox = ErgoNamesUtils.getUnspentBoxFromMempool(ctx, nodeService, boxId)
    if (mempoolBox != null)
      return mempoolBox

    val utxo = ErgoNamesUtils.getUnspentBoxFromUtxoSet(ctx, nodeService, boxId)
    utxo
  }

  def buildNft(boxId: String, tokenName: String): Eip4Token = {
    // Because it's an NFT, the amount must be 1
//    new ErgoToken(boxId, 1)
      new Eip4Token(boxId, 1, tokenName, "description TBD", 0)
  }

  def extractArgsFromMintRequestBox(mintRequestBox: InputBox, networkType: NetworkType): MintRequestArgs = {
    val R5_tokenNameBytes = mintRequestBox.getRegisters.get(1).getValue.asInstanceOf[CollOverArray[Byte]].toArray
    val R6_expectedPaymentAmount = mintRequestBox.getRegisters.get(2).getValue.asInstanceOf[Long]
    val R7_receiverAddressBytes = mintRequestBox.getRegisters.get(3).getValue.asInstanceOf[CollOverArray[Byte]].toArray

    val deserializedReceiverAddress = ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(R7_receiverAddressBytes)
    val receiverAddress = Address.fromErgoTree(deserializedReceiverAddress, networkType)

    MintRequestArgs(new String(R5_tokenNameBytes), R6_expectedPaymentAmount, receiverAddress)
  }

  def buildEIP4CompliantRegisters(tokenName: String, tokenDescription: String, tokenDecimals: Long, assetType: String, imageHash: String, imageUrl: String): List[ErgoValue[_]] = {
    // https://github.com/ergoplatform/eips/blob/master/eip-0004.md
    val R4_tokenName = ErgoValue.of(tokenName.getBytes())
    val R5_tokenDescription = ErgoValue.of(tokenDescription.getBytes())
    val R6_tokenDecimals = ErgoValue.of(tokenDecimals)
    val R7_assetType = ErgoValue.of(assetType.getBytes())
    val R8_imageHash = ErgoValue.of(imageHash.getBytes())
    val R9_imageUrl = ErgoValue.of(imageUrl.getBytes())

//    List(R4_tokenName, R5_tokenDescription, R6_tokenDecimals, R7_assetType, R8_imageHash, R9_imageUrl)
    List(R7_assetType, R8_imageHash, R9_imageUrl)
  }

  def buildNftIssuanceOutBox(ctx: BlockchainContext, boxValue: Long, mintRequestArgs: MintRequestArgs, nft: Eip4Token, eip4CompliantRegisters: List[ErgoValue[_]]): OutBox = {
    // TODO: Make this box FULLY EIP-4 compliant
    ctx.newTxBuilder.outBoxBuilder
      .value(boxValue)
      .contract(new ErgoTreeContract(mintRequestArgs.receiverAddress.getErgoAddress.script, ctx.getNetworkType))
      // TODO: Pull token description and number of decimals from config
      // TODO: Check what happens if you set tokenName to empty string here, but specify it in registers
      .mintToken(nft)
      .registers(eip4CompliantRegisters:_*)
      .build()
  }

  def buildPaymentCollectionOutBox(ctx: BlockchainContext, expectedPaymentAmount: Long, paymentCollectionAddress: Address): OutBox = {
    val contract = new ErgoTreeContract(paymentCollectionAddress.getErgoAddress.script, ctx.getNetworkType)

    ctx.newTxBuilder.outBoxBuilder
      .value(expectedPaymentAmount)
      .contract(contract)
      .build()
  }

  // TODO: Move this to a more general utility class/object
  def buildUnsignedTx(ctx: BlockchainContext, inputs: List[InputBox], outputs: List[OutBox], fee: Long, changeAddress: ErgoAddress): UnsignedTransaction = {
    ctx.newTxBuilder()
      .boxesToSpend(inputs.asJava)
      .outputs(outputs:_*)
      .fee(fee)
      .sendChangeTo(changeAddress)
      .build()
  }
}
