package com.logsys.controller;

import com.logsys.common.ApiResponse;
import com.logsys.model.dto.ServiceCreateRequest;
import com.logsys.model.entity.ServiceEntity;
import com.logsys.service.ServiceManageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceManageService serviceManageService;

    @GetMapping
    public ApiResponse<Map<String, List<ServiceEntity>>> list(
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(Map.of("items", serviceManageService.list(status)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ServiceEntity> create(@Valid @RequestBody ServiceCreateRequest request) {
        return ApiResponse.ok(serviceManageService.create(request));
    }

    @GetMapping("/{name}")
    public ApiResponse<ServiceEntity> detail(
            @PathVariable String name,
            @RequestParam(defaultValue = "1h") String range) {
        return ApiResponse.ok(serviceManageService.detail(name, range));
    }
}
