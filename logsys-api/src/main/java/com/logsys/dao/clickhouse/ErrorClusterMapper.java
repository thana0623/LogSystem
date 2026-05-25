package com.logsys.dao.clickhouse;

import com.logsys.model.vo.ErrorClusterVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ErrorClusterMapper {

    List<ErrorClusterVO> queryTopErrors(@Param("params") Map<String, Object> params);

    List<Map<String, Object>> queryClusters(@Param("params") Map<String, Object> params);

    Map<String, Object> queryClusterSummary(@Param("params") Map<String, Object> params);
}
