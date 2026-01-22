package io.github.fabricetiennette.radiofy.backend.radio.dto;

import java.util.List;

public record RadiofyStationDto(
        String id,           // stationuuid Radio Browser
        String name,
        String streamUrl,
        String imageUrl,
        String country,
        String language,
        List<String> tags
) {}
