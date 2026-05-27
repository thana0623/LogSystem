package com.logsys.controller;

import com.logsys.common.ApiResponse;
import com.logsys.service.StatsService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
@Validated
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(
            @RequestParam(defaultValue = "24h")
            @Pattern(regexp = "^\\d+[hdm]$", message = "range format: 1h, 24h, 7d, 30d") String range) {
        return ApiResponse.ok(statsService.overview(range));
    }
}
