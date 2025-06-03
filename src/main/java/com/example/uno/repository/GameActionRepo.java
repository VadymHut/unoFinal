package com.example.uno.repository;

import com.example.uno.model.GameAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameActionRepo extends JpaRepository<GameAction, Long>
{
}
