package com.example.uno.repository;

import com.example.uno.model.GamePlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GamePlayerRepo extends JpaRepository<GamePlayer, Long>
{
    Optional<GamePlayer> findByGameIdAndUserId(Long gameId, Long userId);
    @Query("SELECT gp FROM GamePlayer gp JOIN FETCH gp.user WHERE gp.game.id = :gameId AND gp.user.id = :userId")
    Optional<GamePlayer> findByGameIdAndUserIdWithUser(@Param("gameId") Long gameId, @Param("userId") Long userId);

}
