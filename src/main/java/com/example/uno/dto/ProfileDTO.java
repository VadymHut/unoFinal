package com.example.uno.dto;

public record ProfileDTO(
        Long id,
        String username,
        Integer elo,
        double winrate
) {}
