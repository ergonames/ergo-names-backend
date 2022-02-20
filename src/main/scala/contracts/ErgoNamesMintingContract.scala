package contracts

/**
 * ====== Contract Info ======
 * Contract for minting ErgoNames NFTs.
 * Holds "minting requests", which are later processed by an off chain minting service.
 * Successful processing of a minting request issues an NFT back to a user, and collects payment.
 * If conditions are not met and a minting request cannot be processed, a refund is expected to be issued back to the user.

 * A "minting request" can be thought of as a receipt for a placed order.
 * It contains information like: what was ordered, who ordered it, and how much they paid for it (potentially also who took or processed the order).
 * In practice, a minting request is a box with certain expected values in its registers:
 * R4: Int - Royalty percentage
 * R5: Coll[Byte] - Name of NFT to mint
 * R6: Long - Expected payment amount
 * R7: Coll[Byte] - Address of the receiver; should receive an NFT or a refund

 * Minting requests are built by a frontend component on behalf of the user, and sent to the contract address.
 * The transaction building process is responsible for defining an output box with the expected values in the respective registers.
 * This way, the details of having to build a request that complies with the contract's expectations are abstracted from the user.

 * Anyone can create a box and send it to any address.
 * If a bad actor figures out the contract's requirements, creates compliant minting request box, and sends it to the contract,
 * we avoid spending it by checking for a signed message in Rx (TBD) that could only have been signed with the ErgoNames PK.
 * This is how we avoid processing minting requests that were not officially created by us on behalf of a user.
 * DISCLAIMER: This security check has yet to be implemented into the contract; it is critical to do so before deploying and using it in production.

 * Moreover, if a box is sent to the contract address that does not comply with the contract's requirements AND, at the very least,
 * DOES NOT contain the sender address in the expected register, the funds might be lost forever.
 *
 * ErgoNames implements royalty reflections from secondary market sales by following the standard set by EIP-0024.
 * EIP-0024 specifies that if a royalty percentage is set in R4 of the issuer box, then royalties will be sent to the
 * address that provided that box as an input to the tx that issued the asset. In this case, that would be the contract address.
 * Because we want reflections accumulated at the contract address to be able to be collected (essentially spent),
 * we add this final potential check in case the token is not being minted, or a refund is not being issued.
 * If a token is not being minted, or a refund is not being issued, then we make the assumption that the box we're trying to spend is a reflection from royalties.
 * This is deliberately set as a final check so ErgoNames can ONLY collect the funds in a box if all previous checks have failed.
 * This has a unintended potential benefit:
 * If, for whatever reason, funds were to get locked in the contract (meaning the mint request was invalid AND a refund couldn't be issued),
 * ErgoNames would have the ability to unlock them by sending them to an address (wallet or contract) for which they have spend permissions.
 *
 * With all that said: DO NOT ATTEMPT TO SEND UNSOLICITED MINTING REQUESTS TO THE CONTRACT OR YOU MIGHT LOSE YOUR FUNDS FOREVER.
 * In such a case, ErgoNames is not responsible for any lost funds.

 * * ====== Contract Constants ======
 * ergoNamesPk - Public key of the party expected to sign for the minting tx. Provided at contract compile time by ErgoNames.
 */
object ErgoNamesMintingContract {
  def getScript: String = {
    val script: String = s"""
      {
        // 2% per EIP24 standard format; could potentially be a data input
        val royaltyPercentage = 20
        val txFee = 1000000

        // Verify all the requirements for minting the NFT are met
        val mintToken = {
          // Verify token is an NFT
          val proposedTokenHasSameIdAsFirstTxInput = OUTPUTS(0).tokens(0)._1 == SELF.id
          val proposedTokenIsNonFungible = OUTPUTS(0).tokens(0)._2 == 1
          val proposedTokenSpecsOk = proposedTokenHasSameIdAsFirstTxInput && proposedTokenIsNonFungible

          // Verify the royalty percentage is correct
          val specifiedRoyalty = SELF.R4[Int].get
          val expectedRoyalty = 20
          val royaltyOk = specifiedRoyalty == expectedRoyalty

          // Verify name of token being issued is what was requested
          val expectedTokenName = SELF.R5[Coll[Byte]].get
          val proposedTokenName = OUTPUTS(0).R4[Coll[Byte]].get
          val tokenNameOk = expectedTokenName == proposedTokenName

          // Verify expected payment amount was received
          val expectedPaymentAmount = SELF.R6[Long].get
          val sentPaymentAmount = SELF.value
          val receivedPaymentAmountOk = expectedPaymentAmount == sentPaymentAmount

          // Verify that NFT is being sent to the sender of the payment
          val expectedReceiverAddress = SELF.R7[Coll[Byte]].get
          val proposedReceiverAddress = OUTPUTS(0).propositionBytes
          val receiverAddressOk = expectedReceiverAddress == proposedReceiverAddress

          // Verify correct payment amount is being collected
          val amountToCollect = sentPaymentAmount - 1000000
          val amountBeingCollected = OUTPUTS(1).value
          val collectedAmountOk = amountToCollect == amountBeingCollected

          // Verify payment is being sent to the right address
          val collectedByErgoNames = OUTPUTS(1).propositionBytes == ergoNamesPk.propBytes
          val paymentDetailsOk = collectedAmountOk && collectedByErgoNames

          proposedTokenSpecsOk &&
          royaltyOk &&
          tokenNameOk &&
          receivedPaymentAmountOk &&
          receiverAddressOk &&
          paymentDetailsOk
        }

        // In case of a refund, check that funds are going back to the sender
        val issueRefund = {
            val senderAddress = SELF.R7[Coll[Byte]].get
            val fundsAreGoingBackToSender = senderAddress == OUTPUTS(0).propositionBytes

            val amountToReturn = SELF.value - 1000000
            val correctAmountBeingReturned = amountToReturn == OUTPUTS(0).value

            fundsAreGoingBackToSender && correctAmountBeingReturned
        }


        val collectRoyalty = {
          // checking for empty registers is a way to prevent ErgoNames from just collecting funds from a valid minting request box without actually issuing an NFT.
          val isNotMintingRequest = !(SELF.R4[Int].isDefined) && !(SELF.R5[Coll[Byte]].isDefined) && !(SELF.R6[Long].isDefined) && !(SELF.R7[Coll[Byte]].isDefined)
          val beingCollectedByErgoNames = OUTPUTS(0).propositionBytes == ergoNamesPk.propBytes // could actually make royalties go to royalties contract

          isNotMintingRequest && beingCollectedByErgoNames
        }

        sigmaProp((mintToken || issueRefund || collectRoyalty) && ergoNamesPk)
      }
      """.stripMargin

    script
  }
}
