package com.example.signinui;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherApiService {
    @GET("weather")
    Call<WeatherResponse> getCurrentWeather(
            @Query("lat") double lat,
            @Query("lon") double lon,
            @Query("units") String units,
            @Query("appid") String apiKey
    );
}