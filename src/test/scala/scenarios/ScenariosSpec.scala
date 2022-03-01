package scenarios

import utils.ErgoNamesUtils
import scala.collection.JavaConverters._

import scenarios.DeployMintingContract
import scenarios.ProcessMintingRequest
import scenarios.SubmitMintingRequest


import org.ergoplatform.appkit._
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.P2PKAddress
import org.ergoplatform.ErgoAddressEncoder

import org.scalatest._
import org.scalatest.Assertions._
import org.scalatest.{Matchers, WordSpecLike}

import io.github.dav009.ergopuppet.Simulator._
import io.github.dav009.ergopuppet.model.{TokenAmount, TokenInfo}

import sigmastate.lang.exceptions.InterpreterException

class SimpleScenarioSpec extends WordSpecLike with Matchers {

  val (blockchainSim, ergoClient) =
    newBlockChainSimulationScenario("Workflow test")

  ergoClient.execute((ctx: BlockchainContext) => {
    val ergoNames = blockchainSim.newParty("ergoNames", ctx)
    val customer = blockchainSim.newParty("customer", ctx)
    val other = blockchainSim.newParty("other", ctx)
    val initialAmount = 100000000000000L
    ergoNames.generateUnspentBoxes(toSpend = initialAmount)
    customer.generateUnspentBoxes(toSpend = initialAmount)
    other.generateUnspentBoxes(toSpend = initialAmount)

    // creates a minting contract address on which requests can be added
    val amountToSpend = Parameters.MinChangeValue + Parameters.MinFee
    val boxes = ergoNames.wallet.getUnspentBoxes(amountToSpend).get
    val (serviceTx, mintContract) =
      DeployMintingContract.createTx(ctx, boxes, ergoNames.wallet.getAddress)
    val signedserviceTx = ergoNames.wallet.sign(serviceTx)
    ctx.sendTransaction(signedserviceTx)
    val mintingContractAddress =
      Address.fromErgoTree(mintContract.getErgoTree(), NetworkType.TESTNET)

    // submit minting request
    val paymentAmount = 2500000
    val customerBoxes =
      customer.wallet.getUnspentBoxes(paymentAmount + Parameters.MinFee).get
    val royaltyPercentage = 20
    val tokenName = "v2_contract_issued_nft_test.erg"
    val nftReceiverAddress = customer.wallet.getAddress
    val senderAddress = customer.wallet.getAddress
          // check no boxes left under contract
    assert(ctx.getUnspentBoxesFor(mintingContractAddress, 0, Int.MaxValue).size() == 1)

    val submitTx = SubmitMintingRequest.createTx(
      ctx,
      customerBoxes,
      mintingContractAddress,
      royaltyPercentage,
      tokenName,
      paymentAmount,
      nftReceiverAddress,
      senderAddress
    )
    val signedsubmitTx = customer.wallet.sign(submitTx)
    ctx.sendTransaction(signedsubmitTx)
    assert(ctx.getUnspentBoxesFor(mintingContractAddress, 0, Int.MaxValue).size() == 2)

    // preparing data for a mint request
    val tokenDesc = "Early stage testing of token minting with contracts"
    val contractBoxes =
      ctx.getUnspentBoxesFor(mintingContractAddress, 0, Int.MaxValue)
    val mintRequestBox = contractBoxes.get(0)

    val ergoNamesUnspentBoxes = ergoNames.wallet
      .getUnspentBoxes(Parameters.MinFee + Parameters.MinChangeValue + 100000)
      .get

    val (_, token, _, _, _, _, contract) = ErgoNamesUtils.issuanceBoxArgs(
      ctx,
      NetworkType.MAINNET,
      value = Parameters.MinChangeValue,
      mintRequestBox,
      tokenDescription = tokenDesc
    )

    val (goodTx, goodTxArgs) =
      ProcessMintingRequest.createTx(
        ctx,
        ergoNamesUnspentBoxes,
        ergoNames.wallet.getAddress,
        mintRequestBox,
        tokenDesc,
        NetworkType.MAINNET
      )

    "entity aside from ergonames should not be able to mint" in {
      val otherBoxes = other.wallet
        .getUnspentBoxes(Parameters.MinFee + Parameters.MinChangeValue + 100000)
        .get
      val (mintTxshoudFail, _) = ProcessMintingRequest.createTx(
        ctx,
        otherBoxes,
        other.wallet.getAddress,
        mintRequestBox,
        tokenDesc,
        NetworkType.MAINNET
      )
      assertThrows[InterpreterException] {
        val signedmintTxshouldFail = other.wallet.sign(mintTxshoudFail)
        ctx.sendTransaction(signedmintTxshouldFail)
      }
    }

    "minting a domain different from the one submitted errors" in {
      val badIssuanceBox = ctx.newTxBuilder.outBoxBuilder
        .mintToken(token, "a bad name", tokenDesc, 1)
        .value(Parameters.MinChangeValue)
        .contract(contract)
        .build()

      val badNameTx = ctx.newTxBuilder
        .boxesToSpend(goodTxArgs.inputs.asJava)
        .outputs(badIssuanceBox, goodTxArgs.outputs(1))
        .fee(goodTxArgs.fee)
        .sendChangeTo(goodTxArgs.receiverAddress)
        .build()

      assertThrows[InterpreterException] {
        val signedBadNameTx = ergoNames.wallet.sign(badNameTx)
        ctx.sendTransaction(signedBadNameTx)
      }
    }

    "errors if expected payment amount was not received" in {
      val incorrectPaymentToErgoNames = ctx.newTxBuilder.outBoxBuilder
        .value(paymentAmount - 1)
        .contract(
          new ErgoTreeContract(
            ergoNames.wallet.getAddress.getErgoAddress().script
          )
        )
        .build()

      val badPaymnetTx = ctx.newTxBuilder
        .boxesToSpend(goodTxArgs.inputs.asJava)
        .outputs(goodTxArgs.outputs(0), incorrectPaymentToErgoNames)
        .fee(goodTxArgs.fee)
        .sendChangeTo(goodTxArgs.receiverAddress)
        .build()

      assertThrows[InterpreterException] {
        val signedPaymentTx = ergoNames.wallet.sign(badPaymnetTx)
        ctx.sendTransaction(signedPaymentTx)
      }
    }

    "errors if trying to claim the payment box on behalf of ergonames" in {
      val incorrectDestination = ctx.newTxBuilder.outBoxBuilder
        .value(paymentAmount - 1)
        .contract(
          new ErgoTreeContract(other.wallet.getAddress.getErgoAddress().script)
        )
        .build()

      val badDestinationTx = ctx.newTxBuilder
        .boxesToSpend(goodTxArgs.inputs.asJava)
        .outputs(goodTxArgs.outputs(0), incorrectDestination)
        .fee(goodTxArgs.fee)
        .sendChangeTo(goodTxArgs.receiverAddress)
        .build()

      assertThrows[InterpreterException] {
        val signedBadDestinationTx = ergoNames.wallet.sign(badDestinationTx)
        ctx.sendTransaction(signedBadDestinationTx)
      }
    }

    " error if nft is to be sent to other than the requqester" in {
      val badNftOwnerBox = ctx.newTxBuilder.outBoxBuilder
        .mintToken(token, tokenName, tokenDesc, 1)
        .value(Parameters.MinChangeValue)
        .contract(
          new ErgoTreeContract(other.wallet.getAddress.getErgoAddress().script)
        )
        .build()

      val badNftOwnerTx = ctx.newTxBuilder
        .boxesToSpend(goodTxArgs.inputs.asJava)
        .outputs(badNftOwnerBox, goodTxArgs.outputs(1))
        .fee(goodTxArgs.fee)
        .sendChangeTo(goodTxArgs.receiverAddress)
        .build()

      assertThrows[InterpreterException] {
        val signedBadNFtOwnerTx = ergoNames.wallet.sign(badNftOwnerTx)
        ctx.sendTransaction(signedBadNFtOwnerTx)
      }
    }

    "when all constraints are fullfilled nft should be minted" in {
      val signedmintTx = ergoNames.wallet.sign(goodTx)
      ctx.sendTransaction(signedmintTx)

      // assert nft owners
      val customerTokens = blockchainSim.getUnspentTokensFor(customer.wallet.getAddress)
      val  expectedCustomerTokens = List(new TokenAmount(new TokenInfo(mintRequestBox.getId(), tokenName), 1))
      assert(customerTokens == expectedCustomerTokens)

      // assert ergoname service assets
      // one tx fee + the amount payed by customer + initial amount
      val costsOfDeployContract = Parameters.MinChangeValue + Parameters.MinFee
      val gainsOfMintingNFT = paymentAmount
      val costsOfMintingNFT =  Parameters.MinFee + Parameters.MinChangeValue
      val expectedErgonamesCoins = initialAmount - costsOfDeployContract - costsOfMintingNFT + gainsOfMintingNFT
      val ergoNamesCoins = blockchainSim.getUnspentCoinsFor(ergoNames.wallet.getAddress)
      assert(ergoNamesCoins == expectedErgonamesCoins)

      // because mint request was susccessful there should be only 1 box left under the mintingContractAddress
      assert(ctx.getUnspentBoxesFor(mintingContractAddress, 0, Int.MaxValue).size() == 1)


    }

  })
}
