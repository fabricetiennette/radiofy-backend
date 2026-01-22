package io.github.fabricetiennette.radiofy.backend.radio.mapper;

import io.github.fabricetiennette.radiofy.backend.radio.dto.RadioBrowserStationDto;
import io.github.fabricetiennette.radiofy.backend.radio.dto.RadiofyStationDto;

import java.util.List;


public final class RadioStationMapper {

    private RadioStationMapper() {}

    public static RadiofyStationDto toRadiofyDto(RadioBrowserStationDto s) {
        var tags = (s.tags() == null || s.tags().isBlank())
                ? List.<String>of()
                : List.of(s.tags().split(","));

        var cleanedTags = tags.stream()
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .toList();

        return new RadiofyStationDto(
                s.stationuuid(),
                s.name() == null ? "" : s.name().trim(),
                s.url_resolved(),
                s.favicon(),
                s.country(),
                s.language(),
                cleanedTags
        );
    }
}
