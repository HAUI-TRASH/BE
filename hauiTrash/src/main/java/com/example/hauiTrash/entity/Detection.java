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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", length = 36, nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_request_id")
    private AiRequest aiRequest;

    @Column(name = "label", length = 100)
    private String label;
    @Column(name = "labelDisplay", length = 100)
    private String labelDisplay;    @Column(name = "confidence")
    private Float confidence;

    @Column(name = "annotatedUrl")
    private String annotatedUrl;

}
