package com.logsys.controller;

import com.logsys.common.ApiResponse;
import com.logsys.model.dto.ServiceCreateRequest;
import com.logsys.model.entity.ServiceEntity;
import com.logsys.model.vo.ServiceVO;
import com.logsys.service.ServiceManageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
@Validated
public class ServiceController {

    private final ServiceManageService serviceManageService;

    @GetMapping
    public ApiResponse<Map<String, List<ServiceVO>>> list(
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(Map.of("items",
                serviceManageService.list(status).stream().map(ServiceVO::from).toList()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ServiceVO> create(@Valid @RequestBody ServiceCreateRequest request) {
        return ApiResponse.ok(ServiceVO.from(serviceManageService.create(request)));
    }

    @GetMapping("/{name}")
    public ApiResponse<ServiceVO> detail(
            @PathVariable String name,
            @RequestParam(defaultValue = "1h")
            @Pattern(regexp = "^\\d+[hdm]$", message = "range format: 1h, 24h, 7d, 30d") String range) {
        return ApiResponse.ok(ServiceVO.from(serviceManageService.detail(name, range)));
    }
}
