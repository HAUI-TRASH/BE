package com.example.hauiTrash.service.impl;

import com.example.hauiTrash.dto.*;
import com.example.hauiTrash.entity.Account;
import com.example.hauiTrash.entity.AccountRole;
import com.example.hauiTrash.exception.BadRequestException;
import com.example.hauiTrash.exception.ForbiddenException;
import com.example.hauiTrash.exception.UnauthorizedException;
import com.example.hauiTrash.repository.AccountRepository;
import com.example.hauiTrash.security.JwtUtil;
import com.example.hauiTrash.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public AuthResponse registerUser(RegisterRequest req) {
        if (req.getPhone() == null || !req.getPhone().matches("^0\\d{9,10}$")) {
            throw new BadRequestException("Số điện thoại không hợp lệ");
        }
        if (accountRepository.existsByPhone(req.getPhone())) {
            throw new BadRequestException("Số điện thoại đã tồn tại");
        }

        Account acc = Account.builder()
                .phone(req.getPhone())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .fullName(req.getFullName())
                .role(AccountRole.USER)
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        acc = accountRepository.save(acc);
        return buildAuthResponse(acc);
    }

    @Override
    public AuthResponse loginUser(LoginRequest req) {
        Account acc = accountRepository.findByPhone(req.getIdentifier())
                .orElseThrow(() -> new UnauthorizedException("Sai tài khoản hoặc mật khẩu"));

        if (acc.getRole() != AccountRole.USER) {
            throw new ForbiddenException("Tài khoản này không thể đăng nhập vào User");
        }

        validateActiveAndPassword(acc, req.getPassword());
        return buildAuthResponse(acc);
    }

    @Override
    public AuthResponse loginAdmin(LoginRequest req) {
        Account acc = accountRepository.findByEmail(req.getIdentifier())
                .orElseThrow(() -> new UnauthorizedException("Sai tài khoản hoặc mật khẩu"));

        if (acc.getRole() == AccountRole.USER) {
            throw new ForbiddenException("Tài khoản này không thể đăng nhập vào Admin");
        }

        validateActiveAndPassword(acc, req.getPassword());
        return buildAuthResponse(acc);
    }

    @Override
    public void logout() {
        // Stateless logout: backend không lưu token, FE tự xoá token
    }

    private void validateActiveAndPassword(Account acc, String rawPassword) {
        if (Boolean.FALSE.equals(acc.getIsActive())) {
            throw new ForbiddenException("Tài khoản đã bị khóa");
        }
        if (!passwordEncoder.matches(rawPassword, acc.getPasswordHash())) {
            throw new UnauthorizedException("Sai tài khoản hoặc mật khẩu");
        }
    }

    private AuthResponse buildAuthResponse(Account acc) {
        String token = jwtUtil.generateToken(acc.getId(), acc.getRole());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationSeconds())
                .account(AccountInfo.builder()
                        .id(acc.getId())
                        .fullName(acc.getFullName())
                        .phone(acc.getPhone())
                        .email(acc.getEmail())
                        .role(acc.getRole().name())
                        .build())
                .build();
    }
}
