package com.example.uno.controller;

import com.example.uno.dto.*;
import com.example.uno.model.UnoGame;
import com.example.uno.repository.UnoGameRepo;
import com.example.uno.security.JwtService;
import com.example.uno.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameRestController {

    private final GameService gameSvc;
    private final UnoGameRepo gameRepo;

    @PostMapping
    public String create(@AuthenticationPrincipal JwtService.UserPrincipal p,
                         @RequestBody CreateGameRequest req) {
        try {
            System.out.println(">>> POST /games by user: " + p.getId() + ", ranked=" + req.ranked());
            String code = gameSvc.createGame(p.getId(), req.ranked());
            System.out.println("Game created with code: " + code);
            return code;
        } catch (Exception e) {
            System.out.println("Exception during game creation: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @PostMapping("/{code}/join")
    public GameStateDTO join(@PathVariable String code,
                             @AuthenticationPrincipal JwtService.UserPrincipal p) {
        return gameSvc.join(code, p.getId());
    }

    @PostMapping("/{id}/move")
    public GameStateDTO move(@PathVariable Long id,
                             @AuthenticationPrincipal JwtService.UserPrincipal p,
                             @RequestBody MoveDTO move) {
        return gameSvc.applyMove(id, p.getId(), move);
    }

    @GetMapping("/state/{code}")
    public GameStateDTO getGameState(@PathVariable String code,
                                     @AuthenticationPrincipal JwtService.UserPrincipal p) {
        System.out.println("GET /state/" + code);
        System.out.println("Authenticated principal: " + p);

        try {
            UnoGame game = gameRepo.findByGameCodeWithPlayers(code)
                    .orElseThrow(() -> new NoSuchElementException("Game not found"));

            return gameSvc.snapshot(game, p.getId());
        } catch (Exception e) {
            System.out.println(">>> Exception in getGameState(): " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
