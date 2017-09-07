package com.example.mkio.importsource.volley;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.example.mkio.importsource.R;

import org.json.JSONObject;

import volley.Request;
import volley.RequestQueue;
import volley.Response;
import volley.VolleyError;
import volley.toolbox.JsonObjectRequest;
import volley.toolbox.JsonRequest;
import volley.toolbox.StringRequest;
import volley.toolbox.Volley;

public class VolleyExActivity extends AppCompatActivity {
    private static final String TAG = "VolleyExActivity";
    private String getUrl = "http://127.0.0.1:8099/api/api.ashx";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volley_ex);
    }

    public void get(View view) {
        RequestQueue queue = Volley.newRequestQueue(this);
        String url = "http://gank.io/api/data/Android/10/1";
        JsonObjectRequest jsonRequest = new JsonObjectRequest(Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Log.e(TAG, "onResponse: " + response.toString());
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "onErrorResponse: " + error.getMessage());
            }
        });
        queue.add(jsonRequest);
        queue.start();
    }
}