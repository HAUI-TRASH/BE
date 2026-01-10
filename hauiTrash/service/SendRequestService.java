package com.example.hauiTrash.service;

import com.example.hauiTrash.dto.SendRequestResponse;
import com.example.hauiTrash.entity.SendRequest;
import com.example.hauiTrash.entity.Account;
import com.example.hauiTrash.repository.SendRequestRepository;
import com.example.hauiTrash.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

@Service
public class SendRequestService {

    @Autowired
    private SendRequestRepository repository;

    @Autowired
    private AccountRepository accountRepository;

    private final Path uploadDir = Paths.get("uploads");

    public SendRequestResponse createRequest(MultipartFile file, String cloudinaryUrl, String accountId) throws IOException {
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);

        String finalUrl = cloudinaryUrl;
        String originalName = null;

        if (file != null && !file.isEmpty()) {
            originalName = file.getOriginalFilename();
            String filename = UUID.randomUUID().toString() + "_" + originalName;
            Path dest = uploadDir.resolve(filename);
            file.transferTo(dest.toFile());
            finalUrl = dest.toAbsolutePath().toString();
        }

        // Resolve Account from accountId (nullable - guest user case)
        Account account = null;
        if (accountId != null && !accountId.isBlank()) {
            try {
                account = accountRepository.findById(Integer.parseInt(accountId)).orElse(null);
            } catch (NumberFormatException e) {
                account = null;
            }
        }

        SendRequest sr = SendRequest.builder()
                .id(UUID.randomUUID().toString())
                .account(account)  // FK to Account (nullable)
                .cloudinaryUrl(finalUrl)
                .originalName(originalName)
                .startedAt(Instant.now())
                .finishedAt(null)
                .build();

        SendRequest saved = repository.save(sr);

        return SendRequestResponse.builder()
                .id(saved.getId())
                .cloudinaryUrl(saved.getCloudinaryUrl())
                .originalName(saved.getOriginalName())
                .startedAt(saved.getStartedAt())
                .finishedAt(saved.getFinishedAt())
                .build();
    }
}
