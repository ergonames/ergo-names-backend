package models.AppKitWorkaround;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.HashMap;

public class NewBoxesRequestHolder {
    @SerializedName("targetAssets")
    private java.util.HashMap<String, Long> targetAssets = new HashMap<>();
//    private ArrayList<ArrayList> targetAssets = new ArrayList<>();

    @SerializedName("targetBalance")
    private Long targetBalance = null;

    public NewBoxesRequestHolder targetAssets(java.util.HashMap<String, Long> targetAssets) {
//    public NewBoxesRequestHolder targetAssets(ArrayList<ArrayList> targetAssets) {
        this.targetAssets = targetAssets;
        return this;
    }

    public NewBoxesRequestHolder targetBalance(Long targetBalance) {
        this.targetBalance = targetBalance;
        return this;
    }

//    @Schema(required = true, description = "Target assets")
    public java.util.HashMap<String, Long> getTargetAssets() {
//    public ArrayList<ArrayList> getTargetAssets() {
        return targetAssets;
    }

    public void setTargetAssets(java.util.HashMap<String, Long> targetAssets) {
//    public void setTargetAssets(ArrayList<ArrayList> targetAssets) {
        this.targetAssets = targetAssets;
    }

    @Schema(required = true, description = "Target balance")
    public Long getTargetBalance() {
        return targetBalance;
    }

    public void setTargetBalance(Long targetBalance) {
        this.targetBalance = targetBalance;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class NewBoxesRequestHolder {\n");

        sb.append("    targetAssets: ").append(toIndentedString(targetAssets)).append("\n");
        sb.append("    targetBalance: ").append(toIndentedString(targetBalance)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    private String toIndentedString(java.lang.Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}
