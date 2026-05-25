package com.logsys.model.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class ServiceEntity {

    private Long id;
    private String name;
    private String displayName;
    private String description;
    private String language;
    private String repositoryUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
