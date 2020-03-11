package com.example.mkio.importsource.retrofit2.coverters;


import java.lang.reflect.Type;

import retrofit2.Call;
import retrofit2.CallAdapter;

/**
 * Creater: liguoying
 * Time: 2018/7/5 0005 18:28
 */
public class CustomCallAdapter<R> implements CallAdapter<R, CustomCall<?>> {

    private final Type responseType;

    public CustomCallAdapter(Type mResponseType) {
        responseType = mResponseType;
    }

    @Override
    public Type responseType() {
        return responseType;
    }

    @Override
    public CustomCall<?> adapt(Call<R> call) {
        return new CustomCall<>(call);
    }
}
