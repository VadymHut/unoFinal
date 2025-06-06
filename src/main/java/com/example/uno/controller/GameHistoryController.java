package com.example.uno.controller;

import com.example.uno.dto.GameHistoryDTO;
import com.example.uno.model.AppUser;
import com.example.uno.repository.GameHistoryRepo;
import com.example.uno.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/history")
public class GameHistoryController {

    private final GameHistoryRepo historyRepo;

    @GetMapping
    public List<GameHistoryDTO> getCurrentUserHistory(@AuthenticationPrincipal JwtService.UserPrincipal principal) {
        Long userId = principal.getId();
        System.out.println("Authenticated principal: ID = " + userId);

        return historyRepo.findAllByPlayerIdOrderByFinishedAtDesc(userId)
                .stream()
                .map(h -> new GameHistoryDTO(
                        h.getGameCode(),
                        h.isRanked(),
                        h.isWinner(),
                        h.getFinishedAt(),
                        h.getFinalElo()))
                .toList();
    }

}

