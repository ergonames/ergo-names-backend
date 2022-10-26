package services

import contracts.ErgoNamesMintingContract
import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoToolConfig
import org.ergoplatform.appkit.impl.ErgoTreeContract
import utils.{ErgoNamesUtils, ErgoNamesUtilsV2}

import scala.collection.JavaConverters._

class MintingRequestService {
  def buildBoxRegisters(tokenName: String, expectedPayment: Long, receiverAddress: Address): List[ErgoValue[_]] = {
    val R4_tokenNameErgoVal = ErgoValue.of(tokenName.getBytes())
    val R5_paymentAmountErgoVal = ErgoValue.of(expectedPayment)
    val R6_receiverAddressErgoVal = ErgoValue.of(receiverAddress.getErgoAddress.script.bytes)

    List(R4_tokenNameErgoVal, R5_paymentAmountErgoVal, R6_receiverAddressErgoVal)
  }

  def buildBox(ctx: BlockchainContext, boxValue: Long, mintingContractAddress: Address, registers: List[ErgoValue[_]]): OutBox = {
    ctx.newTxBuilder.outBoxBuilder
      .value(boxValue)
      .contract(new ErgoTreeContract(mintingContractAddress.getErgoAddress.script, ctx.getNetworkType))
      .registers(registers:_*)
      .build()
  }

  def submit(ctx: BlockchainContext, prover: ErgoProver, ergoConfig: ErgoToolConfig): String = {
    val paymentAddressRaw = ergoConfig.getParameters.get("paymentAddress")
    val paymentAddress = Address.create(paymentAddressRaw)
    // LOAD CONFIG VALUES
    val mintingContractAddress = ErgoNamesMintingContract.getContractAddress(ctx, prover.getEip3Addresses.get(0), paymentAddress)
    val tokenName = ergoConfig.getParameters.get("tokenName")
    val paymentAmount = ergoConfig.getParameters.get("paymentAmount").toLong
    val nftReceiverAddress = Address.create(ergoConfig.getParameters.get("nftReceiverAddress"))

    // GATHER INPUTS
    val inputs = {
      // we are accounting for this tx's fee, the minting tx's fee, AND the min amount of ERG we need to include along with the NFT back to the user
      val totalToSpend = (paymentAmount + Parameters.MinFee) + (Parameters.MinFee + Parameters.MinChangeValue)
      val walletService = ErgoNamesUtils.buildNewWalletApiService(ergoConfig)
      val boxes = ErgoNamesUtils.getUnspentBoxesFromWallet(walletService, totalToSpend).asScala.toList
      boxes
    }

    // BUILD OUTPUTS
    val outputs = {
      val outBoxRegisters = buildBoxRegisters(tokenName, paymentAmount, nftReceiverAddress)
      // Minting Request Box has to have enough fund to cover: 1) tx fee, 2) expected payment, 3) min erg value for issuance box
      val outBoxValue = Parameters.MinFee + paymentAmount + Parameters.MinChangeValue
      val mintingRequestBox = buildBox(ctx, outBoxValue, mintingContractAddress, outBoxRegisters)
      List(mintingRequestBox)
    }

    // BUILD UNSIGNED TX
    val unsignedTx = ErgoNamesUtilsV2.buildUnsignedTx(ctx, inputs, outputs, Parameters.MinFee, prover.getEip3Addresses.get(0).getErgoAddress)

    // SIGN AND SUBMIT TX
    val signedTx = prover.sign(unsignedTx)
    ctx.sendTransaction(signedTx)
    signedTx.toJson(true)
  }
}