package com.example.uno.websocket;

import com.example.uno.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            String authHeader = Optional.ofNullable(httpRequest.getHeader("Authorization"))
                    .orElse(httpRequest.getParameter("Authorization"));

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    String token = authHeader.substring(7);
                    JwtService.UserPrincipal principal = jwtService.parse(token);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    principal, null, principal.getAuthorities());

                    SecurityContextHolder.setContext(new SecurityContextImpl(authentication));

                    attributes.put("user", principal);

                    System.out.println("[HS] Bound Principal in handshake: " + principal.getName());
                    return true;
                } catch (Exception e) {
                    System.out.println("[HS] JWT parsing failed: " + e.getMessage());
                }
            }
        }

        System.out.println("[HS] No Authorization provided");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
