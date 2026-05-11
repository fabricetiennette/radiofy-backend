package io.github.fabricetiennette.radiofy.backend.radio.controller;

import io.github.fabricetiennette.radiofy.backend.radio.dto.RadiofyStationDto;
import io.github.fabricetiennette.radiofy.backend.radio.service.RadioService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/v1/radio/stations")
public class StationsController {
    private final RadioService service;

    public StationsController(RadioService service) {
        this.service = service;
    }

    @GetMapping
    public List<RadiofyStationDto> browse(
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset
    ) {
        return service.browseStations(countryCode, tag, limit, offset);
    }

    @GetMapping("/search")
    public List<RadiofyStationDto> search(
            @RequestParam @NotBlank String q,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset
    ) {
        return service.searchStations(q, limit, offset);
    }

    @GetMapping("/{stationUuid}/stream-url")
    public Map<String, String> streamUrl(
            @PathVariable @NotBlank String stationUuid
    ) {
        return Map.of("streamUrl", service.resolveStreamUrl(stationUuid));
    }
}
