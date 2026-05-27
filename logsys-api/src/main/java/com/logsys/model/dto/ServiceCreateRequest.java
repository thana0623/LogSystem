package com.logsys.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ServiceCreateRequest {

    @NotBlank(message = "name is required")
    @Pattern(regexp = "^[a-z][a-z0-9-]{2,63}$",
             message = "name must be lowercase letters, digits, hyphens, 3-64 chars")
    private String name;

    private String description;
    private String language;
}
