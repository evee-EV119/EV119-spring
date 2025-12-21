package com.app.ev119.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {


    private final SecretKey key;

    // 유효시간
    @Value("${jwt.access-token-validity-in-ms:1800000}")
    private long accessTokenValidityInMs;

    // RefreshToken 유효시간
    @Value("${jwt.refresh-token-validity-in-ms:1209600000}")
    private long refreshTokenValidityInMs;


    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey) {
        // HS256은 최소 32바이트 이상 권장 → secretKey는 충분히 길게!
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    // Access Token
    public String createAccessToken(Long memberId, String role) {
        return createToken(memberId, accessTokenValidityInMs, role);
    }

    // Refresh Token은 굳이 role 없어도 됨
    public String createRefreshToken(Long memberId) {
        return createToken(memberId, refreshTokenValidityInMs, null);
    }

    private String createToken(Long memberId, long validityInMs, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + validityInMs);

        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(memberId))
                .issuedAt(now)
                .expiration(expiryDate);

        if (role != null && !role.isBlank()) {
            builder.claim("role", role); // 예: ROLE_STAFF
        }

        return builder.signWith(key).compact();
    }

    // role 꺼내기
    public String getRole(String token) {
        Claims claims = parseClaims(token);
        return claims.get("role", String.class);
    }


    /*
     * 토큰에서 memberId 꺼내기
     */
    public Long getMemberId(String token) {
        Claims claims = parseClaims(token);
        return Long.valueOf(claims.getSubject());
    }

    public Long getRefreshTokenValidityInMs() {
        return refreshTokenValidityInMs;
    }

    public Long getAccessTokenValidityInMs() {
        return accessTokenValidityInMs;
    }

    /*
     * 토큰 유효성 검증 (유효하면 true, 문제 발생 시 false)
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException 안에 만료, 시그니처 오류 등 다 들어있음
            return false;
        }
    }


    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 토큰 만료까지 남은 시간
    public Long getRemindValidityInMs(String token) {
        Claims claims = parseClaims(token);
        Date expiration = claims.getExpiration();
        long now = System.currentTimeMillis();
        return expiration.getTime() - now;
    }
}
