package com.msadetector.entity;

import com.msadetector.enums.Severity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "code_smells", indexes = {
    @Index(name = "idx_codesmell_microservice", columnList = "microservice_id"),
    @Index(name = "idx_codesmell_type", columnList = "smell_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeSmell extends BaseEntity {

    @NotBlank
    @Size(max = 100)
    @Column(name = "smell_type", nullable = false)
    private String smellType;

    @Size(max = 500)
    private String description;

    @Size(max = 500)
    @Column(name = "file_path")
    private String filePath;

    @Size(max = 255)
    @Column(name = "class_name")
    private String className;

    @Size(max = 255)
    @Column(name = "method_name")
    private String methodName;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Severity severity = Severity.MEDIUM;

    @Size(max = 100)
    @Column(name = "source_tool")
    @Builder.Default
    private String sourceTool = "DesigniteJava";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "microservice_id", nullable = false)
    private Microservice microservice;
}
