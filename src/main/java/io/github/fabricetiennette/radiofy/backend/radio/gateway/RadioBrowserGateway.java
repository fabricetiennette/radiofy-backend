package io.github.fabricetiennette.radiofy.backend.radio.gateway;

import io.github.fabricetiennette.radiofy.backend.config.RadioCacheConfig;
import io.github.fabricetiennette.radiofy.backend.radio.dto.RadioBrowserStationDto;
import io.github.fabricetiennette.radiofy.backend.radio.dto.RadioBrowserTagDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
@Component
public class RadioBrowserGateway {

    /// Each failed attempt costs a full read timeout, so trying the whole fleet would
    /// make a suggestion arrive long after the user stopped typing.
    private static final int MAX_ATTEMPTS = 2;

    private final RestClient restClient;
    private final RadioBrowserServerRegistry serverRegistry;

    public RadioBrowserGateway(
            RadioBrowserServerRegistry serverRegistry,
            @Value("${radio-browser.user-agent}") String userAgent,
            @Value("${radio-browser.connect-timeout:2s}") Duration connectTimeout,
            @Value("${radio-browser.read-timeout:3s}") Duration readTimeout
    ) {
        this.serverRegistry = serverRegistry;

        // Radio Browser drops out often enough that the default wait — around fifteen
        // seconds — turns a hiccup into a hung request. Failing fast is the right
        // trade here: a suggestion is worthless once the user has stopped typing.
        var requestFactory = new ReactorClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        // No base URL: the host is chosen per attempt from the discovered fleet.
        this.restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .requestFactory(requestFactory)
                .build();
    }

    @Cacheable(cacheNames = RadioCacheConfig.STATIONS_CACHE, key = "'name:' + #name + ':' + #limit + ':' + #offset")
    public List<RadioBrowserStationDto> searchByName(String name, int limit, int offset) {
        List<RadioBrowserStationDto> stations = call("search by name", host -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host(host)
                        .path("/json/stations/search")
                        .queryParam("name", name)
                        .queryParam("limit", limit)
                        .queryParam("offset", offset)
                        .queryParam("hidebroken", true)
                        .queryParam("order", "clickcount")
                        .queryParam("reverse", true)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<RadioBrowserStationDto>>() {
                }));
        return Optional.ofNullable(stations).orElse(List.of());
    }

    @Cacheable(cacheNames = RadioCacheConfig.STATIONS_CACHE, key = "'browse:' + #countryCode + ':' + #tag + ':' + #limit + ':' + #offset")
    public List<RadioBrowserStationDto> browse(String countryCode, String tag, int limit, int offset) {
        List<RadioBrowserStationDto> stations = call("browse", host -> restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .scheme("https")
                            .host(host)
                            .path("/json/stations/search")
                            .queryParam("limit", limit)
                            .queryParam("offset", offset)
                            .queryParam("hidebroken", true)
                            .queryParam("order", "clickcount")
                            .queryParam("reverse", true);

                    if (countryCode != null && !countryCode.isBlank()) {
                        builder.queryParam("countrycode", countryCode.trim().toUpperCase());
                    }

                    if (tag != null && !tag.isBlank()) {
                        builder.queryParam("tag", tag.trim());
                    }

                    return builder.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<List<RadioBrowserStationDto>>() {
                }));
        return Optional.ofNullable(stations).orElse(List.of());
    }

    /// Tags matching a prefix, most used first. Without the explicit ordering the
    /// endpoint answers alphabetically, which puts "1.fm jazz" ahead of "jazz".
    @Cacheable(cacheNames = RadioCacheConfig.TAGS_CACHE, key = "#filter + ':' + #limit")
    public List<RadioBrowserTagDto> searchTags(String filter, int limit) {
        List<RadioBrowserTagDto> tags = call("search tags", host -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host(host)
                        .path("/json/tags/{filter}")
                        .queryParam("limit", limit)
                        .queryParam("order", "stationcount")
                        .queryParam("reverse", true)
                        .queryParam("hidebroken", true)
                        .build(filter))
                .retrieve()
                .body(new ParameterizedTypeReference<List<RadioBrowserTagDto>>() {
                }));
        return Optional.ofNullable(tags).orElse(List.of());
    }

    /// Deliberately not cached: Radio Browser asks that every click reach
    /// `/json/url` so it can mark stations as popular. Serving this from a cache
    /// would silently stop feeding the directory that we read from.
    public String resolveStreamUrl(String stationUuid) {
        RadioBrowserClickResponse response = call("resolve stream url", host -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host(host)
                        .path("/json/url/{stationUuid}")
                        .build(stationUuid))
                .retrieve()
                .body(RadioBrowserClickResponse.class));

        if (response == null || response.url() == null || response.url().isBlank()) {
            throw new RadioBrowserUnavailableException("Radio Browser did not return a stream URL.");
        }

        return response.url();
    }

    /// Single funnel for every outbound call: it walks the discovered servers until
    /// one answers, and turns a total failure into one known exception rather than
    /// whatever the HTTP client happened to throw.
    private <T> T call(String operation, Function<String, T> requestForHost) {
        List<String> servers = serverRegistry.servers();
        RestClientException lastFailure = null;

        for (String host : servers.subList(0, Math.min(servers.size(), MAX_ATTEMPTS))) {
            try {
                return requestForHost.apply(host);
            } catch (RestClientException e) {
                log.warn("Radio Browser server {} failed on {}: {}", host, operation, e.getMessage());
                lastFailure = e;
            }
        }

        throw new RadioBrowserUnavailableException(
                "Radio Browser is unavailable (" + operation + ").", lastFailure);
    }

    private record RadioBrowserClickResponse(String url) {
    }
}
