package com.example.mkio.importsource.retrofit2;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.example.mkio.importsource.R;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import xutils3.common.util.LogUtil;

public class RetrofitActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_retrofit);
        findViewById(R.id.tv_check_net).setOnClickListener(view -> {
            RetrofitFactory.getInstance().checkCoupon("2018040202512043", "133", 315).enqueue(new Callback<CouponResp>() {
                @Override
                public void onResponse(Call<CouponResp> call, Response<CouponResp> response) {
                    CouponResp mCouponResp = response.body();
                    LogUtil.d(mCouponResp.toString());

                }

                @Override
                public void onFailure(Call<CouponResp> call, Throwable t) {

                }
            });
        });
    }
}
