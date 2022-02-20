package SubmitPaymentForNFT

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.{ErgoNodeConfig, ErgoToolConfig}
import org.ergoplatform.appkit.impl.ErgoTreeContract
import sigmastate.eval.Colls
import sigmastate.interpreter.HintsBag
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
    val royaltyPercentage: Int = conf.getParameters.get("royaltyPercentage").toInt
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
      val totalToSpend: Long = paymentAmount + Parameters.MinFee
      val boxes: java.util.Optional[java.util.List[InputBox]] = wallet.getUnspentBoxes(totalToSpend)
      if (!boxes.isPresent)
        throw new ErgoClientException(s"Not enough coins in the wallet to pay $totalToSpend", null)

      // Create unsigned tx builder
      val txBuilder: UnsignedTransactionBuilder = ctx.newTxBuilder()


      // Create output box
      val royalty: ErgoValue[Int] = ErgoValue.of(royaltyPercentage)
      val expectedTokenName: ErgoValue[Coll[Byte]] = ErgoValue.of(tokenName.getBytes)
      val expectedPaymentAmount: ErgoValue[Long] = ErgoValue.of(paymentAmount)
      val receiverAddressBytes: Coll[Byte] = Colls.fromArray(nftReceiverAddress.getErgoAddress.script.bytes)
      val expectedReceiverAddress: ErgoValue[Coll[Byte]] = ErgoValue.of(receiverAddressBytes, ErgoType.byteType)

      // Create signed message to later verify "minting request"
      val message = "Minting request issued and signed by ErgoNames"
      val signedMessage = senderProver.signMessage(senderProver.getP2PKAddress, message.getBytes, HintsBag.empty)
      val signedMessageBytes = Colls.fromArray(signedMessage)
      val signatureIsValid1 = senderProver.verifySignature(senderProver.getP2PKAddress, message.getBytes, signedMessage)
      val signatureIsValid2 = senderProver.verifySignature(nftReceiverAddress.asP2PK(), message.getBytes, signedMessage)

      val newBox = txBuilder.outBoxBuilder
        .value(paymentAmount)
        .contract(new ErgoTreeContract(mintingContractAddress.getErgoAddress.script))
        .registers(royalty, expectedTokenName, expectedPaymentAmount, expectedReceiverAddress)
        .build()

      // Create unsigned transaction
      val tx: UnsignedTransaction = txBuilder
        .boxesToSpend(boxes.get)
        .outputs(newBox)
        .fee(Parameters.MinFee)
        .sendChangeTo(senderProver.getEip3Addresses.get(0).getErgoAddress)
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
