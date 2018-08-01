package com.example.mkio.importsource.xutils3sample.http;

import java.util.ArrayList;
import java.util.List;

import xutils3.common.Callback;
import xutils3.http.RequestParams;
import xutils3.http.annotation.HttpRequest;
import xutils3.x;

/**
 * Created by wyouflf on 16/1/23.
 */
@HttpRequest(
        host = JsonDemoParamsBuilder.SEEVER_A,
        path = "query/test",
        builder = JsonDemoParamsBuilder.class
)
public class JsonDemoParams extends RequestParams {

    public String paramStr;

    public int paramInt;

    public List<String> paramList;


    // 发送请求的示例
    // 参数被JsonDemoParamsBuilder重新加工成json的形式发送.
    // 示例项目的混淆配置会使这个类的字段不被混淆, 字段名作为参数名.
    public static Callback.Cancelable send(Callback.CommonCallback<BaiduResponse> callback) {
        JsonDemoParams params = new JsonDemoParams();
        params.paramStr = "test";
        params.paramInt = 10;
        params.paramList = new ArrayList<String>();
        params.paramList.add("test");
        return x.http().post(params, callback);
    }
}
