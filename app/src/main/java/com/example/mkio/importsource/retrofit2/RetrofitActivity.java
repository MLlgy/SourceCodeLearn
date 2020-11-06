package com.example.mkio.importsource.retrofit2;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.widget.TextView;

import com.example.mkio.importsource.R;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.BufferedSink;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import xutils3.common.util.LogUtil;

public class RetrofitActivity extends AppCompatActivity {

    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_retrofit);
        textView = findViewById(R.id.tv_repo_name);
        findViewById(R.id.tv_check_net).setOnClickListener(view -> {
            RetrofitFactory.getInstance().mServer.getRepos("rengwuxian")// 此时返回的类型就为接口中定义的类型，此时为 Single
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread());
            Single.just("")
                    .map(new Function<String, List<Repo>>() {
                        @Override
                        public List<Repo> apply(String s) throws Exception {
                            return null;
                        }
                    })
                    .map(new Function<List<Repo>, List<Repo>>() {
                        @Override
                        public List<Repo> apply(List<Repo> repos) throws Exception {
                            return null;
                        }
                    })
                    .subscribe(new SingleObserver<List<Repo>>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                            textView.setText("正在请求。。。");
                        }

                        @Override
                        public void onSuccess(List<Repo> repos) {
                            textView.setText(repos.get(0).name);
                        }

                        @Override
                        public void onError(Throwable e) {
                            String errorMsg = e.getMessage();
                            if (errorMsg == null) {
                                errorMsg = e.getClass().getSimpleName();
                            }
                            textView.setText("error： " + errorMsg);
                        }
                    });

            Single.just("").subscribe(new SingleObserver<String>() {
                @Override
                public void onSubscribe(Disposable d) {

                }

                @Override
                public void onSuccess(String s) {

                }

                @Override
                public void onError(Throwable e) {

                }
            });

//            String name = "etFileName.getText().toString().trim()";
//            name = TextUtils.isEmpty(name) ? "1.png" : name;
//            String path = Environment.getExternalStorageDirectory() + File.separator + name;
//            File file = new File(path);
//
//            RequestBody fileRQ = RequestBody.create(MediaType.parse("image/*"), file);
//            MultipartBody.Part part = MultipartBody.Part.createFormData("picture", file.getName(), fileRQ);
//
//
//            RetrofitFactory.getInstance().mServer.uploadOneFile(part);
        });

//        RetrofitFactory.getInstance().
//        Response{protocol=http/1.1, code=200, message=OK, url=http://test1-ordersite.sherpa.com.cn/sherpa-web-api/newcoupon?customId=2018040202512043&couponNumber=133&totolValue=315&language=zh_CN&platform=3&appType=2&cityId=&version=2.2.0&gdId=&token=59c20a1f-3df4-4586-91d2-6bcb0d320eb4&gdId=}
    }
}
