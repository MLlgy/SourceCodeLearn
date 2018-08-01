package com.example.mkio.importsource.retrofit2.coverters;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Converter;
import xutils3.common.util.LogUtil;

/**
 * Creater: liguoying
 * Time: 2018/7/5 0005 17:47
 */
public class StringConverter implements Converter<ResponseBody, String> {

    public static final StringConverter STRINGCONVERTER = new StringConverter();

    @Override
    public String convert(ResponseBody value) throws IOException {
        LogUtil.d(value.string());
        return value.string();
    }
}
