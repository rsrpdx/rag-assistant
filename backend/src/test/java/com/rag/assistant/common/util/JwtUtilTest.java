package com.rag.assistant.common.util;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 签发与校验：登录、刷新、鉴权过滤器都依赖这些行为。
 */
class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-key-long-enough-for-hs512-signing-0123456789";
    private static final String OTHER_SECRET = "another-unit-test-secret-key-long-enough-for-hs512-9876543210";
    private static final long ACCESS_EXPIRE = 3_600_000L;
    private static final long REFRESH_EXPIRE = 86_400_000L;
    /** 负数过期时间用来确定性地构造一个已过期 token，避免依赖 sleep */
    private static final long ALREADY_EXPIRED = -60_000L;

    private JwtUtil jwtUtil() {
        return new JwtUtil(SECRET, ACCESS_EXPIRE, REFRESH_EXPIRE);
    }

    @Test
    @DisplayName("签发的 access token 可以解析出用户信息且未过期")
    void accessTokenRoundTrip() {
        JwtUtil jwt = jwtUtil();

        String token = jwt.generateAccessToken(42L, "alice");

        assertEquals(42L, jwt.getUserIdFromToken(token));
        assertEquals("alice", jwt.getUsernameFromToken(token));
        assertFalse(jwt.isTokenExpired(token));
        assertTrue(jwt.parseToken(token).getExpiration().after(new java.util.Date()));
    }

    @Test
    @DisplayName("refresh token 有效期长于 access token")
    void refreshTokenOutlivesAccessToken() {
        JwtUtil jwt = jwtUtil();

        long accessExpireAt = jwt.parseToken(jwt.generateAccessToken(1L, "bob")).getExpiration().getTime();
        long refreshExpireAt = jwt.parseToken(jwt.generateRefreshToken(1L, "bob")).getExpiration().getTime();

        assertTrue(refreshExpireAt > accessExpireAt, "refresh token 应比 access token 活得更久");
    }

    @Test
    @DisplayName("过期 token 被判为过期，但仍能取出 userId 供刷新流程使用")
    void expiredTokenStillIdentifiesUser() {
        JwtUtil jwt = new JwtUtil(SECRET, ALREADY_EXPIRED, ALREADY_EXPIRED);

        String token = jwt.generateAccessToken(7L, "carol");

        assertTrue(jwt.isTokenExpired(token));
        assertEquals(7L, jwt.getUserIdFromToken(token));
        assertThrows(ExpiredJwtException.class, () -> jwt.parseToken(token));
    }

    @Test
    @DisplayName("被篡改的 token 一律拒绝")
    void tamperedTokenIsRejected() {
        JwtUtil jwt = jwtUtil();
        String token = jwt.generateAccessToken(5L, "dave");
        StringBuilder sb = new StringBuilder(token);
        char payloadChar = sb.charAt(20);
        sb.setCharAt(20, payloadChar == 'a' ? 'b' : 'a');

        assertNull(jwt.getUserIdFromToken(sb.toString()));
        assertNull(jwt.getUsernameFromToken(sb.toString()));
        assertTrue(jwt.isTokenExpired(sb.toString()));
    }

    @Test
    @DisplayName("用别的密钥签发的 token 无法通过本服务校验")
    void tokenFromOtherSecretIsRejected() {
        String foreignToken = new JwtUtil(OTHER_SECRET, ACCESS_EXPIRE, REFRESH_EXPIRE)
                .generateAccessToken(9L, "eve");

        assertNull(jwtUtil().getUserIdFromToken(foreignToken));
    }
}
