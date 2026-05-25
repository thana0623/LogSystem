package com.logsys.service;

import com.logsys.model.dto.ServiceCreateRequest;
import com.logsys.model.entity.ServiceEntity;

import java.util.List;

public interface ServiceManageService {
    List<ServiceEntity> list(String status);
    ServiceEntity create(ServiceCreateRequest request);
    ServiceEntity detail(String name, String range);
}
