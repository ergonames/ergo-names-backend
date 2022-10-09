package services.AppKitWorkaround;

import models.AppKitWorkaround.NewBoxesRequestHolder;
import models.AppKitWorkaround.WalletBoxResponseWrapper;
import retrofit2.Call;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface NewWalletApi {
    @Headers({
            "Content-Type:application/json"
    })
    @POST("wallet/boxes/collect")
    Call<WalletBoxResponseWrapper> walletBoxesCollect(
            @retrofit2.http.Body NewBoxesRequestHolder body
    );
}
