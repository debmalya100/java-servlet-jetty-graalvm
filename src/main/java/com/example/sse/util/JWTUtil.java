package com.example.sse.util;

import com.example.sse.config.ConfigManager;
import com.example.sse.exception.ServiceException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static io.jsonwebtoken.Jwts.*;

public class JWTUtil {
    private static final Logger logger = LoggerFactory.getLogger(JWTUtil.class);
    private static JWTUtil instance;
    private final SecretKey secretKey;

    private JWTUtil() {
        ConfigManager config = ConfigManager.getInstance();
        String secret = config.getProperty("jwt.secret");
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 characters long");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public static synchronized JWTUtil getInstance() {
        if (instance == null) {
            instance = new JWTUtil();
        }
        return instance;
    }

    public Claims validateToken(String token) throws Exception {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // Check if token is expired
            Date expiration = claims.getExpiration();
            if (expiration != null && expiration.before(new Date())) {
                throw new ServiceException(ServiceException.ServiceType.JWT, "Token has expired");
            }

            return claims;
        } catch (Exception e) {
            logger.error("Token validation failed", e);
            throw new ServiceException(ServiceException.ServiceType.JWT, "Invalid token: " + e.getMessage());
        }
    }

    public Long getUserId(Claims claims) {
        try {
            // First try to get user_master_id from userdetail object
            Object userDetailObj = claims.get("userdetail");
            if (userDetailObj instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> userDetail = (java.util.Map<String, Object>) userDetailObj;
                Object userIdObj = userDetail.get("user_master_id");
                if (userIdObj instanceof Integer) {
                    return ((Integer) userIdObj).longValue();
                } else if (userIdObj instanceof Long) {
                    return (Long) userIdObj;
                } else if (userIdObj instanceof String) {
                    return Long.parseLong((String) userIdObj);
                }
            }

            // Fallback: try to get user_master_id from root level
            Object userIdObj = claims.get("user_master_id");
            if (userIdObj instanceof Integer) {
                return ((Integer) userIdObj).longValue();
            } else if (userIdObj instanceof Long) {
                return (Long) userIdObj;
            } else if (userIdObj instanceof String) {
                return Long.parseLong((String) userIdObj);
            }

            // If not found in custom claims, try to get from subject
            String subject = claims.getSubject();
            if (subject != null) {
                return Long.parseLong(subject);
            }

            throw new ServiceException(ServiceException.ServiceType.JWT, "User ID not found in token");
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error extracting user ID from token", e);
            throw new ServiceException(ServiceException.ServiceType.JWT, "Failed to extract user ID from token", e);
        }
    }

    public String getUserDetail(Claims claims, String key) {
        try {
            Object userDetailObj = claims.get("userdetail");
            if (userDetailObj instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> userDetail = (java.util.Map<String, Object>) userDetailObj;
                Object value = userDetail.get(key);
                return value != null ? value.toString() : null;
            }
            return null;
        } catch (Exception e) {
            logger.error("Error extracting user detail from token", e);
            return null;
        }
    }

    public boolean isTokenExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration != null && expiration.before(new Date());
    }
}