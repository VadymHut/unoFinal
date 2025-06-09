package com.example.uno.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtService jwt;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/js/**", "/css/**", "/images/**", "/register.html", "/history.html", "/favicon.ico", "/profile.html", "/uno-scripts.js", "uno-theme.css",
                                "login.html", "/images/**", "game.html", "changepassword.html", "rules.html", "match.html").permitAll()
                        .requestMatchers("/ws/**", "/ws", "/ws-sockjs/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/games").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/games/*/join").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/games/**").authenticated()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }


    @Bean
    public OncePerRequestFilter jwtFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req,
                                            HttpServletResponse res,
                                            FilterChain chain)
                    throws IOException, ServletException {

                String auth = req.getHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) {
                    try {
                        Authentication a = jwt.asAuthentication(auth.substring(7));
                        SecurityContextHolder.getContext().setAuthentication(a);
                    } catch (JwtException | IllegalArgumentException e) {
                        res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                        return;
                    }
                }
                chain.doFilter(req, res);
            }
        };
    }
}
