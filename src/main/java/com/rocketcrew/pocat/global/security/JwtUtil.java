package com.rocketcrew.pocat.global.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.lang.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String generateAccessToken(Long userId, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    public Long getUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public long getExpiration(String token) {
        return getClaims(token).getExpiration().getTime() - System.currentTimeMillis();
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Optional<TokenPayload> extractPayload(String token) {
        try {
            Claims claims = getClaims(token);
            return Optional.of(new TokenPayload(
                    Long.parseLong(claims.getSubject()),
                    claims.get("role", String.class)
            ));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
    /**
     * JWT를 1회 파싱하여 Claims 반환. 유효하지 않으면 null 반환.
     * JwtAuthenticationFilter에서 파싱 횟수를 줄이기 위해 사용.
     */
    @Nullable
    public Claims parseClaimsOrNull(String token) {
        try {
            return getClaims(token);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 만료된 토큰에서도 userId 추출. 로그아웃 시 refresh 토큰 삭제에 사용.
     * 서명 자체가 유효하지 않으면 null 반환.
     */
    @Nullable
    public Long getUserIdIgnoringExpiration(String token) {
        try {
            return Long.parseLong(getClaims(token).getSubject());
        } catch (ExpiredJwtException e) {
            try {
                return Long.parseLong(e.getClaims().getSubject());
            } catch (NumberFormatException ex) {
                return null;
            }
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public record TokenPayload(Long userId, String role) {}

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
