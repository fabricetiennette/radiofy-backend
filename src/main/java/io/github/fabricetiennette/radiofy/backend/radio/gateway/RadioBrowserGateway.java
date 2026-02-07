package io.github.fabricetiennette.radiofy.backend.radio.gateway;

import io.github.fabricetiennette.radiofy.backend.radio.dto.RadioBrowserStationDto;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class RadioBrowserGateway {

    private final RestClient restClient;

    public RadioBrowserGateway() {
        this.restClient = RestClient.builder()
                .baseUrl("https://de1.api.radio-browser.info")
                .build();
    }

    public List<RadioBrowserStationDto> searchByName(String name, int limit) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/json/stations/search")
                        .queryParam("name", name)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
