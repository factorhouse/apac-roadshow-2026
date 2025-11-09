package com.ververica.composable_job.quarkus.geo;

import com.ververica.composable_job.model.GeoCoordinate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class GeoLocationService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<GeoCoordinate> readCoordinates = Collections.synchronizedList(new ArrayList<>());

    @Inject
    JsonUrlReaderService jsonLineReaderService;

    public GeoCoordinate collectGeoCoordinate() throws JsonProcessingException {
        Optional<String> lineRead = jsonLineReaderService.readLine();
        if (lineRead.isEmpty()) {
            return null;
        }

        String line = trimComma(lineRead.get());
        List<Double> numbers = getCoordinates(line);

        GeoCoordinate geoCoordinate = new GeoCoordinate(numbers.get(0), numbers.get(1), numbers.get(2));

        readCoordinates.add(geoCoordinate);

        return geoCoordinate;
    }

    public List<GeoCoordinate> getReadCoordinates() {
        return readCoordinates;
    }

    private String trimComma(String line) {
        return Optional.of(line)
                .filter(s -> s.endsWith(","))
                .map(s -> s.substring(0, s.length() - 1))
                .orElse(line);
    }

    private List<Double> getCoordinates(String line) throws JsonProcessingException {
        JsonNode jsonNode = MAPPER.readTree(line);
        JsonNode geometry = jsonNode.get("geometry");

        JsonNode coordinates = geometry.get("coordinates");

        return StreamSupport.stream(coordinates.spliterator(), false)
                .map(JsonNode::asDouble)
                .toList();
    }
}
