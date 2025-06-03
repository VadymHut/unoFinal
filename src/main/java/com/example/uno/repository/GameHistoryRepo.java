package com.example.uno.repository;

import com.example.uno.model.GameHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameHistoryRepo extends JpaRepository<GameHistory, Long>
{
    List<GameHistory> findAllByPlayerIdOrderByFinishedAtDesc(Long playerId);
    int countByPlayerIdAndWinnerTrue(Long playerId);
    int countByPlayerId(Long playerId);
}
