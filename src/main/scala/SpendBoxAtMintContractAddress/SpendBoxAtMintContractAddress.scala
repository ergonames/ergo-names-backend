package SpendBoxAtMintContractAddress

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.{ErgoNodeConfig, ErgoToolConfig}
import org.ergoplatform.appkit.impl.ErgoTreeContract
import sigmastate.serialization.ErgoTreeSerializer
import special.collection.CollOverArray

import java.util.stream.Collectors
import scala.collection.JavaConverters._

object SpendBoxAtMintContractAddress {
  def spendBoxAtContractAddress(configFileName: String): String = {
    // Node configuration values
    val conf: ErgoToolConfig = ErgoToolConfig.load(configFileName)
    val nodeConf: ErgoNodeConfig = conf.getNode
    val explorerUrl: String = RestApiErgoClient.getDefaultExplorerUrl(NetworkType.TESTNET)

    // Fetch parameters from config
    val addressIndex: Int = conf.getParameters.get("addressIndex").toInt
    val mintingContractAddress: String = conf.getParameters.get("mintingContractAddress")
    val nftMintRequestBoxId: String = conf.getParameters.get("nftMintRequestBoxId")

    // Create ErgoClient instance (represents a connection to node)
    val ergoClient: ErgoClient = RestApiErgoClient.create(nodeConf, explorerUrl)

    // Execute transaction
    val txJson: String = ergoClient.execute((ctx: BlockchainContext) => {

      // Initialize prover (signs the transaction)
      val senderProver: ErgoProver = ctx.newProverBuilder
        .withMnemonic(
          SecretString.create(nodeConf.getWallet.getMnemonic),
          SecretString.create(nodeConf.getWallet.getPassword))
        .withEip3Secret(addressIndex)
        .build()

      // Get input (spending) boxes from minting contract address and own wallet.
      // We get boxes to spend from our wallet because we need to cover the tx fee and the minChangeValue when sending the NFT back to the user
      val spendingAddress: Address = Address.create(mintingContractAddress)
      val boxesFilteredByMintRequestBoxId: java.util.List[InputBox] = ctx.getUnspentBoxesFor(spendingAddress, 0, 20)
        .stream()
        .filter(_.getId == ErgoId.create(nftMintRequestBoxId))
        .collect(Collectors.toList())

      val wallet: ErgoWallet = ctx.getWallet
      val totalToSpendFromOwnWallet = Parameters.MinChangeValue
      val ownWalletSpendingBoxes: java.util.Optional[java.util.List[InputBox]] = wallet.getUnspentBoxes(totalToSpendFromOwnWallet)
      if (!ownWalletSpendingBoxes.isPresent)
        throw new ErgoClientException(s"Not enough coins in the wallet to pay $totalToSpendFromOwnWallet", null)

      // Amount to retrieve from nft minting request box - this is essentially collecting payment for the mint
//      val paymentAmount: Long = conf.getParameters.get("paymentAmount").toLong
//      val amountToSend: Long = paymentAmount - Parameters.MinFee

      // Create unsigned tx builder
      val txB: UnsignedTransactionBuilder = ctx.newTxBuilder

      // Extract proposed token values from mint request box registers
      val mintRequestBox = boxesFilteredByMintRequestBoxId.get(0);
      val R5_tokenNameBytes = mintRequestBox.getRegisters.get(1).getValue.asInstanceOf[CollOverArray[Byte]].toArray
      // TODO: Pull expected payment from R6, and create a new output box that goes to ErgoNames wallet/treasury
      val R6_paymentAmount = mintRequestBox.getRegisters.get(2).getValue.asInstanceOf[Long]
      val R7_proposedReceiverAddressBytes = mintRequestBox.getRegisters.get(3).getValue.asInstanceOf[CollOverArray[Byte]].toArray

      val proposedTokenName = new String(R5_tokenNameBytes)
      // user should have no say over description and decimal places. for description, we might even choose to set some additional metadata
      val tokenDescription: String = conf.getParameters.get("tokenDescription")
      val tokenDecimals: Int = conf.getParameters.get("tokenDecimals").toInt
      val deserializedProposedReceiverAddress = ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(R7_proposedReceiverAddressBytes)
      val proposedReceiverAddress = Address.fromErgoTree(deserializedProposedReceiverAddress, NetworkType.TESTNET)
      val proposedToken: ErgoToken = new ErgoToken(mintRequestBox.getId, 1)

      // Create output boxes
      val newBoxWithMintedNft: OutBox = txB.outBoxBuilder
        .mintToken(proposedToken, proposedTokenName, tokenDescription, tokenDecimals)
        .value(Parameters.MinChangeValue) // TODO: see what's the lowest possible amount of ERG you can get away with
        .contract(new ErgoTreeContract(proposedReceiverAddress.getErgoAddress.script)) // be sure to use address pulled from mint request registers, not from our config!
        .build()

      val newBoxWithCollectedPayment: OutBox = txB.outBoxBuilder
//        .value(mintRequestBox.getValue - Parameters.MinFee)
        .value(R6_paymentAmount - Parameters.MinFee)
        // TODO: Figure out why setting contract to EIP3 address pub key causes contract payment address validation step to fail
//        .contract(new ErgoTreeContract(senderProver.getEip3Addresses.get(0).getPublicKey))
        .contract(new ErgoTreeContract(senderProver.getP2PKAddress.script))
//        .contract(new ErgoTreeContract(Address.fromErgoTree(senderProver.getP2PKAddress.script, NetworkType.TESTNET).asP2PK().script))
        .build()

      // Create unsigned transaction
      val totalAmountToSpend = (mintRequestBox.getValue + Parameters.MinFee) + Parameters.MinChangeValue // need to account for min ERG box value to include when sending NFT back to user
      // TODO: Calling .get twice because one list is nullable. look into how to make this cleaner
      val boxesToSpend = List(boxesFilteredByMintRequestBoxId.get(0), ownWalletSpendingBoxes.get().get(0))
      val tx: UnsignedTransaction = txB
        .boxesToSpend(boxesToSpend.asJava)
        .outputs(newBoxWithMintedNft, newBoxWithCollectedPayment)
        .fee(Parameters.MinFee)
        .sendChangeTo(senderProver.getEip3Addresses.get(0).getErgoAddress)
        .build()

      // Sign transaction with prover
      val signed: SignedTransaction = senderProver.sign(tx)

      // TODO: Could have a try/catch here; if tx fails, process refund
      // Or, update the contract so that if conditions for mint are not met, it enforces a refund tx
      // Submit transaction to node
      val txId: String = ctx.sendTransaction(signed)

      // Return transaction as JSON string
      signed.toJson(true)
    })
    txJson
  }

  def main(args: Array[String]): Unit = {
    val txJson = spendBoxAtContractAddress("config.json")
    print(txJson)
  }
}
