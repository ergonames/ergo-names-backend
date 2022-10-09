package utils

import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit._

import scala.collection.JavaConverters._

object ErgoNamesUtilsV2 {
  def buildUnsignedTx(ctx: BlockchainContext, inputs: List[InputBox], outputs: List[OutBox], fee: Long, changeAddress: ErgoAddress): UnsignedTransaction = {
    ctx.newTxBuilder()
      .boxesToSpend(inputs.asJava)
      .outputs(outputs:_*)
      .fee(fee)
      .sendChangeTo(changeAddress)
      .build()
  }
}
