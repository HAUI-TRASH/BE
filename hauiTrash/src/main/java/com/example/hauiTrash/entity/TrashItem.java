package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "trash_items")
public class TrashItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 120)
    private String label;

    @Column(name = "label_display", length = 180)
    private String labelDisplay;

    @Column(nullable = false, length = 30)
    private String status; // ACTIVE / NEED_REVIEW / DISABLED

    @Column(name="created_at")
    private Instant createdAt;

    @Column(name="updated_at")
    private Instant updatedAt;

    /*
     Visual RAG: alias label
     ví dụ:
     coke_can
     soda_can
     metal_can
     -> map về "can"
     */
    @OneToMany(mappedBy = "trashItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrashItemAlias> aliases;

    /*
     mapping sang loại rác
     */
    @OneToMany(mappedBy = "trashItem", cascade = CascadeType.ALL)
    private List<TrashItemMapping> mappings;

    /*
     knowledge của rác
     */
    @OneToMany(mappedBy = "trashItem", cascade = CascadeType.ALL)
    private List<TrashItemKnowledge> knowledges;


    @PrePersist
    void prePersist() {
        if (status == null) {
            status = "ACTIVE";
        }

        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}