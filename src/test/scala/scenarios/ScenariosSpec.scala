package scenarios
import scenarios.DeployMintingContract
import scenarios.ProcessMintingRequest
import scenarios.SubmitMintingRequest
//import ergonames.SpendBoxAtMintContractAddress.SpendBoxAtMintContractAddress
import io.github.dav009.ergopuppet.Simulator._
import io.github.dav009.ergopuppet.model.{ TokenAmount, TokenInfo }
import org.ergoplatform.appkit._
import org.ergoplatform.P2PKAddress
import org.scalatest.{ PropSpec, Matchers }
import org.ergoplatform.ErgoAddressEncoder
import org.scalatest._
import org.scalatest.{ Matchers, WordSpecLike }


class WorkflowSpec extends WordSpecLike with Matchers {

  "simple workflow case" in {

    val (blockchainSim, ergoClient) =
      newBlockChainSimulationScenario("Workflow test")

    ergoClient.execute((ctx: BlockchainContext) => {

      val ergoNames = blockchainSim.newParty("ergoNames", ctx)
      val customer = blockchainSim.newParty("customer", ctx)
      val initialAmount = 100000000000000L
      ergoNames.generateUnspentBoxes(toSpend = initialAmount)
      customer.generateUnspentBoxes(toSpend = initialAmount)

      // creates a minting contract address on which requests can be added
      val amountToSpend = Parameters.MinChangeValue + Parameters.MinFee
      val boxes = ergoNames.wallet.getUnspentBoxes(amountToSpend).get
      val (serviceTx, mintContract) = DeployMintingContract.createTx(ctx, boxes, ergoNames.wallet.getAddress)
      val signedserviceTx = ergoNames.wallet.sign(serviceTx)
      ctx.sendTransaction(signedserviceTx)
      val mintingContractAddress = Address.fromErgoTree(mintContract.getErgoTree(), NetworkType.TESTNET)

      // submit minting request
      val paymentAmount = 2500000
      val customerBoxes = customer.wallet.getUnspentBoxes(paymentAmount + Parameters.MinFee).get
      val royaltyPercentage = 20
      val tokenName = "v2_contract_issued_nft_test.erg"
      val nftReceiverAddress = customer.wallet.getAddress
      val senderAddress = customer.wallet.getAddress

      val submitTx = SubmitMintingRequest.createTx(ctx, customerBoxes,
        mintingContractAddress,
        royaltyPercentage,
        tokenName,
        paymentAmount,
        nftReceiverAddress,
       senderAddress
      )
      val signedsubmitTx = customer.wallet.sign(submitTx)
      ctx.sendTransaction(signedsubmitTx)

      // processing a mint request
      val tokenDesc = "Early stage testing of token minting with contracts"
      val boxesToSpendForMint = ergoNames.wallet.getUnspentBoxes(Parameters.MinFee+ Parameters.MinChangeValue+100000).get
      
      val a =  ctx.getUnspentBoxesFor(mintingContractAddress, 0, Int.MaxValue)
      val mintRequestBox = a.get(0)
      
      val mintTx =ProcessMintingRequest.createTx(ctx, boxesToSpendForMint, ergoNames.wallet.getAddress, mintRequestBox, tokenDesc, NetworkType.MAINNET)
      val signedmintTx = ergoNames.wallet.sign(mintTx)
      ctx.sendTransaction(signedmintTx)


    })
  }
}
