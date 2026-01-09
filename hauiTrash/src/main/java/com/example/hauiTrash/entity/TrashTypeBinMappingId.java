package com.example.hauiTrash.entity;

import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class TrashTypeBinMappingId implements Serializable {
    private Long trashTypeId;
    private Long binId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrashTypeBinMappingId that)) return false;
        return Objects.equals(trashTypeId, that.trashTypeId) &&
                Objects.equals(binId, that.binId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trashTypeId, binId);
    }
}
