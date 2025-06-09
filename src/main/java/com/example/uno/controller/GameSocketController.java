package com.example.uno.controller;

import com.example.uno.dto.MoveDTO;
import com.example.uno.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class GameSocketController {

    private final GameService gameSvc;
    private final SimpMessagingTemplate broker;

    @MessageMapping("/game.{gameId}.move")
    public void handleMove(@DestinationVariable Long gameId,
                           @Payload MoveDTO move,
                           Principal principal) {

        if (principal == null) {
            System.out.println("Principal is still null in session");
            throw new IllegalStateException("Principal not available in session");
        }

        System.out.println("Principal injected: " + principal.getName() + " [" + principal.getClass().getSimpleName() + "]");
        Long userId = Long.valueOf(principal.getName());

        gameSvc.applyMove(gameId, userId, move);
    }


    @MessageMapping("/debug")
    public void debug(Message<?> message) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);
        Principal principal = accessor.getUser();
        System.out.println("Debug message from user: " + (principal != null ? principal.getName() : "null"));
    }

}
