package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name="trash_item_mappings",
        uniqueConstraints = @UniqueConstraint(name="uk_item_active", columnNames={"trash_item_id","is_active"})
)
public class TrashItemMapping {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="trash_item_id", nullable = false)
    private TrashItem trashItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="trash_type_id")
    private TrashType trashType;

    @Column(name="mapping_source", nullable = false, length = 30)
    private String mappingSource; // MANUAL/AI_SUGGESTED/HYBRID

    @Column(name="mapping_confidence")
    private Float mappingConfidence;

    @Column(name="is_active", nullable = false)
    private Boolean isActive;

    @Column(name="created_at")
    private Instant createdAt;

    @Column(name="updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (mappingSource == null) mappingSource = "MANUAL";
        if (isActive == null) isActive = true;
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }
}