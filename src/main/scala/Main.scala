package ergonamesbackend

import ergonames.NodeConfiguration.NodeTools._
import ergonames.Minter.MintToken._

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.{ErgoToolConfig, ErgoNodeConfig}

object Main {

    val ergoToolConfig: ErgoToolConfig = createToolConfig("config.json")
    val ergoNodeConfig: ErgoNodeConfig = createNodeConfig(ergoToolConfig)
    val ergoClient: ErgoClient = createErgoClient(ergoNodeConfig)

    def main(args: Array[String]): Unit = {
        println("Ergo Names Backend")

        val tokenName: String = "testname.erg"
        val tokenDescription: String = "Ergo Names Domain Non-Fungible Token"

        val recieverWalletAddress: Address = Address.create("9fKr9vohYCteqq8hZwR3882xVy3T6F1dz49jcox5bYwoZbCV59z")

        val transactionJson: String = mint(ergoNodeConfig, ergoClient, tokenName, tokenDescription, recieverWalletAddress)
    }

}