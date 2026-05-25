package com.logsys.service.impl;

import com.logsys.common.BizException;
import com.logsys.common.ErrorCode;
import com.logsys.dao.postgres.ServiceMapper;
import com.logsys.model.dto.ServiceCreateRequest;
import com.logsys.model.entity.ServiceEntity;
import com.logsys.service.ServiceManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceManageServiceImpl implements ServiceManageService {

    private final ServiceMapper serviceMapper;

    @Override
    public List<ServiceEntity> list(String status) {
        // Phase 1: return all services from PostgreSQL
        // Phase 2: enrich with ClickHouse health metrics and filter by status
        return serviceMapper.findAll();
    }

    @Override
    @Transactional
    public ServiceEntity create(ServiceCreateRequest request) {
        // Check duplicate
        ServiceEntity existing = serviceMapper.findByName(request.getName());
        if (existing != null) {
            throw BizException.conflict("Service", request.getName());
        }

        ServiceEntity entity = new ServiceEntity();
        entity.setName(request.getName());
        entity.setDescription(request.getDescription() != null ? request.getDescription() : "");
        entity.setLanguage(request.getLanguage() != null ? request.getLanguage() : "other");

        serviceMapper.insert(entity);
        return serviceMapper.findByName(request.getName());
    }

    @Override
    public ServiceEntity detail(String name, String range) {
        ServiceEntity entity = serviceMapper.findByName(name);
        if (entity == null) {
            throw BizException.notFound("Service", name);
        }
        // Phase 2: enrich with ClickHouse metrics (log_volume_trend, top_errors, recent_logs)
        return entity;
    }
}
