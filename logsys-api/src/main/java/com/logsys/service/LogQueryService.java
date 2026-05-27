package com.logsys.service;

import com.logsys.model.dto.LogQueryRequest;
import com.logsys.model.vo.LogEntryVO;
import com.logsys.model.vo.PageResult;

public interface LogQueryService {
    PageResult<LogEntryVO> query(LogQueryRequest request);
}
