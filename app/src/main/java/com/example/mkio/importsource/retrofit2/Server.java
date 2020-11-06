package com.example.mkio.importsource.retrofit2;

import com.example.mkio.importsource.retrofit2.coverters.CustomCall;

import java.util.List;

import io.reactivex.Single;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Creater: liguoying
 * Time: 2018/7/5 0005 14:42
 */
public interface Server {

    @GET("/sherpa-web-api/newcoupon")
    Call<CouponResp> checkCoupon(@Query("customId") String customId, @Query("couponNumber") String code, @Query("totolValue") int totalValue);
    @GET("/sherpa-web-api/newcoupon")
    CustomCall<CouponResp> loadThree(@Query("customId") String customId, @Query("couponNumber") String code, @Query("totolValue") int totalValue);

    @Multipart
    @PUT("user/photo")
    Call<CouponResp> updateUser(@Part("photo") RequestBody photo, @Part("description") RequestBody description);



    @Multipart
    @POST("upload")
    Call<ResponseBody> uploadOneFile(@Part MultipartBody.Part body);

    @GET("users/{username}/repos")
    Single<List<Repo>> getRepos(@Path("username") String username);
}
