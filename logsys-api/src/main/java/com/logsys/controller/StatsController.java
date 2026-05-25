package com.logsys.controller;

import com.logsys.common.ApiResponse;
import com.logsys.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(
            @RequestParam(defaultValue = "24h") String range) {
        return ApiResponse.ok(statsService.overview(range));
    }
}
