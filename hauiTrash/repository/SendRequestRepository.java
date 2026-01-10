package com.example.hauiTrash.repository;

import com.example.hauiTrash.entity.SendRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SendRequestRepository extends JpaRepository<SendRequest, String> {

}
