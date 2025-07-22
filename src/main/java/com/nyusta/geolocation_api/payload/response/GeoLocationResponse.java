package com.nyusta.geolocation_api.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nyusta.geolocation_api.modal.GeoLocationData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeoLocationResponse {
    private boolean success;
    private String message;
    private GeoLocationData data;
    private String error;
    private long processingTime;

    public static GeoLocationResponse success(GeoLocationData data, long processingTime) {
        return GeoLocationResponse.builder()
                .success(true)
                .data(data)
                .processingTime(processingTime)
                .build();
    }
    public static GeoLocationResponse error(String error) {
        return GeoLocationResponse.builder()
                .success(false)
                .error(error)
                .build();
    }
}
