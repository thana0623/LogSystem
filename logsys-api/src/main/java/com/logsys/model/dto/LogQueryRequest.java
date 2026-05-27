package com.logsys.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LogQueryRequest {

    private String serviceName;
    private String level;
    private String keyword;
    private String traceId;

    @NotBlank(message = "start_time is required")
    private String startTime;

    @NotBlank(message = "end_time is required")
    private String endTime;

    private Integer page = 1;

    @Min(value = 1, message = "pageSize must be at least 1")
    @Max(value = 1000, message = "pageSize must not exceed 1000")
    private Integer pageSize = 50;

    @Pattern(regexp = "^(asc|desc)$", message = "sort must be 'asc' or 'desc'")
    private String sort = "desc";
}
