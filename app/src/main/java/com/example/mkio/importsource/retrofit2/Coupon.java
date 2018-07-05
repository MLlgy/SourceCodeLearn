package com.example.mkio.importsource.retrofit2;

import java.io.Serializable;

/**
 * Created by wangpd on 2017/6/8 0008.
 * 优惠券模型
 */

public class Coupon implements Serializable {
    public String couponNumber;//	优惠券号码
    public float couponPrice;//	优惠券价格
    public float couponExtPrice;//	剩余价格
    public float usePrice;//	本次使用价格

    @Override
    public String toString() {
        return "Coupon{" +
                "couponNumber='" + couponNumber + '\'' +
                ", couponPrice=" + couponPrice +
                ", couponExtPrice=" + couponExtPrice +
                ", usePrice=" + usePrice +
                '}';
    }
}
