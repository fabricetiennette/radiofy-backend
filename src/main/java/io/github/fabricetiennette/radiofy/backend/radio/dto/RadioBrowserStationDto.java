package io.github.fabricetiennette.radiofy.backend.radio.dto;

public record RadioBrowserStationDto(
        String stationuuid,
        String name,
        String url_resolved,
        String favicon,
        String country,
        String language,
        String tags
) { }
