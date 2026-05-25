package com.logsys.service;

import com.logsys.model.vo.ErrorClusterVO;

import java.util.List;
import java.util.Map;

public interface ErrorClusterService {
    List<ErrorClusterVO> top(String range, int limit, String serviceName, String trend);
    Map<String, Object> clusters(String range, String serviceName);
}
