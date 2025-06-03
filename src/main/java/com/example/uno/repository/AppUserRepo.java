package com.example.uno.repository;

import com.example.uno.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepo extends JpaRepository<AppUser, Long>
{
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
}
