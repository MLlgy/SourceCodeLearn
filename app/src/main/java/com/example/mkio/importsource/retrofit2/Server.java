package com.example.mkio.importsource.retrofit2;

import com.example.mkio.importsource.retrofit2.coverters.CustomCall;

import retrofit2.Call;
import retrofit2.http.GET;
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
}
