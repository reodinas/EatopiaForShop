package com.reodinas2.eatopiaforshop.api;

import com.reodinas2.eatopiaforshop.model.Res;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

public interface FaceApi {

    // 얼굴검색 api
    @Multipart
    @POST("/faces/search")
    Call<Res> faceSearch(@Query("restaurantId") int restaurantId,
                         @Part MultipartBody.Part photo);
}
