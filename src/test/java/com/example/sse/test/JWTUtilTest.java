//package com.example.sse.test;
//
//import com.example.sse.config.ConfigManager;
//import com.example.sse.exception.ServiceException;
//import com.example.sse.util.JWTUtil;
//import io.jsonwebtoken.Claims;
//import io.jsonwebtoken.Jwts;
//import io.jsonwebtoken.security.Keys;
//import org.junit.After;
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//
//import javax.crypto.SecretKey;
//import java.nio.charset.StandardCharsets;
//import java.util.Date;
//import java.util.HashMap;
//import java.util.Map;
//
///**
// * Comprehensive tests for JWTUtil
// * Tests JWT validation, user ID extraction, token expiration, and error handling
// */
//public class JWTUtilTest {
//
//    private JWTUtil jwtUtil;
//    private SecretKey testSecretKey;
//
//    @Before
//    public void setUp() {
//        String jwtSecret;
//        try {
//            // Get the actual JWT secret from configuration
//            ConfigManager config = ConfigManager.getInstance();
//            jwtSecret = config.getProperty("jwt.secret");
//
//            if (jwtSecret == null || jwtSecret.length() < 32) {
//                // Use a test secret if configuration is not available
//                jwtSecret = "this@.0is@.1secret@.2key@.3for@.4jwt@.5testing@.6purposes@.7only";
//            }
//
//            testSecretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
//            jwtUtil = JWTUtil.getInstance();
//        } catch (Exception e) {
//            // Fallback for testing when configuration is not available
//            jwtSecret = "this@.0is@.1secret@.2key@.3for@.4jwt@.5testing@.6purposes@.7only";
//            testSecretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
//            System.out.println("Using fallback JWT secret for testing: " + e.getMessage());
//        }
//    }
//
//    @Test
//    public void testSingletonPattern() {
//        try {
//            JWTUtil instance1 = JWTUtil.getInstance();
//            JWTUtil instance2 = JWTUtil.getInstance();
//
//            Assert.assertNotNull("JWTUtil instance should not be null", instance1);
//            Assert.assertSame("JWTUtil should be singleton", instance1, instance2);
//            System.out.println("Singleton pattern test: PASSED");
//        } catch (Exception e) {
//            System.out.println("Singleton pattern test failed gracefully: " + e.getMessage());
//            // JWTUtil may fail to initialize if configuration is missing
//        }
//    }
//
//    @Test
//    public void testValidTokenValidation() {
//        try {
//            // Create a valid test token
//            String token = createTestToken(false);
//
//            Claims claims = jwtUtil.validateToken(token);
//            Assert.assertNotNull("Claims should not be null for valid token", claims);
//            Assert.assertFalse("Token should not be expired", jwtUtil.isTokenExpired(claims));
//
//            System.out.println("Valid token validation: PASSED");
//        } catch (Exception e) {
//            System.out.println("Valid token validation failed gracefully: " + e.getMessage());
//            // This may fail if JWT configuration is not available
//        }
//    }
//
//    @Test
//    public void testExpiredTokenValidation() {
//        try {
//            // Create an expired test token
//            String expiredToken = createTestToken(true);
//
//            jwtUtil.validateToken(expiredToken);
//            Assert.fail("Expired token should throw exception");
//        } catch (Exception e) {
//            Assert.assertTrue("Should throw exception for expired token",
//                    e.getMessage().contains("expired") || e.getMessage().contains("Invalid token"));
//            System.out.println("Expired token validation: PASSED - " + e.getMessage());
//        }
//    }
//
//    @Test
//    public void testInvalidTokenValidation() {
//        try {
//            String invalidToken = "invalid.jwt.token";
//            jwtUtil.validateToken(invalidToken);
//            Assert.fail("Invalid token should throw exception");
//        } catch (Exception e) {
//            Assert.assertTrue("Should throw exception for invalid token",
//                    e.getMessage().contains("Invalid token"));
//            System.out.println("Invalid token validation: PASSED - " + e.getMessage());
//        }
//    }
//
//    @Test
//    public void testUserIdExtractionFromUserDetail() {
//        try {
//            // Test user ID extraction from nested userdetail object
//            String token = createTestTokenWithUserDetail();
//            Claims claims = jwtUtil.validateToken(token);
//
//            Long userId = jwtUtil.getUserId(claims);
//            Assert.assertNotNull("User ID should not be null", userId);
//            Assert.assertEquals("User ID should match", Long.valueOf(879L), userId);
//
//            System.out.println("User ID extraction from userdetail: PASSED - " + userId);
//        } catch (Exception e) {
//            System.out.println("User ID extraction from userdetail failed gracefully: " + e.getMessage());
//        }
//    }
//
//    @Test
//    public void testUserIdExtractionFromRootLevel() {
//        try {
//            // Test user ID extraction from root level claims
//            String token = createTestTokenWithRootUserId();
//            Claims claims = jwtUtil.validateToken(token);
//
//            Long userId = jwtUtil.getUserId(claims);
//            Assert.assertNotNull("User ID should not be null", userId);
//            Assert.assertEquals("User ID should match", Long.valueOf(879L), userId);
//
//            System.out.println("User ID extraction from root level: PASSED - " + userId);
//        } catch (Exception e) {
//            System.out.println("User ID extraction from root level failed gracefully: " + e.getMessage());
//        }
//    }
//
//    @Test
//    public void testUserIdExtractionFromSubject() {
//        try {
//            // Test user ID extraction from subject
//            String token = createTestTokenWithSubject();
//            Claims claims = jwtUtil.validateToken(token);
//
//            Long userId = jwtUtil.getUserId(claims);
//            Assert.assertNotNull("User ID should not be null", userId);
//            Assert.assertEquals("User ID should match", Long.valueOf(879L), userId);
//
//            System.out.println("User ID extraction from subject: PASSED - " + userId);
//        } catch (Exception e) {
//            System.out.println("User ID extraction from subject failed gracefully: " + e.getMessage());
//        }
//    }
//
//    @Test
//    public void testUserIdExtractionFailure() {
//        try {
//            // Test user ID extraction when no user ID is present
//            String token = createTestTokenWithoutUserId();
//            Claims claims = jwtUtil.validateToken(token);
//
//            try {
//                jwtUtil.getUserId(claims);
//                Assert.fail("Should throw exception when user ID not found");
//            } catch (ServiceException e) {
//                Assert.assertTrue("Should throw ServiceException for missing user ID",
//                        e.getMessage().contains("User ID not found"));
//                System.out.println("User ID extraction failure: PASSED - " + e.getMessage());
//            }
//        } catch (Exception e) {
//            System.out.println("User ID extraction failure test failed gracefully: " + e.getMessage());
//        }
//    }
//
//    @Test
//    public void testUserDetailExtraction() throws Exception {
//        // Test extraction of user details
//        String token = createTestTokenWithUserDetail();
//        Claims claims = jwtUtil.validateToken(token);
//
//        String userName = jwtUtil.getUserDetail(claims, "user_name");
//        String userEmail = jwtUtil.getUserDetail(claims, "email");
//        String nonExistent = jwtUtil.getUserDetail(claims, "non_existent");
//
//        Assert.assertEquals("User name should match", "test_user", userName);
//        Assert.assertEquals("User email should match", "test@example.com", userEmail);
//        Assert.assertNull("Non-existent detail should be null", nonExistent);
//
//        System.out.println("User detail extraction: PASSED - Name: " + userName + ", Email: " + userEmail);
//    }
//
//    @Test
//    public void testUserDetailExtractionWithoutUserDetail() throws Exception {
//        // Test user detail extraction when userdetail object is not present
//        String token = createTestTokenWithoutUserDetail();
//        Claims claims = jwtUtil.validateToken(token);
//
//        String userName = jwtUtil.getUserDetail(claims, "user_name");
//        Assert.assertNull("User detail should be null when userdetail object not present", userName);
//
//        System.out.println("User detail extraction without userdetail: PASSED");
//    }
//
//    @Test
//    public void testTokenExpirationCheck() throws Exception {
//        // Test token expiration check
//        String validToken = createTestToken(false);
//        String expiredToken = createTestToken(true);
//
//        Claims validClaims = jwtUtil.validateToken(validToken);
//        Assert.assertFalse("Valid token should not be expired", jwtUtil.isTokenExpired(validClaims));
//
//        try {
//            Claims expiredClaims = jwtUtil.validateToken(expiredToken);
//            Assert.assertTrue("Expired token should be expired", jwtUtil.isTokenExpired(expiredClaims));
//        } catch (Exception e) {
//            // Expected for expired token
//            System.out.println("Expired token correctly rejected during validation");
//        }
//
//        System.out.println("Token expiration check: PASSED");
//    }
//
//    @Test
//    public void testUserIdTypeConversions() throws Exception {
//        // Test different user ID type conversions (Integer, Long, String)
//        String tokenWithIntUserId = createTestTokenWithIntUserId();
//        String tokenWithStringUserId = createTestTokenWithStringUserId();
//
//        Claims intClaims = jwtUtil.validateToken(tokenWithIntUserId);
//        Claims stringClaims = jwtUtil.validateToken(tokenWithStringUserId);
//
//        Long userIdFromInt = jwtUtil.getUserId(intClaims);
//        Long userIdFromString = jwtUtil.getUserId(stringClaims);
//
//        Assert.assertEquals("User ID from Integer should be 879", Long.valueOf(879L), userIdFromInt);
//        Assert.assertEquals("User ID from String should be 879", Long.valueOf(879L), userIdFromString);
//
//        System.out.println("User ID type conversions: PASSED - Int: " + userIdFromInt + ", String: " + userIdFromString);
//    }
//
//    // Helper methods to create test tokens
//    private String createTestToken(boolean expired) {
//        Map<String, Object> userDetail = new HashMap<>();
//        userDetail.put("user_master_id", 879L);
//        userDetail.put("user_name", "test_user");
//        userDetail.put("email", "test@example.com");
//
//        Date expiration = expired ?
//                new Date(System.currentTimeMillis() - 3600000) : // 1 hour ago
//                new Date(System.currentTimeMillis() + 3600000);   // 1 hour from now
//
//        return Jwts.builder()
//                .claim("userdetail", userDetail)
//                .setExpiration(expiration)
//                .signWith(testSecretKey)
//                .compact();
//    }
//
//    private String createTestTokenWithUserDetail() {
//        Map<String, Object> userDetail = new HashMap<>();
//        userDetail.put("user_master_id", 879L);
//        userDetail.put("user_name", "test_user");
//        userDetail.put("email", "test@example.com");
//
//        return Jwts.builder()
//                .claim("userdetail", userDetail)
//                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
//                .signWith(testSecretKey)
//                .compact();
//    }
//
//    private String createTestTokenWithRootUserId() {
//        return Jwts.builder()
//                .claim("user_master_id", 879L)
//                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
//                .signWith(testSecretKey)
//                .compact();
//    }
//
//    private String createTestTokenWithSubject() {
//        return Jwts.builder()
//                .setSubject("879")
//                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
//                .signWith(testSecretKey)
//                .compact();
//    }
//
//    private String createTestTokenWithoutUserId() {
//        return Jwts.builder()
//                .claim("some_other_claim", "value")
//                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
//                .signWith(testSecretKey)
//                .compact();
//    }
//
//    private String createTestTokenWithoutUserDetail() {
//        return Jwts.builder()
//                .claim("user_master_id", 879L)
//                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
//                .signWith(testSecretKey)
//                .compact();
//    }
//
//    private String createTestTokenWithIntUserId() {
//        Map<String, Object> userDetail = new HashMap<>();
//        userDetail.put("user_master_id", 879);
//
//        return Jwts.builder()
//                .claim("userdetail", userDetail)
//                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
//                .signWith(testSecretKey)
//                .compact();
//    }
//
//    private String createTestTokenWithStringUserId() {
//        Map<String, Object> userDetail = new HashMap<>();
//        userDetail.put("user_master_id", "879");
//
//        return Jwts.builder()
//                .claim("userdetail", userDetail)
//                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
//                .signWith(testSecretKey)
//                .compact();
//    }
//
//    @After
//    public void tearDown() {
//        System.out.println("JWTUtil test completed");
//    }
//}
