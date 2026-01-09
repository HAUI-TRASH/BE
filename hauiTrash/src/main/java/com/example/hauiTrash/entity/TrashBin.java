package com.example.hauiTrash.entity;


import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(name = "trash_bins")
public class TrashBin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 50, unique = true)
    private String code;

    @Column(name = "name", length = 120)
    private String name;

    @Column(name = "color", length = 30)
    private String color;

    @Lob
    @Column(name = "description")
    private String description;

    @OneToMany(mappedBy = "bin", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<TrashTypeBinMapping> typeMappings = new HashSet<>();
}

