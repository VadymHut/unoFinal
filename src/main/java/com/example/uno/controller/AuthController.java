package com.example.uno.controller;

import com.example.uno.model.AppUser;
import com.example.uno.repository.AppUserRepo;
import com.example.uno.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AppUserRepo users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public record RegisterReq(@NotBlank @Size(min = 3, max = 20) String username,
                              @NotBlank @Size(min = 6, max = 100) String password) {}
    public record LoginReq    (@NotBlank @Size(min = 3, max = 20) String username,
                               @NotBlank @Size(min = 6, max = 100) String password) {}
    public record TokenResp   (String token) {}

    @PostMapping("/register")
    public ResponseEntity<TokenResp> register(@Valid @RequestBody RegisterReq r) {
        if (users.existsByUsername(r.username()))
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new TokenResp("Username already taken"));

        AppUser saved = users.save(AppUser.builder()
                .username(r.username())
                .passwordHash(encoder.encode(r.password()))
                .enabled(true)
                .elo(1000)
                .build());

        return ResponseEntity.ok(new TokenResp(jwt.issue(saved)));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResp> login(@Valid @RequestBody LoginReq r) {

        AppUser user = users.findByUsername(r.username())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Bad credentials"));

        if (!encoder.matches(r.password(), user.getPasswordHash()))
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Bad credentials");

        return ResponseEntity.ok(new TokenResp(jwt.issue(user)));
    }
}

