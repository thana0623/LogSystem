package com.logsys.model.vo;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class ErrorClusterVO {

    private Integer rank;
    private String signature;
    private String exceptionType;
    private String normalizedMsg;
    private String sampleMessage;
    private String serviceName;
    private Long totalCount;
    private Instant firstSeen;
    private Instant lastSeen;
    private String trend;
    private List<String> affectedServices;
}
