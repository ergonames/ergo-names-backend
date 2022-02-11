package ergonames.NodeConfiguration

import org.ergoplatform.appkit.config.ErgoNodeConfig
import org.ergoplatform.appkit.Mnemonic

object NodeWallet {

    def getWalletMnemonic(nodeConfig: ErgoNodeConfig): Mnemonic = {
        val mnemonicString: String = getWalletMnemonicString(nodeConfig)
        val mnemonicPassword: String = getWalletMnemonicPasswordString(nodeConfig)
        val mnemonic: Mnemonic = Mnemonic.create(mnemonicString.toCharArray(), mnemonicPassword.toCharArray())
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
}