package com.example.hauiTrash.dto;

import lombok.*;

import java.time.Instant;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendRequestResponse {
    private String id;
    private String cloudinaryUrl;
    private String originalName;
    private Instant startedAt;
    private Instant finishedAt;
}
