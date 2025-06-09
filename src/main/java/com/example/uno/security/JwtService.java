package com.example.uno.security;

import com.example.uno.model.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;
    private Key key;

    @PostConstruct
    void init() { key = Keys.hmacShaKeyFor(secret.getBytes()); }

    public String issue(AppUser u) {
        Set<String> roleStrings = Optional.ofNullable(u.getRoles())
                .orElse(Collections.emptySet())
                .stream()
                .map(Enum::name)
                .collect(Collectors.toSet());

        return Jwts.builder()
                .setSubject(u.getId().toString())
                .claim("roles", roleStrings)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Authentication asAuthentication(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();

        Long uid = Long.valueOf(claims.getSubject());

        @SuppressWarnings("unchecked")
        Collection<String> roleStrings = claims.get("roles", Collection.class);
        if (roleStrings == null) roleStrings = Collections.emptyList();

        UserPrincipal principal = new UserPrincipal(uid, new HashSet<>(roleStrings));

        return new UsernamePasswordAuthenticationToken(
                principal, token, principal.getAuthorities());
    }

    public UserPrincipal parse(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();

        Long uid = Long.valueOf(claims.getSubject());

        @SuppressWarnings("unchecked")
        Collection<String> roleStrings = claims.get("roles", Collection.class);
        if (roleStrings == null) roleStrings = Collections.emptyList();

        return new UserPrincipal(uid, new HashSet<>(roleStrings));
    }

    @Getter
    @RequiredArgsConstructor
    public static class UserPrincipal implements UserDetails, Principal {

        private final Long id;
        private final Set<String> roles;

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toUnmodifiableSet());
        }

        @Override public String getPassword() { return null; }
        @Override public String getUsername() { return id.toString(); }
        @Override public boolean isAccountNonExpired() { return true; }
        @Override public boolean isAccountNonLocked() { return true; }
        @Override public boolean isCredentialsNonExpired() { return true; }
        @Override public boolean isEnabled() { return true; }

        @Override
        public String getName() {
            return id.toString();
        }
    }

}
