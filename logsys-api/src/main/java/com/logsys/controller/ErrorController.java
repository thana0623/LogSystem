package com.logsys.controller;

import com.logsys.common.ApiResponse;
import com.logsys.model.vo.ErrorClusterVO;
import com.logsys.service.ErrorClusterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/errors")
@RequiredArgsConstructor
public class ErrorController {

    private final ErrorClusterService errorClusterService;

    @GetMapping("/top")
    public ApiResponse<Map<String, List<ErrorClusterVO>>> top(
            @RequestParam(defaultValue = "24h") String range,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String trend) {
        return ApiResponse.ok(Map.of("items", errorClusterService.top(range, limit, serviceName, trend)));
    }

    @GetMapping("/clusters")
    public ApiResponse<Map<String, Object>> clusters(
            @RequestParam(defaultValue = "7d") String range,
            @RequestParam(required = false) String serviceName) {
        return ApiResponse.ok(errorClusterService.clusters(range, serviceName));
    }
}
