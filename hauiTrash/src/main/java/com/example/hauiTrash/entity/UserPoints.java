package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(name = "user_points")
public class UserPoints {
    @Id
    @Column(name = "account_id")
    private Integer accountId;

    @Column(name = "total_points")
    private Integer totalPoints;

    @Column(name = "level")
    private Integer level;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (totalPoints == null) totalPoints = 0;
        if (level == null) level = 1;
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}