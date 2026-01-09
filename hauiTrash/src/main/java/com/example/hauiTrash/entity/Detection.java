package com.example.hauiTrash.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(name = "detections")
public class Detection {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_request_id")
    private AiRequest aiRequest;

    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "confidence")
    private Float confidence;

    @Column(name = "x_min")
    private Float xMin;

    @Column(name = "y_min")
    private Float yMin;

    @Column(name = "x_max")
    private Float xMax;

    @Column(name = "y_max")
    private Float yMax;
}
