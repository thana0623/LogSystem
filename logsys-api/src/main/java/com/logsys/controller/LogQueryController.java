package com.logsys.controller;

import com.logsys.common.ApiResponse;
import com.logsys.model.dto.LogQueryRequest;
import com.logsys.model.vo.LogEntryVO;
import com.logsys.model.vo.PageResult;
import com.logsys.service.LogQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogQueryController {

    private final LogQueryService logQueryService;

    @PostMapping("/query")
    public ApiResponse<PageResult<LogEntryVO>> query(@Valid @RequestBody LogQueryRequest request) {
        return ApiResponse.ok(logQueryService.query(request));
    }
}
