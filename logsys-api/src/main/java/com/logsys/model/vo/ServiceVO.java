package com.logsys.model.vo;

import com.logsys.model.entity.ServiceEntity;
import lombok.Data;

import java.time.Instant;

@Data
public class ServiceVO {

    private Long id;
    private String name;
    private String displayName;
    private String description;
    private String language;
    private Instant createdAt;

    public static ServiceVO from(ServiceEntity entity) {
        ServiceVO vo = new ServiceVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setDisplayName(entity.getDisplayName());
        vo.setDescription(entity.getDescription());
        vo.setLanguage(entity.getLanguage());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
