package models

import org.ergoplatform.P2PKAddress
import org.ergoplatform.appkit.{InputBox, OutBox}

case class MintingTxArgs(inputs: List[InputBox], outputs: List[OutBox], fee: Long, receiverAddress: P2PKAddress)
