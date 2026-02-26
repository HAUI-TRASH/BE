package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.AiRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AiRequestRepository extends JpaRepository<AiRequest, Integer> {
    @Query("select ar from AiRequest ar left join fetch ar.detections where ar.id = :id")
    Optional<AiRequest> findByIdWithDetections(@Param("id") Integer id);
}
