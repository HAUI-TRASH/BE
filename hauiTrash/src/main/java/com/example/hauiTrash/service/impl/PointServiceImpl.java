package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.LeaderboardDTO;
import com.example.hauiTrash.dto.PointHistoryDTO;
import com.example.hauiTrash.dto.UserPointsDTO;
import com.example.hauiTrash.entity.Account;
import com.example.hauiTrash.entity.PointActionType;
import com.example.hauiTrash.entity.PointHistory;
import com.example.hauiTrash.entity.UserPoints;
import com.example.hauiTrash.repository.AccountRepository;
import com.example.hauiTrash.repository.PointHistoryRepository;
import com.example.hauiTrash.repository.UserPointsRepository;
import com.example.hauiTrash.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PointServiceImpl implements PointService {

    private final UserPointsRepository userPointsRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final AccountRepository accountRepository;

    private static final int LEVEL_UP_POINTS = 100; // 100 điểm = 1 level
    @Override
    public UserPointsDTO getMyPoints() {
        // Lấy trực tiếp từ JwtFilter
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Account account = (Account) auth.getPrincipal();
        return getUserPoints(account.getId());
    }
    @Override
    @Transactional(readOnly = true)
    public UserPointsDTO getUserPoints(Integer accountId) {
        UserPoints up = userPointsRepository.findByAccountId(accountId)
                .orElse(UserPoints.builder()
                        .accountId(accountId)
                        .totalPoints(0)
                        .build());


        return UserPointsDTO.builder()
                .totalPoints(up.getTotalPoints())
                .build();
    }
    @Override
    @Transactional(readOnly = true)
    public List<PointHistoryDTO> getPointHistory(Integer accountId) {
        return pointHistoryRepository.findByAccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaderboardDTO> getLeaderboard(Pageable pageable) {
        List<UserPoints> topUsers = userPointsRepository.findTopLeaderboard(pageable);
        List<LeaderboardDTO> result = new java.util.ArrayList<>();
        int rank = 1;
        for (UserPoints up : topUsers) {
            var account = accountRepository.findById(up.getAccountId()).orElse(null);
            result.add(LeaderboardDTO.builder()
                    .rank(rank++)
                    .accountId(up.getAccountId())
                    .fullName(account != null ? account.getFullName() : "Unknown")
                    .avatarUrl(account != null ? account.getAvatarUrl() : null)
                    .totalPoints(up.getTotalPoints())
                    .build());
        }
        return result;
    }

    @Override
    @Transactional
    public void addPoints(Integer accountId, String actionType, String referenceId, String description) {
        PointActionType action;
        try {
            action = PointActionType.valueOf(actionType);
        } catch (IllegalArgumentException e) {
            return;
        }

        int pointsToAdd = action.getPoints();

        // Kiểm tra duplicate (ví dụ: không nhận 2 lần cho cùng 1 story)
        if (referenceId != null) {
            boolean exists = pointHistoryRepository.existsByAccountIdAndActionTypeAndReferenceId(
                    accountId, actionType, referenceId);
            if (exists) {
                return;
            }
        }

        // Lưu lịch sử
        PointHistory history = PointHistory.builder()
                .accountId(accountId)
                .points(pointsToAdd)
                .actionType(actionType)
                .referenceId(referenceId)
                .description(description)
                .build();
        pointHistoryRepository.save(history);

        // Cập nhật hoặc tạo mới UserPoints
        UserPoints up = userPointsRepository.findByAccountId(accountId)
                .orElse(UserPoints.builder()
                        .accountId(accountId)
                        .totalPoints(0)
                        .build());

        int newTotal = up.getTotalPoints() + pointsToAdd;
        up.setTotalPoints(newTotal);
        up.setUpdatedAt(Instant.now());
        userPointsRepository.save(up);
    }

    private PointHistoryDTO toDTO(PointHistory history) {
        return PointHistoryDTO.builder()
                .points(history.getPoints())
                .actionType(history.getActionType())
                .description(history.getDescription())
                .createdAt(history.getCreatedAt())
                .build();
    }
}