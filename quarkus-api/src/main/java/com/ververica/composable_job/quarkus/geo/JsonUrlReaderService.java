package com.ververica.composable_job.quarkus.geo;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;

@ApplicationScoped
public class JsonUrlReaderService {

    private static final String JSON_URL = "https://docs.mapbox.com/mapbox-gl-js/assets/earthquakes.geojson";
    private BufferedReader reader;
    private HttpURLConnection connection;

    @PostConstruct
    public void init() {
        try {
            URL url = new URL(JSON_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            skipFirstLines();
        } catch (IOException e) {
            throw new RuntimeException("Error opening URL connection", e);
        }
    }

    private void skipFirstLines() {
        this.readLine();
        this.readLine();
        this.readLine();
        this.readLine();
    }

    public Optional<String> readLine() {
        try {
            if (reader == null) {
                return Optional.empty();
            }
            String line = reader.readLine();
            return Optional.ofNullable(line);
        } catch (IOException e) {
            throw new RuntimeException("Error reading from URL", e);
        }
    }

    @PreDestroy
    public void close() {
        try {
            if (reader != null) {
                reader.close();
            }
            if (connection != null) {
                connection.disconnect();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error closing connection", e);
        }
    }
}


