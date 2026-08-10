package io.github.fabricetiennette.radiofy.backend.radio.dto;

/// Only the fields we actually use. The last five never reach the client: they
/// serve to recognise duplicate entries of one real station and to pick which of
/// them to keep.
public record RadioBrowserStationDto(
        String stationuuid,
        String name,
        String url_resolved,
        String favicon,
        String country,
        String language,
        String tags,
        String homepage,
        String countrycode,
        int votes,
        int bitrate,
        int clickcount
) { }
