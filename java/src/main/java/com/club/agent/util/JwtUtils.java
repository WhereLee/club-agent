package com.club.agent.util;

import com.club.agent.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具：签发 / 解析（jjwt 0.12.x API）。
 * 载荷：sub=username、uid=userId、iat/exp。
 */
@Component
public class JwtUtils {

    private static final String CLAIM_UID = "uid";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtUtils(JwtProperties properties) {
        this.properties = properties;
        byte[] bytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET 长度不足 32 字节，HS256 无法签名，请重新生成");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /** 签发 token */
    public String createToken(Long userId, String username) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + properties.getExpireMinutes() * 60_000L);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())   // jti：登出黑名单凭据
                .subject(username)
                .claim(CLAIM_UID, userId)
                .issuedAt(now)
                .expiration(expire)
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 token（过期/篡改抛 JwtException）。
     *
     * @return 解析后的载荷；token 非法返回 null
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public Long getUserId(Claims claims) {
        Object uid = claims.get(CLAIM_UID);
        return uid == null ? null : Long.valueOf(uid.toString());
    }
}
