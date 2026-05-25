package com.logsys.model.dto;

import jakarta.validation.constraints.NotBlank;
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
    private Integer pageSize = 50;
    private String sort = "desc";
}
