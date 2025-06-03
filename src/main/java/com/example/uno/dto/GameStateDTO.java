package com.example.uno.dto;

import com.example.uno.model.Color;
import com.example.uno.model.GameStatus;
import com.example.uno.model.Rank;
import lombok.Builder;

import java.util.List;

@Builder
public record GameStateDTO(
        Long gameId,
        GameStatus status,
        Integer yourSeat,
        Integer currentSeat,
        List<CardDTO> yourHand,
        Integer opponentHandSize,
        CardDTO topCard,
        Color activeColor,
        Rank activeRank,
        boolean ranked,
        Integer yourElo,
        Integer opponentElo,
        String yourUsername,
        String opponentUsername
) {}
