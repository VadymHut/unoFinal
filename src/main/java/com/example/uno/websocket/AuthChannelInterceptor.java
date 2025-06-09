package com.example.uno.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
@RequiredArgsConstructor
public class AuthChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        Principal principal = accessor.getUser();

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            System.out.println("[WS] CONNECT frame received");
        }

        if (principal == null) {
            System.out.println("[WS] No Principal bound for frame: " + accessor.getCommand());
        } else {
            System.out.println("[WS] Frame from user: " + principal.getName() + " | " + accessor.getCommand());
        }

        return message;
    }
}
