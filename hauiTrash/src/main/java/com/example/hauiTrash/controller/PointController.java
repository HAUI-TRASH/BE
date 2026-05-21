package com.example.hauiTrash.controller;

import com.example.hauiTrash.dto.ApiResponse;
import com.example.hauiTrash.dto.LeaderboardDTO;
import com.example.hauiTrash.dto.PointHistoryDTO;
import com.example.hauiTrash.dto.UserPointsDTO;
import com.example.hauiTrash.entity.Account;
import com.example.hauiTrash.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    private Account getCurrentAccount() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Chưa đăng nhập");
        }
        return (Account) auth.getPrincipal();
    }

    // GET /api/v1/points/me
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserPointsDTO>> getMyPoints() {
        Account account = getCurrentAccount();
        UserPointsDTO points = pointService.getUserPoints(account.getId());
        return ResponseEntity.ok(ApiResponse.<UserPointsDTO>builder()
                .message("Lấy thông tin điểm thành công")
                .data(points)
                .build());
    }

    // GET /api/v1/points/history
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<PointHistoryDTO>>> getMyHistory() {
        Account account = getCurrentAccount();
        List<PointHistoryDTO> history = pointService.getPointHistory(account.getId());
        return ResponseEntity.ok(ApiResponse.<List<PointHistoryDTO>>builder()
                .message("Lấy lịch sử điểm thành công")
                .data(history)
                .build());
    }

    // GET /api/v1/points/leaderboard?page=0&size=10
    @GetMapping("/leaderboard")
    public ResponseEntity<ApiResponse<List<LeaderboardDTO>>> getLeaderboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        List<LeaderboardDTO> leaderboard = pointService.getLeaderboard(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.<List<LeaderboardDTO>>builder()
                .message("Lấy bảng xếp hạng thành công")
                .data(leaderboard)
                .build());
    }
}