package com.example.mkio.importsource.retrofit2;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.example.mkio.importsource.InitApp;
import com.example.mkio.importsource.NetWorkUtil;
import com.example.mkio.importsource.retrofit2.coverters.CustomCall;
import com.example.mkio.importsource.retrofit2.coverters.CustomCallAdapterFactory;
import com.example.mkio.importsource.retrofit2.coverters.StringConverterFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import xutils3.common.util.LogUtil;

/**
 * Created by Meiji on 2017/4/22.
 */

public class RetrofitFactory {
    private static final Object Object = new Object();
    /**
     * 缓存机制
     * 在响应请求之后在 data/data/<包名>/cache 下建立一个response 文件夹，保持缓存数据。
     * 这样我们就可以在请求的时候，如果判断到没有网络，自动读取缓存的数据。
     * 同样这也可以实现，在我们没有网络的情况下，重新打开App可以浏览的之前显示过的内容。
     * 也就是：判断网络，有网络，则从网络获取，并保存到缓存中，无网络，则从缓存中获取。
     * https://werb.github.io/2016/07/29/%E4%BD%BF%E7%94%A8Retrofit2+OkHttp3%E5%AE%9E%E7%8E%B0%E7%BC%93%E5%AD%98%E5%A4%84%E7%90%86/
     */
    private static final Interceptor cacheControlInterceptor = new Interceptor() {

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            if (!NetWorkUtil.isNetworkConnected(InitApp.AppContext)) {
                request = request.newBuilder().cacheControl(CacheControl.FORCE_CACHE).build();
            }

            Response originalResponse = chain.proceed(request);
            if (NetWorkUtil.isNetworkConnected(InitApp.AppContext)) {
                // 有网络时 设置缓存为默认值
                String cacheControl = request.cacheControl().toString();
                return originalResponse.newBuilder()
                        .header("Cache-Control", cacheControl)
                        .removeHeader("Pragma") // 清除头信息，因为服务器如果不支持，会返回一些干扰信息，不清除下面无法生效
                        .build();
            } else {
                // 无网络时 设置超时为1周
                int maxStale = 60 * 60 * 24 * 7;
                return originalResponse.newBuilder()
                        .header("Cache-Control", "public, only-if-cached, max-stale=" + maxStale)
                        .removeHeader("Pragma")
                        .build();
            }
        }
    };
    private volatile static Retrofit retrofit;
    public Server mServer;

    public RetrofitFactory() {
        if (retrofit == null) {
            getRetrofit();
        }
        if (mServer == null) {
            mServer = retrofit.create(Server.class);
        }
    }

    public static RetrofitFactory getInstance() {
        return RetrofitFactoryHolder.Instance;
    }

    @NonNull
    public static Retrofit getRetrofit() {
        synchronized (Object) {
            if (retrofit == null) {
                // 指定缓存路径,缓存大小 50Mb
//                Cache cache = new Cache(new File(InitApp.AppContext.getCacheDir(), "HttpCache"),
//                        1024 * 1024 * 50);
                OkHttpClient.Builder builder = new OkHttpClient.Builder()
//                        .cache(cache)
//                        .addInterceptor(cacheControlInterceptor)
                        .addInterceptor(new OkInterceptor())
                        .addInterceptor(new AddHeadersInterceptor())
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .writeTimeout(15, TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
//                        .hostnameVerifier(new HostnameVerifier() {
//                            @Override
//                            public boolean verify(String hostname, SSLSession session) {
//                                return true;
//                            }
//                        })
//                        .sslSocketFactory(sslParams.sSLSocketFactory,sslParams.trustManager)
                        ;
                retrofit = new Retrofit.Builder()
//                        .baseUrl("http://test1-ordersite.sherpa.com.cn/")
                        .baseUrl("https://api.github.com/")
                        .client(builder.build())
//                        .addConverterFactory(StringConverterFactory.create())
                        .addConverterFactory(GsonConverterFactory.create())
                        .addCallAdapterFactory(CustomCallAdapterFactory.create())
                        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())

                        .build();

            }
        }
        return retrofit;
    }

    public Call<CouponResp> checkCoupon(String customerId, String code, int totalValue) {
        return mServer.checkCoupon(customerId, code, totalValue);
    }

    public CustomCall<CouponResp> loadThree(String customerId, String code, int totalValue) {
        return mServer.loadThree(customerId, code, totalValue);
    }

    private static class RetrofitFactoryHolder {
        public static RetrofitFactory Instance = new RetrofitFactory();
    }

    /**
     * 统一添加header
     */
    public static class AddHeadersInterceptor implements Interceptor {

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request()
                    .newBuilder()
                    .addHeader("language", "10")
                    .addHeader("token", "59c20a1f-3df4-4586-91d2-6bcb0d320eb4")
                    .addHeader("devNo", "HUAWEIPE-TL00M6.03d7ad27e94db1e451080_1812S7TDU15204006422")
                    .addHeader("channelType", "0")
                    .addHeader("devType", "10")
                    .addHeader("registrationId", "18071adc0306d68e178")
                    .build();

            return chain.proceed(request);
        }
    }

    /**
     * 拦截器
     */
    private static class OkInterceptor implements Interceptor {

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            if (request.method().equalsIgnoreCase("get")) {
                String url = request.url().toString();
                HttpUrl httpUrl;
                HttpUrl.Builder builder = request.url().newBuilder();
                builder.addQueryParameter("language", "zh_CN")
                        .addQueryParameter("platform", "3")
                        .addQueryParameter("appType", "2")
                        .addQueryParameter("cityId", "")
                        .addQueryParameter("version", "2.2.0")
                        .addQueryParameter("gdId", "")
                        .addQueryParameter("token", "59c20a1f-3df4-4586-91d2-6bcb0d320eb4");

                if (TextUtils.isEmpty(url) || !url.contains("gdId")) {
                    builder.addQueryParameter("gdId", "");
                }
                httpUrl = builder.build();
                request = request.newBuilder().url(httpUrl).build();
            }
            try {
                return chain.proceed(request);
            } catch (Exception e) {
                LogUtil.e(e.getMessage());
            }
            return chain.proceed(chain.request());
        }
    }


}
