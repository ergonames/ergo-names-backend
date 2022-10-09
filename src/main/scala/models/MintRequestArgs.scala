package models

import org.ergoplatform.appkit.Address

case class MintRequestArgs(tokenName: String, expectedPaymentAmount: Long, receiverAddress: Address)
