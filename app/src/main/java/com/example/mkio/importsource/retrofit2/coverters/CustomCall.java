package com.example.mkio.importsource.retrofit2.coverters;

import java.io.IOException;

import retrofit2.Call;

/**
 * Creater: liguoying
 * Time: 2018/7/5 0005 18:25
 */
public class CustomCall<R> {
    public final Call<R> mRCall;


    public CustomCall(Call<R> mRCall) {
        this.mRCall = mRCall;
    }

    public R get() throws IOException {
        // TODO: 2020/10/29 CallAdapter 返回自定义 Call 的执行结果
        return mRCall.execute().body();
    }
}
