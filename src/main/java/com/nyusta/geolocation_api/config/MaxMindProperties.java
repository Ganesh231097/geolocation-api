package com.nyusta.geolocation_api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "maxmind")
public class MaxMindProperties {
    private String licenseKey;
    private String databasePath;
    private boolean autoUpdate = true;
    private int updateInterval = 7; // days
    private Map<String, String> databases;
    private Map<String, String> downloadUrls;
}
