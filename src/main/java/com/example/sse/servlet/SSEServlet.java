package com.example.sse.servlet;

import com.example.sse.cache.CacheProvider;
import com.example.sse.cache.CacheProviderFactory;
import com.example.sse.service.DataService;
import com.example.sse.service.DataServiceImpl;
import com.example.sse.util.JWTUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PRODUCTION SSE SERVLET - NO BACKGROUND TASKS
 * 
 * Simplified for production use:
 * ✅ High-performance connection pooling
 * ✅ 3-tier caching (L1 + L2 + L3)
 * ✅ Parallel database queries
 * ✅ Scale-to-zero friendly
 * ❌ NO background tasks (not needed!)
 * ❌ NO periodic cleanup (lazy cleanup is sufficient)
 * ❌ NO performance monitoring threads (use health endpoints)
 * 
 * Benefits:
 * 🚀 Faster startup (no background thread setup)
 * 💾 Lower memory usage (fewer threads)
 * 🔧 Simpler debugging (no background noise)
 * ⚡ Better for scale-to-zero (no interrupted tasks)
 */
@WebServlet("/api/sse/*")
public class SSEServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(SSEServlet.class);

    // === PRODUCTION OPTIMIZED SETTINGS ===

    private static final int ASYNC_THREADS = 4;
    private static final int TOKEN_THREADS = 2;

    // Timeouts
    private static final long LOCAL_CACHE_CHECK_MS = 2;
    private static final long REDIS_TIMEOUT_MS = 300;
    private static final long TOKEN_VALIDATION_TIMEOUT_MS = 2000;
    private static final long PARALLEL_DB_TIMEOUT_MS = 8000;
    private static final long INDIVIDUAL_QUERY_TIMEOUT_MS = 6000;

    // Cache TTLs
    private static final long LOCAL_TOKEN_TTL_MS = 300000; // 5 minutes
    private static final long LOCAL_DATA_TTL_MS = 60000; // 1 minute
    private static final long LOCAL_SESSION_TTL_MS = 30000; // 30 seconds

    private static final int REDIS_TOKEN_TTL = 1800; // 30 minutes
    private static final int REDIS_DATA_TTL = 300; // 5 minutes
    private static final int REDIS_SESSION_TTL = 120; // 2 minutes

    // Cache size limits (with lazy cleanup)
    private static final int MAX_LOCAL_CACHE_SIZE = 200;
    private static final int LAZY_CLEANUP_THRESHOLD = 150; // Clean when accessing cache

    // Cache key prefixes
    private static final String TOKEN_PREFIX = "sse:token:";
    private static final String DATA_PREFIX = "sse:data:";
    private static final String SESSION_PREFIX = "sse:session:";

    // Services
    private DataService dataService;
    private JWTUtil jwtUtil;
    private ObjectMapper objectMapper;
    private CacheProvider redisCache;
    private ExecutorService dbExecutor;
    private ExecutorService tokenExecutor;

    // Simple performance counters (no background monitoring needed)
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong timeouts = new AtomicLong(0);
    private volatile long startTime = System.currentTimeMillis();

    // LAZY-CLEANUP Local caches (cleaned on access, not background)
    private final ConcurrentHashMap<String, LocalCacheEntry<Long>> localTokenCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalCacheEntry<String>> localDataCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalCacheEntry<String>> localSessionCache = new ConcurrentHashMap<>();

    @Override
    public void init() throws ServletException {
        super.init();
        logger.info("🚀 Initializing PRODUCTION SSE Servlet (No Background Tasks)...");
        long initStart = System.currentTimeMillis();
        startTime = initStart;

        try {
            // Core services
            this.objectMapper = new ObjectMapper();
            this.jwtUtil = JWTUtil.getInstance();
            this.dataService = DataServiceImpl.getInstance();
            this.redisCache = CacheProviderFactory.getInstance();

            // Thread pools (daemon threads - no explicit shutdown needed)
            this.dbExecutor = Executors.newFixedThreadPool(ASYNC_THREADS, r -> {
                Thread t = new Thread(r, "prod-db-" + System.currentTimeMillis());
                t.setDaemon(true); // JVM can exit without waiting for these
                return t;
            });

            this.tokenExecutor = Executors.newFixedThreadPool(TOKEN_THREADS, r -> {
                Thread t = new Thread(r, "prod-token-" + System.currentTimeMillis());
                t.setDaemon(true); // JVM can exit without waiting for these
                return t;
            });

            long initTime = System.currentTimeMillis() - initStart;
            logger.info("✅ PRODUCTION SSE Servlet initialized in {}ms (No Background Tasks)", initTime);
            logger.info("🎯 PRODUCTION MODE: Lazy cleanup, no monitoring threads, scale-to-zero optimized");

        } catch (Exception e) {
            logger.error("❌ Failed to initialize production servlet", e);
            throw new ServletException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        long startTime = System.nanoTime();
        long requestId = requestCount.incrementAndGet();
        setSSEHeaders(response);

        logger.debug("🚀 Request #{} started", requestId);

        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.contains("session-status")) {
                handleSessionStatusProduction(request, response, startTime, requestId);
            } else {
                handleMainSSEProduction(request, response, startTime, requestId);
            }
        } catch (Exception e) {
            logger.error("❌ Request #{} failed: {}", requestId, e.getMessage());
            response.setStatus(500);
            sendErrorResponse(response, "Service temporarily unavailable");
        }
    }

    /**
     * PRODUCTION Main SSE Handler
     */
    private void handleMainSSEProduction(HttpServletRequest request, HttpServletResponse response,
            long startTime, long requestId) throws IOException {

        try (PrintWriter out = response.getWriter()) {
            String token = request.getParameter("token");
            String typeId = getParameter(request, "type_id", request.getParameter("session_id"));
            String type = getParameter(request, "type", "session");

            logger.debug("🚀 Req #{}: typeId={}, type={}", requestId, typeId, type);

            String dataCacheKey = type + ":" + typeId;

            // === L1: LOCAL CACHE WITH LAZY CLEANUP ===
            LocalCacheEntry<String> localData = getFromLocalCacheWithLazyCleanup(localDataCache, dataCacheKey);
            if (localData != null && !localData.isExpired()) {
                logger.debug("⚡ Req #{}: L1 HIT", requestId);
                cacheHits.incrementAndGet();

                out.print("data: ");
                out.print(localData.value);
                out.print("\n\n");
                out.flush();

                long responseTimeMs = (System.nanoTime() - startTime) / 1_000_000;
                logger.debug("✅ Req #{}: L1 response in {}ms", requestId, responseTimeMs);
                return;
            }

            // === L2: PARALLEL TOKEN + REDIS ===
            logger.debug("🔄 Req #{}: Starting parallel token+Redis", requestId);

            CompletableFuture<Long> tokenFuture = CompletableFuture.supplyAsync(() -> {
                return validateTokenProduction(token, requestId);
            }, tokenExecutor);

            CompletableFuture<String> redisFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return redisCache.get(DATA_PREFIX + dataCacheKey);
                } catch (Exception e) {
                    logger.debug("Req #{}: Redis failed: {}", requestId, e.getMessage());
                    return null;
                }
            }, dbExecutor);

            // Wait for both
            try {
                CompletableFuture<Void> bothComplete = CompletableFuture.allOf(tokenFuture, redisFuture);
                bothComplete.get(TOKEN_VALIDATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                Long userId = tokenFuture.get();
                String redisData = redisFuture.get();

                if (userId == null) {
                    response.setStatus(401);
                    sendError(out, "Invalid access token");
                    return;
                }

                if (redisData != null && !redisData.isEmpty()) {
                    logger.debug("⚡ Req #{}: L2 HIT", requestId);
                    cacheHits.incrementAndGet();

                    // Update L1 cache with lazy cleanup
                    putToLocalCacheWithLazyCleanup(localDataCache, dataCacheKey, redisData, LOCAL_DATA_TTL_MS);

                    out.print("data: ");
                    out.print(redisData);
                    out.print("\n\n");
                    out.flush();

                    long responseTimeMs = (System.nanoTime() - startTime) / 1_000_000;
                    logger.debug("✅ Req #{}: L2 response in {}ms", requestId, responseTimeMs);
                    return;
                }

                // === L3: PARALLEL DATABASE QUERIES ===
                logger.debug("🔄 Req #{}: Starting parallel DB queries", requestId);

                CompletableFuture<Map<String, Object>> dataFuture = CompletableFuture.supplyAsync(() -> {
                    return fetchDataProduction(userId, typeId, type, requestId);
                }, dbExecutor);

                Map<String, Object> mainData = dataFuture.get(PARALLEL_DB_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                String jsonData = objectMapper.writeValueAsString(mainData);

                out.print("data: ");
                out.print(jsonData);
                out.print("\n\n");
                out.flush();

                // Update caches asynchronously
                updateAllCachesProduction(dataCacheKey, jsonData);

                long responseTimeMs = (System.nanoTime() - startTime) / 1_000_000;
                logger.debug("✅ Req #{}: L3 response in {}ms", requestId, responseTimeMs);

            } catch (TimeoutException e) {
                timeouts.incrementAndGet();
                long responseTimeMs = (System.nanoTime() - startTime) / 1_000_000;
                logger.warn("⏰ Req #{}: Timeout after {}ms", requestId, responseTimeMs);

                handleTimeoutFallback(out, dataCacheKey, response, requestId);
            }

        } catch (Exception e) {
            logger.error("❌ Req #{}: Main SSE error: {}", requestId, e.getMessage());
            response.setStatus(500);
            sendErrorResponse(response, "Service temporarily unavailable");
        }
    }

    /**
     * PRODUCTION Token validation
     */
    private Long validateTokenProduction(String token, long requestId) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }

        String tokenKey = String.valueOf(token.hashCode());

        // L1: Local cache with lazy cleanup
        LocalCacheEntry<Long> localToken = getFromLocalCacheWithLazyCleanup(localTokenCache, tokenKey);
        if (localToken != null && !localToken.isExpired()) {
            logger.debug("⚡ Req #{}: Token L1 HIT", requestId);
            return localToken.value;
        }

        // L2: Redis
        try {
            CompletableFuture<String> redisFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return redisCache.get(TOKEN_PREFIX + tokenKey);
                } catch (Exception e) {
                    return null;
                }
            });

            String cachedUserId = redisFuture.get(REDIS_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (cachedUserId != null && !cachedUserId.isEmpty()) {
                Long userId = Long.parseLong(cachedUserId);
                logger.debug("⚡ Req #{}: Token L2 HIT", requestId);

                // Update L1 cache
                putToLocalCacheWithLazyCleanup(localTokenCache, tokenKey, userId, LOCAL_TOKEN_TTL_MS);
                return userId;
            }
        } catch (Exception e) {
            logger.debug("Req #{}: Token Redis failed: {}", requestId, e.getMessage());
        }

        // L3: JWT validation
        try {
            CompletableFuture<Long> jwtFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    Claims claims = jwtUtil.validateToken(token);
                    return jwtUtil.getUserId(claims);
                } catch (Exception e) {
                    return null;
                }
            });

            Long userId = jwtFuture.get(1500, TimeUnit.MILLISECONDS);

            if (userId != null) {
                logger.debug("✅ Req #{}: Token L3 validated", requestId);
                updateTokenCaches(tokenKey, userId);
            }

            return userId;

        } catch (Exception e) {
            logger.debug("Req #{}: Token validation failed: {}", requestId, e.getMessage());
            return null;
        }
    }

    /**
     * PRODUCTION parallel data fetching
     */
    private Map<String, Object> fetchDataProduction(Long userId, String typeId, String type, long requestId) {
        Map<String, Object> mainData = new HashMap<>();

        logger.debug("🔄 Req #{}: Production DB fetch start", requestId);

        // Start ALL queries in parallel
        CompletableFuture<List<?>> commentFuture = CompletableFuture.supplyAsync(() -> {
            try {
                long queryStart = System.currentTimeMillis();
                List<?> result = dataService.getComments(userId, typeId, type);
                long queryTime = System.currentTimeMillis() - queryStart;
                logger.debug("📊 Req #{}: Comments {}ms, {} items", requestId, queryTime,
                        result != null ? result.size() : 0);
                return result != null ? result : List.of();
            } catch (Exception e) {
                logger.warn("Req #{}: Comments failed: {}", requestId, e.getMessage());
                return List.of();
            }
        }, dbExecutor);

        CompletableFuture<List<?>> surveyFuture = null;
        CompletableFuture<String> sessionStatusFuture = null;

        if ("session".equals(type)) {
            surveyFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    long queryStart = System.currentTimeMillis();
                    List<?> result = dataService.getStreamingPolls(typeId, userId);
                    long queryTime = System.currentTimeMillis() - queryStart;
                    logger.debug("📊 Req #{}: Surveys {}ms, {} items", requestId, queryTime,
                            result != null ? result.size() : 0);
                    return result != null ? result : List.of();
                } catch (Exception e) {
                    logger.warn("Req #{}: Surveys failed: {}", requestId, e.getMessage());
                    return List.of();
                }
            }, dbExecutor);

            sessionStatusFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    long queryStart = System.currentTimeMillis();
                    String result = dataService.getSessionStatus(typeId);
                    long queryTime = System.currentTimeMillis() - queryStart;
                    logger.debug("📊 Req #{}: Status {}ms, result: {}", requestId, queryTime, result);
                    return result;
                } catch (Exception e) {
                    logger.warn("Req #{}: Status failed: {}", requestId, e.getMessage());
                    return "unavailable";
                }
            }, dbExecutor);
        }

        // Collect results
        try {
            if ("session".equals(type)) {
                CompletableFuture<Void> allQueries = CompletableFuture.allOf(
                        commentFuture, surveyFuture, sessionStatusFuture);

                allQueries.get(PARALLEL_DB_TIMEOUT_MS - 1000, TimeUnit.MILLISECONDS);

                mainData.put("comment_data", commentFuture.get());
                mainData.put("survey_data", surveyFuture.get());
                mainData.put("session_status", sessionStatusFuture.get());

                logger.debug("✅ Req #{}: All 3 queries completed", requestId);

            } else {
                mainData.put("comment_data", commentFuture.get(INDIVIDUAL_QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS));
                mainData.put("survey_data", List.of());
                mainData.put("session_status", "");

                logger.debug("✅ Req #{}: Comments query completed", requestId);
            }

        } catch (TimeoutException e) {
            logger.warn("⏰ Req #{}: Parallel queries timeout - collecting partial", requestId);

            // Collect partial results
            try {
                mainData.put("comment_data", commentFuture.isDone() ? commentFuture.get() : List.of());
            } catch (Exception ex) {
                mainData.put("comment_data", List.of());
            }

            if ("session".equals(type)) {
                try {
                    mainData.put("survey_data", surveyFuture.isDone() ? surveyFuture.get() : List.of());
                } catch (Exception ex) {
                    mainData.put("survey_data", List.of());
                }
                try {
                    mainData.put("session_status",
                            sessionStatusFuture.isDone() ? sessionStatusFuture.get() : "timeout");
                } catch (Exception ex) {
                    mainData.put("session_status", "timeout");
                }
            } else {
                mainData.put("survey_data", List.of());
                mainData.put("session_status", "");
            }
        } catch (Exception e) {
            logger.error("Req #{}: DB error: {}", requestId, e.getMessage());
            mainData.put("comment_data", List.of());
            mainData.put("survey_data", List.of());
            mainData.put("session_status", "error");
        }

        return mainData;
    }

    /**
     * Session status production
     */
    private void handleSessionStatusProduction(HttpServletRequest request, HttpServletResponse response,
            long startTime, long requestId) throws IOException {

        try (PrintWriter out = response.getWriter()) {
            String sessionId = request.getParameter("session_id");

            if (sessionId == null || sessionId.trim().isEmpty()) {
                response.setStatus(400);
                sendError(out, "session_id parameter required");
                return;
            }

            // L1: Local cache with lazy cleanup
            LocalCacheEntry<String> localStatus = getFromLocalCacheWithLazyCleanup(localSessionCache, sessionId);
            if (localStatus != null && !localStatus.isExpired()) {
                out.print("data: " + localStatus.value + "\n\n");
                out.flush();
                logger.debug("⚡ Req #{}: Session L1 HIT", requestId);
                return;
            }

            // L2: Redis
            try {
                CompletableFuture<String> redisFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return redisCache.get(SESSION_PREFIX + sessionId);
                    } catch (Exception e) {
                        return null;
                    }
                });

                String redisStatus = redisFuture.get(REDIS_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (redisStatus != null && !redisStatus.isEmpty()) {
                    putToLocalCacheWithLazyCleanup(localSessionCache, sessionId, redisStatus, LOCAL_SESSION_TTL_MS);
                    out.print("data: " + redisStatus + "\n\n");
                    out.flush();
                    logger.debug("⚡ Req #{}: Session L2 HIT", requestId);
                    return;
                }
            } catch (Exception e) {
                logger.debug("Req #{}: Session Redis failed: {}", requestId, e.getMessage());
            }

            // L3: Database
            try {
                CompletableFuture<String> dbFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return dataService.getSessionStatus(sessionId);
                    } catch (Exception e) {
                        return "unavailable";
                    }
                }, dbExecutor);

                String status = dbFuture.get(INDIVIDUAL_QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                String statusResult = status != null ? status : "unknown";

                out.print("data: " + statusResult + "\n\n");
                out.flush();

                updateSessionCaches(sessionId, statusResult);
                logger.debug("✅ Req #{}: Session L3 response", requestId);

            } catch (TimeoutException e) {
                out.print("data: loading\n\n");
                out.flush();
                logger.warn("⏰ Req #{}: Session timeout", requestId);
            }

        } catch (Exception e) {
            logger.error("❌ Req #{}: Session error: {}", requestId, e.getMessage());
            response.setStatus(500);
            sendErrorResponse(response, "Session status failed");
        }
    }

    // === LAZY CLEANUP CACHE METHODS (No background tasks needed!) ===

    /**
     * Get from cache with lazy cleanup - removes expired entries when accessing
     */
    private <T> LocalCacheEntry<T> getFromLocalCacheWithLazyCleanup(
            ConcurrentHashMap<String, LocalCacheEntry<T>> cache, String key) {

        // Lazy cleanup: Remove expired entries occasionally when accessing cache
        if (cache.size() > LAZY_CLEANUP_THRESHOLD && Math.random() < 0.1) { // 10% chance
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            logger.debug("🧹 Lazy cleanup removed expired entries from cache (size: {})", cache.size());
        }

        return cache.get(key);
    }

    /**
     * Put to cache with size limit - removes entries if cache gets too big
     */
    private <T> void putToLocalCacheWithLazyCleanup(
            ConcurrentHashMap<String, LocalCacheEntry<T>> cache, String key, T value, long ttl) {

        // If cache is getting full, clean it up
        if (cache.size() >= MAX_LOCAL_CACHE_SIZE) {
            // Remove expired entries first
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());

            // If still too big, remove 20% of entries (LRU approximation)
            if (cache.size() >= MAX_LOCAL_CACHE_SIZE) {
                cache.entrySet().stream()
                        .limit(cache.size() / 5)
                        .forEach(entry -> cache.remove(entry.getKey()));
                logger.debug("🧹 Cache size limit reached, removed old entries (size: {})", cache.size());
            }
        }

        cache.put(key, new LocalCacheEntry<>(value, ttl));
    }

    private void handleTimeoutFallback(PrintWriter out, String dataCacheKey, HttpServletResponse response,
            long requestId) {
        try {
            // Try expired cache first
            LocalCacheEntry<String> localData = localDataCache.get(dataCacheKey);
            if (localData != null) {
                logger.info("📤 Req #{}: Returning expired cache (fallback)", requestId);
                out.print("data: " + localData.value + "\n\n");
                out.flush();
                return;
            }

            // Return loading state
            Map<String, Object> loadingData = new HashMap<>();
            loadingData.put("comment_data", List.of());
            loadingData.put("survey_data", List.of());
            loadingData.put("session_status", "loading");

            String jsonData = objectMapper.writeValueAsString(loadingData);
            out.print("data: " + jsonData + "\n\n");
            out.flush();

            logger.info("📤 Req #{}: Returned loading state", requestId);

        } catch (Exception e) {
            logger.error("Req #{}: Fallback failed: {}", requestId, e.getMessage());
            response.setStatus(504);
        }
    }

    private void updateAllCachesProduction(String key, String data) {
        CompletableFuture.runAsync(() -> {
            putToLocalCacheWithLazyCleanup(localDataCache, key, data, LOCAL_DATA_TTL_MS);
            try {
                redisCache.set(DATA_PREFIX + key, data, REDIS_DATA_TTL);
            } catch (Exception e) {
                logger.debug("Redis cache write failed: {}", e.getMessage());
            }
        });
    }

    private void updateTokenCaches(String tokenKey, Long userId) {
        CompletableFuture.runAsync(() -> {
            putToLocalCacheWithLazyCleanup(localTokenCache, tokenKey, userId, LOCAL_TOKEN_TTL_MS);
            try {
                redisCache.set(TOKEN_PREFIX + tokenKey, userId.toString(), REDIS_TOKEN_TTL);
            } catch (Exception e) {
                logger.debug("Redis token write failed: {}", e.getMessage());
            }
        });
    }

    private void updateSessionCaches(String sessionId, String status) {
        CompletableFuture.runAsync(() -> {
            putToLocalCacheWithLazyCleanup(localSessionCache, sessionId, status, LOCAL_SESSION_TTL_MS);
            try {
                redisCache.set(SESSION_PREFIX + sessionId, status, REDIS_SESSION_TTL);
            } catch (Exception e) {
                logger.debug("Redis session write failed: {}", e.getMessage());
            }
        });
    }

    /**
     * Get production statistics (for health check endpoint)
     */
    public Map<String, Object> getProductionStats() {
        long uptime = System.currentTimeMillis() - startTime;
        long requests = requestCount.get();
        long hits = cacheHits.get();
        long timeoutCount = timeouts.get();

        Map<String, Object> stats = new HashMap<>();
        stats.put("uptime_ms", uptime);
        stats.put("total_requests", requests);
        stats.put("cache_hits", hits);
        stats.put("timeouts", timeoutCount);
        stats.put("cache_hit_rate", requests > 0 ? (hits * 100.0) / requests : 0);
        stats.put("timeout_rate", requests > 0 ? (timeoutCount * 100.0) / requests : 0);
        stats.put("cache_sizes", Map.of(
                "tokens", localTokenCache.size(),
                "data", localDataCache.size(),
                "sessions", localSessionCache.size()));

        return stats;
    }

    // === HELPER CLASSES AND METHODS ===

    private static class LocalCacheEntry<T> {
        final T value;
        final long expiryTime;

        LocalCacheEntry(T value, long ttlMs) {
            this.value = value;
            this.expiryTime = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    private void setSSEHeaders(HttpServletResponse response) {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST");
        response.setHeader("Access-Control-Allow-Headers", "X-Requested-With");
    }

    private String getParameter(HttpServletRequest request, String name, String defaultValue) {
        String value = request.getParameter(name);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    private void sendError(PrintWriter out, String message) {
        try {
            out.print("event: error\ndata: {\"error\": \"" + message.replace("\"", "\\\"") + "\"}\n\n");
            out.flush();
        } catch (Exception e) {
            logger.error("Failed to send error", e);
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String message) {
        try {
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"" + message + "\"}");
            response.getWriter().flush();
        } catch (Exception e) {
            logger.error("Failed to send error response", e);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST");
        response.setHeader("Access-Control-Allow-Headers", "X-Requested-With");
        response.setStatus(200);
    }

    @Override
    public void destroy() {
        logger.info("🚀 Shutting down PRODUCTION SSE Servlet...");

        long uptimeMinutes = (System.currentTimeMillis() - startTime) / 60000;
        long totalRequests = requestCount.get();
        long totalCacheHits = cacheHits.get();
        long totalTimeouts = timeouts.get();

        double cacheHitRate = totalRequests > 0 ? (totalCacheHits * 100.0) / totalRequests : 0;
        double timeoutRate = totalRequests > 0 ? (totalTimeouts * 100.0) / totalRequests : 0;

        logger.info("🚀 FINAL PRODUCTION STATS - Uptime: {}min, Requests: {}, Cache Hit: {:.1f}%, Timeouts: {:.1f}%",
                uptimeMinutes, totalRequests, cacheHitRate, timeoutRate);

        // Note: Thread pools are daemon threads, so they'll shutdown automatically
        // No need for explicit shutdown - keeps destroy() fast for scale-to-zero

        localTokenCache.clear();
        localDataCache.clear();
        localSessionCache.clear();

        logger.info("🚀 Production SSE Servlet shutdown complete");
        super.destroy();
    }
}