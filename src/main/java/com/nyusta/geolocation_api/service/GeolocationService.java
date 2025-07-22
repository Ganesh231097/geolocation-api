package com.nyusta.geolocation_api.service;

import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Continent;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Location;
import com.maxmind.geoip2.record.Postal;
import com.maxmind.geoip2.record.Subdivision;
import com.nyusta.geolocation_api.modal.GeoLocationData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeolocationService {
    private final MaxMindDatabaseService maxMindService;

    // EU country codes for GDPR compliance
    private static final Set<String> EU_COUNTRIES = new HashSet<>(Arrays.asList(
            "AD", "AT", "BE", "BG", "CH", "CY", "CZ", "DE", "DK", "EE", "ES", "FI",
            "FR", "GB", "GR", "HR", "HU", "IE", "IS", "IT", "LI", "LT", "LU", "LV",
            "MC", "MT", "NL", "NO", "PL", "PT", "RO", "SE", "SI", "SK", "SM", "VA"
    ));

    @Cacheable(value = "geolocations", key = "#ip")
    public GeoLocationData getLocationByIP(String ip) {
        if (!maxMindService.isInitialized()) {
            throw new IllegalStateException("MaxMind database service not initialized");
        }

        try {
            InetAddress ipAddress = InetAddress.getByName(ip);

            // Check if it's a private IP
            boolean isPrivate = isPrivateIP(ipAddress);

            GeoLocationData.GeoLocationDataBuilder builder = GeoLocationData.builder()
                    .ip(ip)
                    .isPrivateIP(isPrivate)
                    .source("MaxMind-GeoLite2")
                    .timestamp(System.currentTimeMillis());

            if (isPrivate) {
                // For private IPs, return basic info
                return builder
                        .country("Unknown")
                        .countryCode("XX")
                        .city("Private Network")
                        .build();
            }

            // Try to get detailed city information first
            try {
                CityResponse cityResponse = maxMindService.getCityResponse(ipAddress);
                populateFromCityResponse(builder, cityResponse);
            } catch (Exception e) {
                log.debug("City lookup failed, trying country lookup for IP: {}", ip);

                // Fallback to country-only lookup
                try {
                    CountryResponse countryResponse = maxMindService.getCountryResponse(ipAddress);
                    populateFromCountryResponse(builder, countryResponse);
                } catch (Exception countryEx) {
                    log.warn("Country lookup also failed for IP: {}", ip, countryEx);
                    return builder
                            .country("Unknown")
                            .countryCode("XX")
                            .build();
                }
            }

            // Try to get ASN information
            try {
                AsnResponse asnResponse = maxMindService.getAsnResponse(ipAddress);
                if (asnResponse.getAutonomousSystemNumber() != null) {
                    builder.asn(asnResponse.getAutonomousSystemNumber().longValue());
                }
                if (asnResponse.getAutonomousSystemOrganization() != null) {
                    builder.asnOrganization(asnResponse.getAutonomousSystemOrganization());
                    builder.isp(asnResponse.getAutonomousSystemOrganization());
                }
            } catch (Exception e) {
                log.debug("ASN lookup failed for IP: {}", ip);
            }

            GeoLocationData result = builder.build();

            // Set EU flag for compliance
            if (result.getCountryCode() != null) {
                result.setEuCountry(EU_COUNTRIES.contains(result.getCountryCode()));
            }

            return result;

        } catch (UnknownHostException e) {
            log.warn("Invalid IP address: {}", ip);
            throw new IllegalArgumentException("Invalid IP address: " + ip, e);
        } catch (Exception e) {
            log.error("Error looking up geolocation for IP: {}", ip, e);
            throw new RuntimeException("Geolocation lookup failed", e);
        }
    }

    private void populateFromCityResponse(GeoLocationData.GeoLocationDataBuilder builder, CityResponse response) {
        // Country information
        Country country = response.getCountry();
        if (country != null) {
            builder.country(country.getName());
            builder.countryCode(country.getIsoCode());
        }

        // Continent information
        Continent continent = response.getContinent();
        if (continent != null) {
            builder.continent(continent.getCode());
        }

        // Subdivision (state/region) information
        Subdivision subdivision = response.getMostSpecificSubdivision();
        if (subdivision != null) {
            builder.region(subdivision.getName());
            builder.regionCode(subdivision.getIsoCode());
        }

        // City information
        City city = response.getCity();
        if (city != null) {
            builder.city(city.getName());
        }

        // Postal code
        Postal postal = response.getPostal();
        if (postal != null) {
            builder.postalCode(postal.getCode());
        }

        // Location (coordinates)
        Location location = response.getLocation();
        if (location != null) {
            if (location.getLatitude() != null) {
                builder.latitude(location.getLatitude());
            }
            if (location.getLongitude() != null) {
                builder.longitude(location.getLongitude());
            }
            if (location.getTimeZone() != null) {
                builder.timezone(location.getTimeZone());
            }
            if (location.getAccuracyRadius() != null) {
                builder.accuracyRadius(location.getAccuracyRadius());
            }
        }
    }

    private void populateFromCountryResponse(GeoLocationData.GeoLocationDataBuilder builder, CountryResponse response) {
        // Country information
        Country country = response.getCountry();
        if (country != null) {
            builder.country(country.getName());
            builder.countryCode(country.getIsoCode());
        }

        // Continent information
        Continent continent = response.getContinent();
        if (continent != null) {
            builder.continent(continent.getCode());
        }
    }

    private boolean isPrivateIP(InetAddress address) {
        // Check for private IP ranges
        byte[] addr = address.getAddress();

        if (addr.length == 4) { // IPv4
            // 10.0.0.0/8
            if (addr[0] == 10) return true;

            // 172.16.0.0/12
            if (addr[0] == (byte) 172 && (addr[1] & 0xF0) == 16) return true;

            // 192.168.0.0/16
            if (addr[0] == (byte) 192 && addr[1] == (byte) 168) return true;

            // 127.0.0.0/8 (loopback)
            if (addr[0] == 127) return true;
        }

        // Add IPv6 private ranges if needed
        return false;
    }

    public boolean isServiceAvailable() {
        return maxMindService.isInitialized();
    }
}
