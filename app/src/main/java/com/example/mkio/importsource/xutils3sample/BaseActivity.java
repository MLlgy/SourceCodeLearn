package com.example.mkio.importsource.xutils3sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import xutils3.x;

/**
 * Created by wyouflf on 15/11/4.
 */
public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        x.view().inject(this);
    }
}
