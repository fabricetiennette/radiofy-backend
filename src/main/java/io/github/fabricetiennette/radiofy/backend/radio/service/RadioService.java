package io.github.fabricetiennette.radiofy.backend.radio.service;

import io.github.fabricetiennette.radiofy.backend.radio.dto.RadioBrowserStationDto;
import io.github.fabricetiennette.radiofy.backend.radio.dto.RadioBrowserTagDto;
import io.github.fabricetiennette.radiofy.backend.radio.dto.RadiofyStationDto;
import io.github.fabricetiennette.radiofy.backend.radio.gateway.RadioBrowserGateway;
import io.github.fabricetiennette.radiofy.backend.radio.gateway.RadioBrowserUnavailableException;
import io.github.fabricetiennette.radiofy.backend.radio.mapper.RadioStationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

@Slf4j
@Service
public class RadioService {
    private static final int MAX_LIMIT = 100;
    private static final int MAX_SUGGESTIONS = 10;
    /// Tags are user contributed and occasionally carry a pasted URL or a sentence.
    private static final int MAX_SUGGESTION_LENGTH = 40;

    private final RadioBrowserGateway radioBrowserGateway;

    public RadioService(RadioBrowserGateway radioBrowserGateway) {
        this.radioBrowserGateway = radioBrowserGateway;
    }

    /// Searches names and tags, because the two answer different intents: "France
    /// Inter" is a name, "jazz" is a genre. A station tagged jazz but named "Blue
    /// Note Radio" is invisible to a name-only search.
    public List<RadiofyStationDto> searchStations(String q, int limit, int offset) {
        String query = q == null ? "" : q.trim();
        if (query.isBlank()) {
            return List.of();
        }

        int safeLimit = clampLimit(limit);
        int safeOffset = Math.max(offset, 0);
        // The two sources are merged before paging, so each has to cover the whole
        // window rather than just its own slice of it.
        int fetchSize = clampLimit(safeLimit + safeOffset);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<List<RadioBrowserStationDto>> byName = executor.submit(() ->
                    radioBrowserGateway.searchByName(query, fetchSize, 0));

            Future<List<RadioBrowserStationDto>> byTag = executor.submit(() ->
                    radioBrowserGateway.browse(null, query, fetchSize, 0));

            List<RadioBrowserStationDto> nameHits = awaitOrNull("name search", byName);
            List<RadioBrowserStationDto> tagHits = awaitOrNull("tag search", byTag);

            // One source failing still gives usable results; both failing is a real
            // outage and has to surface rather than look like "no station found".
            if (nameHits == null && tagHits == null) {
                throw new RadioBrowserUnavailableException("Radio Browser is unreachable.");
            }

            return mergeStations(
                    nameHits == null ? List.of() : nameHits,
                    tagHits == null ? List.of() : tagHits,
                    safeLimit,
                    safeOffset
            );
        }
    }

    public List<RadiofyStationDto> browseStations(String countryCode, String tag, int limit, int offset) {
        int safeLimit = clampLimit(limit);
        int safeOffset = Math.max(offset, 0);

        // Browsing hits the same directory, so it carries the same duplicates.
        return StationDeduplicator.deduplicate(
                        radioBrowserGateway.browse(countryCode, tag, safeLimit, safeOffset))
                .stream()
                .map(RadioStationMapper::toRadiofyDto)
                .toList();
    }

    /// Search terms to offer while the user types, most popular first.
    ///
    /// Two sources: tags, because "jazz" is a whole category rather than a word in a
    /// name, and station names, because that is what people mostly type.
    public List<String> suggest(String q, int limit) {
        String query = q == null ? "" : q.trim();
        if (query.isBlank()) {
            return List.of();
        }

        int safeLimit = clampSuggestions(limit);

        // Both sources hit Radio Browser, so running them in sequence cost the sum of
        // the two round trips. Virtual threads fit a blocking wait and need no pool
        // sizing; the executor closes once both have answered.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Each source is optional: answering with what one of them found is far
            // more useful than failing, and Radio Browser is not always reachable.
            Future<List<String>> tags = executor.submit(() ->
                    fromSource("tags", () ->
                            radioBrowserGateway.searchTags(query, safeLimit).stream()
                                    .map(RadioBrowserTagDto::name)
                                    .toList()));

            Future<List<String>> stationNames = executor.submit(() ->
                    fromSource("stations", () ->
                            radioBrowserGateway.searchByName(query, safeLimit, 0).stream()
                                    .map(RadioBrowserStationDto::name)
                                    .toList()));

            return merge(await(tags), await(stationNames), safeLimit);
        }
    }

    public String resolveStreamUrl(String stationUuid) {
        String safeStationUuid = stationUuid == null ? "" : stationUuid.trim();
        if (safeStationUuid.isBlank()) {
            throw new IllegalArgumentException("stationUuid is required.");
        }

        return radioBrowserGateway.resolveStreamUrl(safeStationUuid);
    }

    /// Alternates the two sources rather than listing names first: on "jazz" the name
    /// matches alone would fill the page and bury every station that is tagged jazz
    /// without saying so in its name.
    private List<RadiofyStationDto> mergeStations(
            List<RadioBrowserStationDto> nameHits,
            List<RadioBrowserStationDto> tagHits,
            int limit,
            int offset
    ) {
        Set<String> seenIds = new LinkedHashSet<>();
        List<RadioBrowserStationDto> interleaved = new ArrayList<>();

        int rounds = Math.max(nameHits.size(), tagHits.size());
        for (int i = 0; i < rounds; i++) {
            if (i < nameHits.size()) {
                addStation(nameHits.get(i), seenIds, interleaved);
            }
            if (i < tagHits.size()) {
                addStation(tagHits.get(i), seenIds, interleaved);
            }
        }

        // Paging comes after collapsing duplicates, otherwise a page of ten could
        // shrink to four once the copies of one station are folded together.
        return StationDeduplicator.deduplicate(interleaved).stream()
                .skip(offset)
                .limit(limit)
                .map(RadioStationMapper::toRadiofyDto)
                .toList();
    }

    /// Only removes the exact same entry appearing in both sources. Recognising
    /// several entries as one station is `StationDeduplicator`'s job.
    private void addStation(RadioBrowserStationDto station, Set<String> seenIds, List<RadioBrowserStationDto> collected) {
        if (station == null || station.stationuuid() == null || !seenIds.add(station.stationuuid())) {
            return;
        }

        collected.add(station);
    }

    /// Returns null when the call itself failed, which `searchStations` needs to tell
    /// apart from a source that simply had nothing to offer.
    private <T> List<T> awaitOrNull(String name, Future<List<T>> task) {
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            log.warn("Search source '{}' unavailable: {}", name, e.getCause().getMessage());
            return null;
        }
    }

    /// Alternates between the sources so a broad category and a concrete station both
    /// get a slot, rather than three variations of the same tag filling the list.
    private List<String> merge(List<String> tags, List<String> stationNames, int limit) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> suggestions = new ArrayList<>();

        int rounds = Math.max(tags.size(), stationNames.size());
        for (int i = 0; i < rounds && suggestions.size() < limit; i++) {
            if (i < tags.size()) {
                addIfUsable(tags.get(i), seen, suggestions, limit);
            }
            if (i < stationNames.size()) {
                addIfUsable(stationNames.get(i), seen, suggestions, limit);
            }
        }

        return List.copyOf(suggestions);
    }

    private void addIfUsable(String term, Set<String> seen, List<String> suggestions, int limit) {
        if (term == null || suggestions.size() >= limit) {
            return;
        }

        String trimmed = term.trim();
        if (!isUsable(trimmed) || !seen.add(trimmed.toLowerCase(Locale.ROOT))) {
            return;
        }

        suggestions.add(trimmed);
    }

    private boolean isUsable(String term) {
        return !term.isBlank()
                && term.length() <= MAX_SUGGESTION_LENGTH
                && !term.toLowerCase(Locale.ROOT).contains("http");
    }

    /// Each source already answers with an empty list when the call itself fails, so
    /// reaching a failure here means the wait went wrong rather than the request.
    private List<String> await(Future<List<String>> task) {
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (ExecutionException e) {
            log.warn("Suggestion task failed: {}", e.getCause().getMessage());
            return List.of();
        }
    }

    private <T> List<T> fromSource(String name, Supplier<List<T>> source) {
        try {
            return source.get();
        } catch (RuntimeException e) {
            log.warn("Suggestion source '{}' unavailable: {}", name, e.getMessage());
            return List.of();
        }
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private int clampSuggestions(int limit) {
        return Math.max(1, Math.min(limit, MAX_SUGGESTIONS));
    }
}
