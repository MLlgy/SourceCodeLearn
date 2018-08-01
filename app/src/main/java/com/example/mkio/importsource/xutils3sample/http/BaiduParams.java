package com.example.mkio.importsource.xutils3sample.http;

import java.util.ArrayList;
import java.util.List;

import xutils3.http.RequestParams;
import xutils3.http.annotation.HttpRequest;
import xutils3.http.app.DefaultParamsBuilder;

/**
 * Created by wyouflf on 15/11/4.
 */
@HttpRequest(
        host = "https://www.baidu.com",
        path = "s",
        builder = DefaultParamsBuilder.class/*可选参数, 控制参数构建过程, 定义参数签名, SSL证书等*/)
public class BaiduParams extends RequestParams {
    public String wd;

    // 数组参数 aa=1&aa=2&aa=4
    public int[] aa = new int[]{1, 2, 4, 5};
    public List<String> bb = new ArrayList<String>();

    public BaiduParams() {
        bb.add("a");
        bb.add("c");
        // this.setMultipart(true); // 使用multipart表单
        // this.setAsJsonContent(true); // 请求body将参数转换为json形式发送
    }

    //public long timestamp = System.currentTimeMillis();
    //public File uploadFile; // 上传文件
    //public List<File> files; // 上传文件数组
}
