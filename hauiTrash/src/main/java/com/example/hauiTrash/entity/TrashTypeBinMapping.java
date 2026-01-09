package com.example.hauiTrash.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(name = "trash_type_bin_mapping")
@IdClass(TrashTypeBinMappingId.class)
public class TrashTypeBinMapping {

    @Id
    @Column(name = "trash_type_id")
    private Long trashTypeId;

    @Id
    @Column(name = "bin_id")
    private Long binId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trash_type_id", insertable = false, updatable = false)
    private TrashType trashType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bin_id", insertable = false, updatable = false)
    private TrashBin bin;
}
