package ergonames.NodeConfiguration

import org.ergoplatform.appkit.config.ErgoNodeConfig
import org.ergoplatform.appkit.{Mnemonic, Address}
import org.ergoplatform.appkit.NetworkType
import org.ergoplatform.appkit.SecretString

object NodeWallet {

    def getWalletMnemonic(nodeConfig: ErgoNodeConfig): Mnemonic = {
        val mnemonicString: String = getWalletMnemonicString(nodeConfig)
        val mnemonicPassword: String = getWalletMnemonicPasswordString(nodeConfig)
        val mnemonic: Mnemonic = Mnemonic.create(getWalletMnemonicString(nodeConfig).toCharArray(), getWalletMnemonicPasswordString(nodeConfig).toCharArray())
        return mnemonic
    }

    private def getWalletMnemonicString(nodeConfig: ErgoNodeConfig): String = {
        val mnemonicString: String = nodeConfig.getWallet().getMnemonic()
        return mnemonicString
    }

    private def getWalletMnemonicPasswordString(nodeConfig: ErgoNodeConfig): String = {
        val mnemonicPassword: String = nodeConfig.getWallet().getMnemonicPassword()
        return mnemonicPassword
    }

    def getPublicErgoAddress(nodeConfig: ErgoNodeConfig): Address = {
        val address: Address = Address.createEip3Address(0, NetworkType.TESTNET, SecretString.create(getWalletMnemonicString(nodeConfig)), SecretString.create(""))
        return address
    }

}