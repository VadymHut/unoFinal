package com.example.uno.controller;

import com.example.uno.dto.ChangePasswordRequest;
import com.example.uno.dto.ProfileDTO;
import com.example.uno.model.AppUser;
import com.example.uno.repository.AppUserRepo;
import com.example.uno.repository.GameHistoryRepo;
import com.example.uno.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final AppUserRepo userRepo;
    private final GameHistoryRepo historyRepo;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public ProfileDTO getProfile(@AuthenticationPrincipal JwtService.UserPrincipal principal) {
        AppUser user = userRepo.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        int wins = historyRepo.countByPlayerIdAndWinnerTrue(user.getId());
        int total = historyRepo.countByPlayerId(user.getId());
        double winRate = (total > 0) ? (wins * 100.0) / total : 0.0;

        return new ProfileDTO(
                user.getId(),
                user.getUsername(),
                user.getElo(),
                winRate
        );
    }

    @PostMapping("/changepassword")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal JwtService.UserPrincipal principal,
                                            @RequestBody ChangePasswordRequest request) {
        AppUser user = userRepo.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Incorrect old password.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepo.save(user);

        return ResponseEntity.ok("Password changed successfully.");
    }


}
