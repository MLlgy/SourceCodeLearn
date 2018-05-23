package com.example.mkio.importsource.volleyexample;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.example.mkio.importsource.R;

import org.json.JSONArray;
import org.json.JSONArray;

import java.util.HashMap;
import java.util.Map;

import volley.AuthFailureError;
import volley.Request;
import volley.RequestQueue;
import volley.Response;
import volley.VolleyError;
import volley.toolbox.JsonArrayRequest;
import volley.toolbox.StringRequest;
import volley.toolbox.Volley;

public class VolleyActivity extends AppCompatActivity {

    private RequestQueue mQueue;
    JSONArray commandsArray = new JSONArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volley);
        mQueue = Volley.newRequestQueue(this);
    }

    public void start(View view) {
        StringRequest stringRequest = new StringRequest("http://www.baidu.com",
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d("TAG", response);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("TAG", error.getMessage(), error);
            }
        });


        JsonArrayRequest request = new JsonArrayRequest(Request.Method.POST, "", commandsArray, new Response.Listener<JSONArray>() {
            @Override
            public void onResponse(JSONArray response) {

            }
        }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {

                    }
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
//                String token = SPUtil.getToken();
//                LogUtil.debug("token3 = " + token);
                HashMap<String, String> headers = new HashMap<String, String>();
//                String cookie = getCookie(token);
//                headers.put("Cookie", cookie);
                return headers;
            }

            @Override
            public int getMethod() {
                return super.getMethod();
            }
        };
        mQueue.add(stringRequest);
    }
}
