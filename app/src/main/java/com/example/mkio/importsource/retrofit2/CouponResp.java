package com.example.mkio.importsource.retrofit2;


/**
 * Created by liulimeng_pc on 2017/6/20.
 */

public class CouponResp extends BaseResp {
    public Coupon data;

    @Override
    public String toString() {
        return "CouponResp{" +
                "data=" +
                ", errorCode=" + errorCode +
                ", message='" + message + '\'' +
                '}';
    }
}
