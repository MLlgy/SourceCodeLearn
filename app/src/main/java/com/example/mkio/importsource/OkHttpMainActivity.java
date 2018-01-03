package com.example.mkio.importsource;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OkHttpMainActivity extends AppCompatActivity implements View.OnClickListener {
    @Override
    public void onClick(View view) {

    }
//    private Button clickbt;
//    private TextView textresult;
//    File cachefile;
//    Cache mcache;
//    private OkHttpClient client;
//    ConnectivityManager mConnectivityManager;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//        mConnectivityManager = (ConnectivityManager) this
//                .getSystemService(Context.CONNECTIVITY_SERVICE);
//        cachefile = new File(getExternalCacheDir(), "okcache");
//        mcache = new Cache(cachefile, 10 * 1024 * 1024);
//        client = new OkHttpClient.Builder().cache(mcache).build();
//        textresult = (TextView) findViewById(R.id.textresult);
//        clickbt = (Button) findViewById(R.id.click);
//        clickbt.setOnClickListener(this);
//    }
//
//    Handler handler = new Handler() {
//        @Override
//        public void handleMessage(Message msg) {
//            super.handleMessage(msg);
//            Response msgresponse = (Response) msg.obj;
//            Log.e("Mk  handleMessage", "THreand :" + Thread.currentThread());
//            try {
//                textresult.setText("" + msgresponse.isSuccessful());
//            } catch (Exception e) {
//                Log.w("wenfeng", "Exception=" + e.toString());
//            }
//        }
//    };
//
//    @Override
//    public void onClick(View v) {
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    execute();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        }).start();
//    }
//
//    public boolean isNetworkConnected() {
//        NetworkInfo mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
//        if (mNetworkInfo != null) {
//            return mNetworkInfo.isAvailable();
//        }
//
//        return false;
//    }
//
//    public void execute() throws Exception {
//        Log.i("wenfeng", "execute");
//
//        CacheControl.Builder cachebuild = new CacheControl.Builder();
//        cachebuild.maxAge(30, TimeUnit.SECONDS);
//
//        CacheControl mCacheControl = cachebuild.build();
//        /*
//        if(!isNetworkConnected()){
//            Log.i("wenfeng","force cache");
//            mCacheControl = CacheControl.FORCE_CACHE;
//        }
//        */
//        Request request = new Request.Builder()
//                .url("http://publicobject.com/helloworld.txt")
//                .cacheControl(mCacheControl)
//                .build();
//        Log.i("wenfeng", "request body=null " + (request.body() == null));
//        /*
//        Response response = client.newCall(request).execute();
//        Log.i("wenfeng","interceptors size="+client.interceptors().size());
//        Log.i("wenfeng","client connectionCount="+client.connectionPool().connectionCount());
//        Log.i("wenfeng","client idleconnectionCount="+client.connectionPool().toString());
//        if(response.isSuccessful()){
//            Log.i("wenfeng","body="+response.body().string());
//            Message msg=new Message();
//            msg.obj=response;
//            handler.sendMessage(msg);
//        }
//        */
//        Call mcall = client.newCall(request);
//        mcall.enqueue(new Callback() {
//            @Override
//            public void onFailure(Call call, IOException e) {
//
//            }
//
//            @Override
//            public void onResponse(Call call, Response response) throws IOException {
//                if (response.isSuccessful()) {
//                    Log.i("wenfeng", "yibua body=" + response.body().string());
//                    Log.i("wenfeng", "yibua network response=" + response.networkResponse());
//                    Log.i("wenfeng", "yibua cache response=" + response.cacheResponse());
//                    Message msg = new Message();
//                    msg.obj = response;
//                    handler.sendMessage(msg);
//                    Log.e("Mk  onResponse", "THreand :" + Thread.currentThread());
//                }
//            }
//        });
//    }
}
