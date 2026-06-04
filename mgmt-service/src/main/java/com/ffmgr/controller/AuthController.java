package com.ffmgr.controller;

import com.ffmgr.dto.LoginRequest;
import com.ffmgr.dto.LoginResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SecretKey secretKey;
    private final long expirationMs;

    public AuthController(@Value("${jwt.secret}") String secret,
                          @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        if (!"admin".equals(request.getUsername()) || !"admin".equals(request.getPassword())) {
            return ResponseEntity.status(401).build();
        }

        List<String> roles = Arrays.asList("admin", "operator");
        String token = Jwts.builder()
                .setSubject(request.getUsername())
                .claim("roles", roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)
                .compact();

        return ResponseEntity.ok(new LoginResponse(token, request.getUsername(), roles));
    }

}
