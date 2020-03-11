package com.example.mkio.importsource.okhttp3example;

import java.lang.reflect.Type;

import retrofit2.Call;
import retrofit2.CallAdapter;

public class AsyncAdapter implements CallAdapter<String,Object> {
    @Override
    public Type responseType() {
        return null;
    }

    @Override
    public Object adapt(Call<String> call) {
        return null;
    }
}
