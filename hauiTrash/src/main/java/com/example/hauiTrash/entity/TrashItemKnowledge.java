package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name="trash_item_knowledge")
public class TrashItemKnowledge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="trash_item_id", nullable = false)
    private TrashItem trashItem;

    private String material;

    @Lob private String note;
    @Lob private String action;
    @Lob private String impact;
    @Lob private String toxicity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="safe_steps_json")
    private List<String> safeSteps;

    @Column(nullable = false, length = 30)
    private String source; // AI/MANUAL

    @Column(length = 80)
    private String model;

    @Column(nullable = false)
    private Integer version;

    @Column(name="is_active", nullable = false)
    private Boolean isActive;

    @Column(name="created_at")
    private Instant createdAt;

    @Column(name="updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (source == null) source = "AI";
        if (version == null) version = 1;
        if (isActive == null) isActive = true;
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}