package com.example.mkio.importsource.retrofit2.coverters;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import javax.annotation.Nullable;

import retrofit2.CallAdapter;
import retrofit2.Retrofit;

/**
 * Creater: liguoying
 * Time: 2018/7/5 0005 18:39
 */
public class CustomCallAdapterFactory extends CallAdapter.Factory {
    @Nullable
    @Override
    public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {

        Class<?> rawTypr = getRawType(returnType);
        if (rawTypr == CustomCall.class && returnType instanceof ParameterizedType) {
            Type callReturnType = getParameterUpperBound(0, (ParameterizedType) returnType);
            return new CustomCallAdapter(callReturnType);

        }
        return null;
    }

    public static CustomCallAdapterFactory create(){
        return new CustomCallAdapterFactory();
    }
}


