package com.reodinas2.eatopiaforshop.model;

import java.util.List;

public class Res {
    private String result;
    private String msg;
    private User userInfo;
    private List<Order> orderInfo;
    private float similarity;

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public User getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(User userInfo) {
        this.userInfo = userInfo;
    }

    public List<Order> getOrderInfo() {
        return orderInfo;
    }

    public void setOrderInfo(List<Order> orderInfo) {
        this.orderInfo = orderInfo;
    }

    public float getSimilarity() {
        return similarity;
    }

    public void setSimilarity(float similarity) {
        this.similarity = similarity;
    }
}
