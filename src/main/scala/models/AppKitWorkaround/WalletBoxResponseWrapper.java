package models.AppKitWorkaround;

import com.google.gson.annotations.SerializedName;
import org.ergoplatform.restapi.client.ErgoTransactionOutput;

import java.util.ArrayList;
import java.util.List;

public class WalletBoxResponseWrapper {
    @SerializedName("boxes")
    private List<ErgoTransactionOutput> boxes = new ArrayList<>();

    public WalletBoxResponseWrapper boxes(List<ErgoTransactionOutput> boxes) {
        this.boxes = boxes;
        return this;
    }

    public List<ErgoTransactionOutput> getBoxes() {
        return this.boxes;
    }

    public void setBoxes(List<ErgoTransactionOutput> boxes) {
        this.boxes = boxes;
    }
}
