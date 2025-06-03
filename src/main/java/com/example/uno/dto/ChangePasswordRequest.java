package com.example.uno.dto;

public record ChangePasswordRequest(
        String oldPassword,
        String newPassword
) {}