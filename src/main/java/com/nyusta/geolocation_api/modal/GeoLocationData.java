package com.nyusta.geolocation_api.modal;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeoLocationData {
    private String ip;
    private String country;
    private String countryCode;
    private String region;
    private String regionCode;
    private String city;
    private String postalCode;
    private String timezone;
    private String continent;
    private Double latitude;
    private Double longitude;
    private String isp;
    private Long asn;
    private String asnOrganization;
    private Integer accuracyRadius;
    private String source;
    private Long timestamp;
    private boolean isEuCountry;
    private boolean isPrivateIP;
}
