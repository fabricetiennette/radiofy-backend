package io.github.fabricetiennette.radiofy.backend.radio.service;

import io.github.fabricetiennette.radiofy.backend.radio.dto.RadioBrowserStationDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/// Collapses the several entries Radio Browser holds for one real station.
///
/// Being a contributed directory, the same station is submitted repeatedly: once
/// per stream format, once per spelling, sometimes twice by accident. A search for
/// "nova" otherwise returns four rows that all play Radio Nova.
final class StationDeduplicator {

    private StationDeduplicator() {}

    /// Popularity first, because it reflects what listeners actually use; audio
    /// quality only settles ties. Many stations sit at zero clicks, so without the
    /// tiebreakers the survivor would effectively be picked at random.
    private static final Comparator<RadioBrowserStationDto> BEST_FIRST =
            Comparator.comparingInt(RadioBrowserStationDto::clickcount)
                    .thenComparingInt(RadioBrowserStationDto::votes)
                    .thenComparingInt(RadioBrowserStationDto::bitrate)
                    .reversed();

    /// Keeps the incoming order — the position of each station's first appearance —
    /// so the popularity ranking of the list as a whole is preserved.
    static List<RadioBrowserStationDto> deduplicate(List<RadioBrowserStationDto> stations) {
        Map<String, Integer> groupOfKey = new HashMap<>();
        List<List<RadioBrowserStationDto>> groups = new ArrayList<>();

        for (RadioBrowserStationDto station : stations) {
            if (station == null || station.stationuuid() == null) {
                continue;
            }

            List<String> keys = identityKeys(station);
            Integer group = keys.stream()
                    .map(groupOfKey::get)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            if (group == null) {
                group = groups.size();
                groups.add(new ArrayList<>());
            }

            groups.get(group).add(station);
            for (String key : keys) {
                groupOfKey.putIfAbsent(key, group);
            }
        }

        return groups.stream()
                .map(group -> group.stream().min(BEST_FIRST).orElseThrow())
                .toList();
    }

    /// Trailing "| AAC 192k", "(128k MP3)" and the like. Stripping them is what
    /// links the format variants of one station, whose names are otherwise equal.
    private static final Pattern STREAM_DESCRIPTOR = Pattern.compile(
            "([\\s|\\-–(\\[]+(aac\\+?|mp3|ogg|opus|flac|hls|\\d{2,4}\\s?k(bps)?))+[\\s)\\]]*$",
            Pattern.CASE_INSENSITIVE
    );

    /// Three ways of recognising the same station, because none catches everything.
    private static List<String> identityKeys(RadioBrowserStationDto station) {
        List<String> keys = new ArrayList<>(3);

        // The strongest of the three, and the only one that cannot over-merge: two
        // entries pointing at the same file play the same audio, whatever they are
        // called. Radio Browser holds the same stream under http and https.
        String stream = normalisedUrl(station.url_resolved());
        if (!stream.isBlank()) {
            keys.add("stream:" + stream);
        }

        String homepage = stationHomepage(station.homepage());
        if (!homepage.isBlank()) {
            keys.add("home:" + homepage);
        }

        String name = comparableName(station.name());
        if (!name.isBlank()) {
            keys.add("name:" + name + "|" + normalised(station.countrycode()));
        }

        return keys;
    }

    /// Only a home page carrying a path identifies a station; a bare domain
    /// identifies a broadcaster. nova.fr alone covers Radio Nova, Radio Nova France
    /// and Radio Nova Vintage — three different stations — while
    /// vip-radios.fm/station/bossa-nova-brazil designates exactly one.
    private static String stationHomepage(String homepage) {
        String trimmed = normalisedUrl(homepage).replaceFirst("^www\\.", "");

        return trimmed.contains("/") ? trimmed : "";
    }

    private static String normalisedUrl(String url) {
        if (url == null) {
            return "";
        }

        return url.trim().toLowerCase(Locale.ROOT)
                .replaceFirst("^https?://", "")
                .replaceAll("/+$", "");
    }

    private static String comparableName(String name) {
        String normalised = normalised(name);
        String stripped = STREAM_DESCRIPTOR.matcher(normalised).replaceAll("").trim();

        // A station genuinely called "MP3" would otherwise vanish entirely.
        return stripped.isBlank() ? normalised : stripped;
    }

    private static String normalised(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
