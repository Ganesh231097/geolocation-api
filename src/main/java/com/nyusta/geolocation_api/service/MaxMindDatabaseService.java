package com.nyusta.geolocation_api.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.nyusta.geolocation_api.config.MaxMindProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class MaxMindDatabaseService {
    private final MaxMindProperties properties;
    private final Map<String, DatabaseReader> databaseReaders = new HashMap<>();
    private volatile boolean initialized = false;

    public MaxMindDatabaseService(MaxMindProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() {
        log.info("Initializing MaxMind database service...");
        try {
            createDatabaseDirectory();
            downloadDatabasesIfNeeded();
            loadDatabases();
            initialized = true;
            log.info("MaxMind database service initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize MaxMind database service", e);
            throw new RuntimeException("MaxMind initialization failed", e);
        }
    }
    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up MaxMind database readers...");
        databaseReaders.values().forEach(reader -> {
            try {
                reader.close();
            } catch (IOException e) {
                log.warn("Error closing database reader", e);
            }
        });
        databaseReaders.clear();
    }

    private void createDatabaseDirectory() throws IOException {
        Path dbPath = Paths.get(properties.getDatabasePath());
        if (!Files.exists(dbPath)) {
            Files.createDirectories(dbPath);
            log.info("Created database directory: {}", dbPath);
        }
    }
    private void downloadDatabasesIfNeeded() throws IOException {
        if (!properties.isAutoUpdate()) {
            log.info("Auto-update disabled, skipping database download");
            return;
        }

        for (Map.Entry<String, String> entry : properties.getDatabases().entrySet()) {
            String dbType = entry.getKey();
            String filename = entry.getValue();
            Path dbFile = Paths.get(properties.getDatabasePath(), filename);

            if (!Files.exists(dbFile) || isDatabaseOutdated(dbFile)) {
                log.info("Downloading {} database...", dbType);
                downloadAndExtractDatabase(dbType, filename);
            } else {
                log.debug("{} database is up to date", dbType);
            }
        }
    }
    private boolean isDatabaseOutdated(Path dbFile) {
        try {
            LocalDateTime lastModified = LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(dbFile).toInstant(),
                    java.time.ZoneId.systemDefault()
            );
            LocalDateTime cutoff = LocalDateTime.now().minusDays(properties.getUpdateInterval());
            return lastModified.isBefore(cutoff);
        } catch (IOException e) {
            log.warn("Could not check database file modification time", e);
            return true;
        }
    }
    private void downloadAndExtractDatabase(String dbType, String filename) throws IOException {
        String downloadUrl = properties.getDownloadUrls().get(dbType);
        if (downloadUrl == null) {
            throw new IllegalArgumentException("No download URL configured for database type: " + dbType);
        }

        // Download compressed file
        Path tempFile = Paths.get(properties.getDatabasePath(), filename + ".tar.gz");
        try (InputStream in = new URL(downloadUrl).openStream();
             FileOutputStream out = new FileOutputStream(tempFile.toFile())) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        // Extract the .mmdb file from tar.gz
        extractMmdbFromTarGz(tempFile, filename);

        // Clean up temp file
        Files.deleteIfExists(tempFile);
        log.info("Successfully downloaded and extracted {} database", dbType);
    }
    private void extractMmdbFromTarGz(Path tarGzFile, String targetFilename) throws IOException {
        Path outputPath = Paths.get(properties.getDatabasePath(), targetFilename);

        try (FileInputStream fis = new FileInputStream(tarGzFile.toFile());
             GzipCompressorInputStream gzis = new GzipCompressorInputStream(fis);
             TarArchiveInputStream tais = new TarArchiveInputStream(gzis)) {

            TarArchiveEntry entry;
            while ((entry = tais.getNextTarEntry()) != null) {
                if (entry.getName().endsWith(".mmdb")) {
                    try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = tais.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    break;
                }
            }
        }
    }
    private void loadDatabases() throws IOException {
        for (Map.Entry<String, String> entry : properties.getDatabases().entrySet()) {
            String dbType = entry.getKey();
            String filename = entry.getValue();
            Path dbFile = Paths.get(properties.getDatabasePath(), filename);

            if (Files.exists(dbFile)) {
                DatabaseReader reader = new DatabaseReader.Builder(dbFile.toFile()).build();
                databaseReaders.put(dbType, reader);
                log.info("Loaded {} database: {}", dbType, dbFile);
            } else {
                log.warn("{} database not found: {}", dbType, dbFile);
            }
        }
    }
    public CountryResponse getCountryResponse(InetAddress ipAddress) throws IOException, GeoIp2Exception {
        DatabaseReader reader = databaseReaders.get("country");
        if (reader == null) {
            throw new IllegalStateException("Country database not loaded");
        }
        return reader.country(ipAddress);
    }

    public CityResponse getCityResponse(InetAddress ipAddress) throws IOException, GeoIp2Exception {
        DatabaseReader reader = databaseReaders.get("city");
        if (reader == null) {
            throw new IllegalStateException("City database not loaded");
        }
        return reader.city(ipAddress);
    }

    public AsnResponse getAsnResponse(InetAddress ipAddress) throws IOException, GeoIp2Exception {
        DatabaseReader reader = databaseReaders.get("asn");
        if (reader == null) {
            throw new IllegalStateException("ASN database not loaded");
        }
        return reader.asn(ipAddress);
    }

    public boolean isInitialized() {
        return initialized;
    }

    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void scheduledDatabaseUpdate() {
        if (!properties.isAutoUpdate()) {
            return;
        }

        log.info("Starting scheduled database update...");
        try {
            // Close existing readers
            cleanup();

            // Download and reload
            downloadDatabasesIfNeeded();
            loadDatabases();
            initialized = true;

            log.info("Scheduled database update completed successfully");
        } catch (Exception e) {
            log.error("Scheduled database update failed", e);
            initialized = false;
        }
    }
}
