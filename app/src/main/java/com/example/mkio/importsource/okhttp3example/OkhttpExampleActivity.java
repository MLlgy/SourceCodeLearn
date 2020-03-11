package com.example.mkio.importsource.okhttp3example;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.mkio.importsource.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;


public class OkhttpExampleActivity extends AppCompatActivity implements View.OnClickListener {
    public static final MediaType MEDIA_TYPE_MARKDOWN
            = MediaType.parse("text/x-markdown; charset=utf-8");
    private static final int WRITE_PERMISSION = 1;
    private static final MediaType MEDIA_TYPE_PNG = MediaType.parse("image/png");
    private OkHttpClient mOkHttpClient;
    private Button bt_send;
    private Button bt_postsend;
    private Button bt_sendfile;
    private Button bt_downfile;
    private ImageView mShowImage;
    private String[] urlStr = new String[]
            {"https://images.unsplash.com/photo-1494879540385-bc170b0878a7?ixlib=rb-0.3.5&q=80&fm=jpg&crop=entropy&cs=tinysrgb&w=1080&fit=max&ixid=eyJhcHBfaWQiOjcwOTV9&s=3cdfe0410304367615d29b675ebeece7"
                    , "https://img.alicdn.com/tfs/TB1CnXQxxjaK1RjSZKzXXXVwXXa-504-300.jpg"};

    private int i = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_okhttp_example);
        initperssion();
        initOkHttpClient();
        bt_send = this.findViewById(R.id.bt_send);
        bt_sendfile = this.findViewById(R.id.bt_sendfile);
        bt_postsend = this.findViewById(R.id.bt_postsend);
        bt_downfile = this.findViewById(R.id.bt_downfile);
        mShowImage = findViewById(R.id.iv_show_image);
        bt_send.setOnClickListener(this);
        bt_postsend.setOnClickListener(this);
        bt_sendfile.setOnClickListener(this);
        bt_downfile.setOnClickListener(this);
    }

    private void initperssion() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        WRITE_PERMISSION);
            }
        }

        ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case WRITE_PERMISSION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "yes", Toast.LENGTH_SHORT).show();

                } else {

                }
                return;
            }

        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bt_send:
                getAsynHttp();
                break;
            case R.id.bt_postsend:
                postAsynHttp();
                break;
            case R.id.bt_sendfile:
                postAsynFile();
                break;
            case R.id.bt_downfile:
                downAsynFile();
//             sendMultipart();
                break;
        }
    }

    //    /storage/emulated/0/Android/data/com.example.mkio.importsource/cache
    private void initOkHttpClient() {
        File sdcache = getExternalCacheDir();
        int cacheSize = 10 * 1024 * 1024;
        //OkHttpClient.Builder 可以用来自定义生产 OkHttpClient 实例
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
//                .cache(new Cache(sdcache.getAbsoluteFile(), cacheSize))
                .addInterceptor(new LoggingInterceptor())
//                .addNetworkInterceptor(new LoggingInterceptor())
                ;
        mOkHttpClient = builder.build();
    }

    /**
     * get异步请求
     */
    private void getAsynHttp() {


        Request.Builder requestBuilder = new Request.Builder().url("https://wanandroid.com/wxarticle/chapters/json")
                .method("GET", null);
//        requestBuilder.method("GET", null);
        Request request = requestBuilder.build();
        Call mcall = mOkHttpClient.newCall(request);
        mcall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (null != response.cacheResponse()) {
                    String str = response.cacheResponse().toString();
                    Log.i("wangshu", "cache---" + str);
                } else {

//                    response.body().string();
                    String str = response.networkResponse().toString();
                    Log.i("wangshu", "network---" + str + "    " + response.body().string());
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "请求成功", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });


        mOkHttpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                super.onOpen(webSocket, response);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                super.onMessage(webSocket, text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                super.onMessage(webSocket, bytes);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                super.onClosing(webSocket, code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                super.onClosed(webSocket, code, reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
            }
        });
    }

    /**
     * post异步请求
     */
    private void postAsynHttp() {
        RequestBody formBody = new FormBody.Builder()
                .add("size", "10")
                .build();
        Request request = new Request.Builder()
                .url("http://api.1-blog.com/biz/bizserver/article/list.do")
                .post(formBody)
                .build();
        Call call = mOkHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String str = response.body().string();
                Log.i("wangshu", str);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "请求成功", Toast.LENGTH_SHORT).show();
                    }
                });
            }

        });
    }

    /**
     * 异步上传文件
     */
    private void postAsynFile() {
        File file = new File("/sdcard/wangshu.txt");
        Request request = new Request.Builder()
                .url("https://api.github.com/markdown/raw")
                .post(RequestBody.create(MEDIA_TYPE_MARKDOWN, file))
                .build();
        mOkHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.i("wangshu", response.body().string());
            }
        });
    }


    /**
     * 异步下载文件 在 这里read 真正执行的为 { okio.RealBufferedSource#read(byte[], int, int)}
     */
    private void downAsynFile() {
        i++;
        int index = i % urlStr.length;
        String url = urlStr[index];
        Request request = new Request.Builder().url(url).build();

        mOkHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) {
                InputStream inputStream = response.body().byteStream();
                FileOutputStream fileOutputStream = null;
                File mFile = new File(Environment.getExternalStorageDirectory().getPath() + "/wangshu.jpg");
                try {// TODO: 2019/1/24 此时在这里 文件仍为 0.tmp 和 1.tmp
                    fileOutputStream = new FileOutputStream(mFile);
                    byte[] buffer = new byte[2048];
                    int len = 0;
                    while ((len = inputStream.read(buffer)) != -1) {
                        fileOutputStream.write(buffer, 0, len);
                    }
                    fileOutputStream.flush();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            BitmapFactory.Options options;
                            options = new BitmapFactory.Options();
                            options.inSampleSize = 2;
//                           以下语句报错语句： skia : --- SkImageDecoder::Factory returned null
//                            Bitmap mBitmap1 = BitmapFactory.decodeStream(inputStream,null,options);
                            Bitmap mBitmap = BitmapFactory.decodeFile(mFile.getAbsolutePath(), options);
                            mShowImage.setImageBitmap(mBitmap);
                            Log.d("wangshu", "图片显示成功");

                        }
                    });
                } catch (IOException e) {
                    Log.i("wangshu", "IOException");
                    e.printStackTrace();
                }


                Log.d("wangshu", "文件下载成功");

            }
        });
    }

    private void sendMultipart() {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("title", "wangshu")
                .addFormDataPart("image", "wangshu.jpg",
                        RequestBody.create(MEDIA_TYPE_PNG, new File("/sdcard/wangshu.jpg")))
                .build();

        Request request = new Request.Builder()
                .header("Authorization", "Client-ID " + "...")
                .url("https://api.imgur.com/3/image")
                .post(requestBody)
                .build();

        mOkHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.i("wangshu", response.body().string());
            }
        });
    }

}
