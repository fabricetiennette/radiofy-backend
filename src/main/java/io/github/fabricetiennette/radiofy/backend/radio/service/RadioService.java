package io.github.fabricetiennette.radiofy.backend.radio.service;

import io.github.fabricetiennette.radiofy.backend.radio.dto.RadiofyStationDto;
import io.github.fabricetiennette.radiofy.backend.radio.gateway.RadioBrowserGateway;
import io.github.fabricetiennette.radiofy.backend.radio.mapper.RadioStationMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RadioService {
    private static final int MAX_LIMIT = 100;

    private final RadioBrowserGateway radioBrowserGateway;

    public RadioService(RadioBrowserGateway radioBrowserGateway) {
        this.radioBrowserGateway = radioBrowserGateway;
    }

    public List<RadiofyStationDto> searchStations(String q, int limit, int offset) {
        String query = q == null ? "" : q.trim();
        if (query.isBlank()) {
            return List.of();
        }

        int safeLimit = clampLimit(limit);
        int safeOffset = Math.max(offset, 0);

        return radioBrowserGateway.searchByName(query, safeLimit, safeOffset)
                .stream()
                .map(RadioStationMapper::toRadiofyDto)
                .toList();
    }

    public List<RadiofyStationDto> browseStations(String countryCode, String tag, int limit, int offset) {
        int safeLimit = clampLimit(limit);
        int safeOffset = Math.max(offset, 0);

        return radioBrowserGateway.browse(countryCode, tag, safeLimit, safeOffset)
                .stream()
                .map(RadioStationMapper::toRadiofyDto)
                .toList();
    }

    public String resolveStreamUrl(String stationUuid) {
        String safeStationUuid = stationUuid == null ? "" : stationUuid.trim();
        if (safeStationUuid.isBlank()) {
            throw new IllegalArgumentException("stationUuid is required.");
        }

        return radioBrowserGateway.resolveStreamUrl(safeStationUuid);
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}