package io.github.fabricetiennette.radiofy.backend.radio.controller;

import io.github.fabricetiennette.radiofy.backend.radio.dto.RadiofyStationDto;
import io.github.fabricetiennette.radiofy.backend.radio.service.RadioService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/stations")
public class StationsController {
    private final RadioService service;

    public StationsController(RadioService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public List<RadiofyStationDto> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return service.searchStations(q, limit);
    }
}
