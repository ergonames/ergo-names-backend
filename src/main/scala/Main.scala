package ergonamesbackend

import ergonames.NodeConfiguration.NodeTools._
import ergonames.Minter.MintToken._

import org.ergoplatform.appkit._
import org.ergoplatform.appkit.config.{ErgoToolConfig, ErgoNodeConfig}

object Main {

    val ergoToolConfig: ErgoToolConfig = createToolConfig("testnet_config.json")
    val ergoNodeConfig: ErgoNodeConfig = createNodeConfig(ergoToolConfig)
    val ergoClient: ErgoClient = createErgoClient(ergoNodeConfig)

    def main(args: Array[String]): Unit = {
        println("Ergo Names Backend\n")

        val tokenName: String = "testname.erg"
        val tokenDescription: String = "Ergo Names Domain Non-Fungible Token"

        val recieverWalletAddress: Address = Address.create("3WwKzFjZGrtKAV7qSCoJsZK9iJhLLrUa3uwd4yw52bVtDVv6j5TL")

        val transactionJson: String = mint(ergoNodeConfig, ergoClient, tokenName, tokenDescription, recieverWalletAddress)

        println(transactionJson)
    }

}