package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(name = "ai_requests")
public class AiRequest {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Lob
    @Column(name = "cloudinary_url")
    private String cloudinaryUrl;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "status", length = 20)
    private String status; // PROCESSING|DONE|ERROR...

    @Column(name = "model_yolo", length = 50)
    private String modelYolo;

    @Column(name = "model_cls", length = 50)
    private String modelCls;

    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @OneToMany(mappedBy = "aiRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Detection> detections = new ArrayList<>();

    @OneToOne(mappedBy = "aiRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Classification classification;
}
