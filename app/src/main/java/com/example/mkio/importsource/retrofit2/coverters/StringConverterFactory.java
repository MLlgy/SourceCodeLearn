package com.example.mkio.importsource.retrofit2.coverters;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.annotation.Nullable;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

/**
 * Creater: liguoying
 * Time: 2018/7/5 0005 17:50
 */
public class StringConverterFactory extends Converter.Factory {
    public static final StringConverterFactory STRING_CONVERTER_FACTORY = new StringConverterFactory();

    public static StringConverterFactory create() {
        return STRING_CONVERTER_FACTORY;
    }

    @Nullable
    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        if (type == String.class) {
            return StringConverter.STRINGCONVERTER;
        }
        return null;
    }

    @Nullable
    @Override
    public Converter<?, String> stringConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        return super.stringConverter(type, annotations, retrofit);
    }
}
