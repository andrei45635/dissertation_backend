package com.msadetector.entity;

import com.msadetector.enums.HttpMethod;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "endpoints", indexes = {
    @Index(name = "idx_endpoint_microservice", columnList = "microservice_id"),
    @Index(name = "idx_endpoint_path", columnList = "path")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Endpoint extends BaseEntity {

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", nullable = false)
    private HttpMethod httpMethod;

    @Size(max = 255)
    @Column(name = "controller_class")
    private String controllerClass;

    @Size(max = 255)
    @Column(name = "method_name")
    private String methodName;

    @Size(max = 100)
    @Column(name = "api_version")
    private String apiVersion;

    @Column(name = "has_versioning")
    @Builder.Default
    private boolean hasVersioning = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "microservice_id", nullable = false)
    private Microservice microservice;
}
