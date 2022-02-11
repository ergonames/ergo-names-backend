package ergonames.Minter

import ergonames.NodeConfiguration.NodeWallet.getWalletMnemonic

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.ErgoNodeConfig
import org.ergoplatform.ErgoAddress

object MintToken {

  def mint(nodeConfig: ErgoNodeConfig, client: ErgoClient, tokenName: String, tokenDescription: String, recieverWalletAddress: Address): String = {
    val transactionJson: String = client.execute((ctx: BlockchainContext) => {
      val amountToSpend: Long = 0L
      val totalToSpend: Long = amountToSpend + Parameters.MinFee

      val mnemonic: Mnemonic = getWalletMnemonic(nodeConfig)

      val senderProver: ErgoProver = BoxOperations.createProver(ctx, mnemonic)
      val senderAddress: Address = senderProver.getAddress()

      val unspentBoxes = ctx.getUnspentBoxesFor(senderAddress, 0, 20)
      val boxesToSpend = BoxOperations.selectTop(unspentBoxes, totalToSpend)

      val domainToken = new ErgoToken(boxesToSpend.get(0).getId(), 1)

      val transactionBuilder = ctx.newTxBuilder()

      val outBox = transactionBuilder.outBoxBuilder()
        .value(amountToSpend)
        .mintToken(domainToken, tokenName, tokenDescription, 0)
        .contract(ctx.compileContract(
          ConstantsBuilder.create()
            .item("recieverPublicKey", recieverWalletAddress.getPublicKey())
            .build(),
            "{ recieverPublicKey }")
        )
        .build()

      val transaction: UnsignedTransaction = transactionBuilder
        .boxesToSpend(boxesToSpend)
        .outputs(outBox)
        .fee(Parameters.MinFee)
        .sendChangeTo(recieverWalletAddress.asP2PK())
        .build()

      val signedTransaction: SignedTransaction = senderProver.sign(transaction)

      val transactionId: String = ctx.sendTransaction(signedTransaction)

      signedTransaction.toJson(true)
    })
    transactionJson
  }


}