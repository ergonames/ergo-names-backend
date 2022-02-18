package SubmitPaymentForNFT

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.{ErgoNodeConfig, ErgoToolConfig}
import org.ergoplatform.appkit.impl.ErgoTreeContract
import sigmastate.eval.Colls
import special.collection.Coll

object SubmitPaymentForNFT {
  def submitPaymentForNFT(configFileName: String): String = {
    // Node configuration values
    val conf: ErgoToolConfig = ErgoToolConfig.load(configFileName)
    val nodeConf: ErgoNodeConfig = conf.getNode
    val explorerUrl: String = RestApiErgoClient.getDefaultExplorerUrl(NetworkType.TESTNET)

    // Fetch parameters from config
    val addressIndex: Int = conf.getParameters.get("addressIndex").toInt
    val mintingContractAddress: Address = Address.create(conf.getParameters.get("mintingContractAddress"))
    val tokenName: String = conf.getParameters.get("tokenName")
    val paymentAmount: Long = conf.getParameters.get("paymentAmount").toLong
    val nftReceiverAddress: Address = Address.create(conf.getParameters.get("nftReceiverAddress"))

    // Create ErgoClient instance (represents a connection to node)
    val ergoClient: ErgoClient = RestApiErgoClient.create(nodeConf, explorerUrl)

    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {

      // Initialize prover (signs the transaction)
      val senderProver: ErgoProver = ctx.newProverBuilder
        .withMnemonic(
          SecretString.create(nodeConf.getWallet.getMnemonic),
          SecretString.create(nodeConf.getWallet.getPassword))
        .withEip3Secret(addressIndex)
        .build()

      // Get input (spending) boxes from node wallet
      val wallet: ErgoWallet = ctx.getWallet
      val amountToSpend: Long = paymentAmount
      val totalToSpend: Long = amountToSpend + Parameters.MinFee
      val boxes: java.util.Optional[java.util.List[InputBox]] = wallet.getUnspentBoxes(totalToSpend)
      if (!boxes.isPresent)
        throw new ErgoClientException(s"Not enough coins in the wallet to pay $totalToSpend", null)

      // Create unsigned tx builder
      val txBuilder: UnsignedTransactionBuilder = ctx.newTxBuilder()

      // Create output box
      val expectedTokenName: ErgoValue[Coll[Byte]] = ErgoValue.of(tokenName.getBytes)
      val expectedPaymentAmount: ErgoValue[Long] = ErgoValue.of(paymentAmount)
      val receiverAddressBytes: Coll[Byte] = Colls.fromArray(nftReceiverAddress.getErgoAddress.script.bytes)
      val expectedReceiverAddress: ErgoValue[Coll[Byte]] = ErgoValue.of(receiverAddressBytes, ErgoType.byteType)

      val newBox = txBuilder.outBoxBuilder
        .value(amountToSpend)
        .contract(new ErgoTreeContract(mintingContractAddress.getErgoAddress.script))
        .registers(expectedTokenName, expectedPaymentAmount, expectedReceiverAddress)
        .build()

      // Create unsigned transaction
      val tx: UnsignedTransaction = txBuilder
        .boxesToSpend(boxes.get)
        .outputs(newBox)
        .fee(Parameters.MinFee)
        .sendChangeTo(senderProver.getP2PKAddress)
        .build()

      val signed: SignedTransaction = senderProver.sign(tx)

      val txId: String = ctx.sendTransaction(signed)

      signed.toJson(true)
    })
    txJson
  }

  def main(args: Array[String]): Unit = {
    val txJson: String = submitPaymentForNFT("config.json")
    println(txJson)
  }
}
