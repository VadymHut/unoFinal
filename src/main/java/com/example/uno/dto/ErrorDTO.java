package com.example.uno.dto;

import java.time.Instant;

public record ErrorDTO(int status, String message, Instant timestamp)
{
    public static ErrorDTO of(int status, String msg)
    {
        return new ErrorDTO(status, msg, Instant.now());
    }
}