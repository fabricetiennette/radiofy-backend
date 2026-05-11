package io.github.fabricetiennette.radiofy.backend.radio.gateway;

import io.github.fabricetiennette.radiofy.backend.radio.dto.RadioBrowserStationDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@Component
public class RadioBrowserGateway {

    private final RestClient restClient;

    // ⚠️ pas encore de découverte dynamique des serveurs Radio
    //
    //⚠️ pas encore de fallback automatique si base-url tombe

    public RadioBrowserGateway(
            @Value("${radio-browser.base-url}") String baseUrl,
            @Value("${radio-browser.user-agent}") String userAgent
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
    }

    public List<RadioBrowserStationDto> searchByName(String name, int limit, int offset) {
        List<RadioBrowserStationDto> stations = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/json/stations/search")
                        .queryParam("name", name)
                        .queryParam("limit", limit)
                        .queryParam("offset", offset)
                        .queryParam("hidebroken", true)
                        .queryParam("order", "clickcount")
                        .queryParam("reverse", true)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return Optional.ofNullable(stations).orElse(List.of());
    }

    public List<RadioBrowserStationDto> browse(String countryCode, String tag, int limit, int offset) {
        List<RadioBrowserStationDto> stations = restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
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
                .body(new ParameterizedTypeReference<>() {
                });
        return Optional.ofNullable(stations).orElse(List.of());
    }

    public String resolveStreamUrl(String stationUuid) {
        RadioBrowserClickResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/json/url/{stationUuid}")
                        .build(stationUuid))
                .retrieve()
                .body(RadioBrowserClickResponse.class);

        if (response == null || response.url() == null || response.url().isBlank()) {
            throw new IllegalStateException("Radio Browser did not return a stream URL.");
        }

        return response.url();
    }

    private record RadioBrowserClickResponse(String url) {
    }
}
