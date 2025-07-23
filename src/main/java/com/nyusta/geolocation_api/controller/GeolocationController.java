package com.nyusta.geolocation_api.controller;

import com.nyusta.geolocation_api.modal.GeoLocationData;
import com.nyusta.geolocation_api.payload.request.GeoLocationRequest;
import com.nyusta.geolocation_api.payload.response.GeoLocationResponse;
import com.nyusta.geolocation_api.service.GeolocationService;
import com.nyusta.geolocation_api.utils.IPUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/geolocation")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class GeolocationController {
    private final GeolocationService geolocationService;

    @GetMapping
    public ResponseEntity<GeoLocationResponse> getCurrentLocation(HttpServletRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            String clientIP = IPUtils.getClientIpAddress(request);
            log.debug("Geolocation request for client IP: {}", clientIP);

            GeoLocationData locationData = geolocationService.getLocationByIP(clientIP);
            long processingTime = System.currentTimeMillis() - startTime;

            return ResponseEntity.ok(GeoLocationResponse.success(locationData, processingTime));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid IP address in request", e);
            return ResponseEntity.badRequest()
                    .body(GeoLocationResponse.error("Invalid IP address"));
        } catch (Exception e) {
            log.error("Error processing geolocation request", e);
            return ResponseEntity.internalServerError()
                    .body(GeoLocationResponse.error("Internal server error"));
        }
    }

    @GetMapping("/{ip}")
    public ResponseEntity<GeoLocationResponse> getLocationByIP(@PathVariable String ip) {
        long startTime = System.currentTimeMillis();

        try {
            log.debug("Geolocation request for IP: {}", ip);

            GeoLocationData locationData = geolocationService.getLocationByIP(ip);
            long processingTime = System.currentTimeMillis() - startTime;

            return ResponseEntity.ok(GeoLocationResponse.success(locationData, processingTime));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid IP address: {}", ip, e);
            return ResponseEntity.badRequest()
                    .body(GeoLocationResponse.error("Invalid IP address: " + ip));
        } catch (Exception e) {
            log.error("Error processing geolocation request for IP: {}", ip, e);
            return ResponseEntity.internalServerError()
                    .body(GeoLocationResponse.error("Internal server error"));
        }
    }
}
