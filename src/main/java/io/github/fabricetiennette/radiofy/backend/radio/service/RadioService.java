package io.github.fabricetiennette.radiofy.backend.radio.service;

import io.github.fabricetiennette.radiofy.backend.radio.gateway.RadioBrowserGateway;
import io.github.fabricetiennette.radiofy.backend.radio.dto.RadioBrowserStationDto;
import io.github.fabricetiennette.radiofy.backend.radio.dto.RadiofyStationDto;
import io.github.fabricetiennette.radiofy.backend.radio.mapper.RadioStationMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RadioService {
    private final RadioBrowserGateway radioBrowserGateway;

    public RadioService(RadioBrowserGateway radioBrowserGateway) {
        this.radioBrowserGateway = radioBrowserGateway;
    }

    public List<RadiofyStationDto> searchStations(String q, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);

        return radioBrowserGateway.searchByName(q, safeLimit)
                .stream()
                .map(RadioStationMapper::toRadiofyDto)
                .toList();
    }
}