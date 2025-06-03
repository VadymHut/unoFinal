package com.example.uno.dto;

import java.time.Instant;

public record GameHistoryDTO(
        String gameCode,
        boolean ranked,
        boolean winner,
        Instant finishedAt,
        int finalElo
) {}
