package com.logsys.dao.clickhouse;

import com.logsys.model.vo.LogEntryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface LogMapper {

    List<LogEntryVO> queryLogs(@Param("params") Map<String, Object> params);

    long countLogs(@Param("params") Map<String, Object> params);
}
