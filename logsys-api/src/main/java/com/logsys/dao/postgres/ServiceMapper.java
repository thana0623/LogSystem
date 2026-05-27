package com.logsys.dao.postgres;

import com.logsys.model.entity.ServiceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ServiceMapper {

    List<ServiceEntity> findAll();

    int count();

    ServiceEntity findByName(@Param("name") String name);

    int insert(@Param("service") ServiceEntity service);

    int deleteByName(@Param("name") String name);
}
