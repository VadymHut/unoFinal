package com.example.uno.repository;

import com.example.uno.model.UnoGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UnoGameRepo extends JpaRepository<UnoGame, Long>
{
    Optional<UnoGame> findByGameCode(String gameCode);

    @Query("SELECT g FROM UnoGame g JOIN FETCH g.players WHERE g.gameCode = :code")
    Optional<UnoGame> findByGameCodeWithPlayers(@Param("code") String code);
}
