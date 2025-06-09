package com.example.uno.websocket;

import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
public class GlobalWsExceptionHandler {

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Throwable ex) {
        System.out.println("WebSocket exception: " + ex.getMessage());
        return "WebSocket error: " + ex.getMessage();
    }
}
