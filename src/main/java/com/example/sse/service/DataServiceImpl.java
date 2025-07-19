package com.example.sse.service;

import com.example.sse.cache.CacheProvider;
import com.example.sse.cache.CacheProviderFactory;
import com.example.sse.config.ConfigManager;
import com.example.sse.database.DatabaseProvider;
import com.example.sse.database.DatabaseProviderFactory;
import com.example.sse.model.DataModels.CommentData;
import com.example.sse.model.DataModels.RegionInfo;
import com.example.sse.model.DataModels.SurveyData;
import com.example.sse.model.DataModels.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DataServiceImpl implements DataService {
    private static final Logger logger = LoggerFactory.getLogger(DataServiceImpl.class);
    private static volatile DataServiceImpl instance;
    private static final Object lock = new Object();

    private volatile DatabaseProvider databaseProvider;
    private volatile CacheProvider cacheProvider;
    private final ConfigManager configManager;
    private volatile boolean initialized = false;

    private DataServiceImpl() {
        this.configManager = ConfigManager.getInstance();
        logger.info("DataService instance created");
        initializeAsync();
    }

    public static DataServiceImpl getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new DataServiceImpl();
                }
            }
        }
        return instance;
    }

    private void initializeAsync() {
        // Run initialization in background thread to avoid blocking
        Thread initThread = new Thread(() -> {

            try {
                logger.info("Initializing DataService components...");

                // Initialize database manager
                try {
                    this.databaseProvider = DatabaseProviderFactory.getInstance();
                    if (databaseProvider.isHealthy()) {
                        logger.info("Database manager ready");
                    } else {
                        logger.warn("Database manager not ready");
                    }
                } catch (Exception e) {
                    logger.error("Database manager initialization failed: {}", e.getMessage());
                    this.databaseProvider = null;
                }

                // Initialize Redis manager
                try {
                    this.cacheProvider = CacheProviderFactory.getInstance();
                    if (cacheProvider.isHealthy()) {
                        logger.info("Redis manager ready");
                    } else {
                        logger.warn("Redis manager not healthy");
                    }
                } catch (Exception e) {
                    logger.error("Redis manager initialization failed: {}", e.getMessage());
                    this.cacheProvider = null;
                }

                this.initialized = true;
                logger.info("DataService initialization completed - DB: {}, Redis: {}",
                        (databaseProvider != null), (cacheProvider != null));

            } catch (Exception e) {
                logger.error("DataService initialization failed", e);
                this.initialized = false;
            }
        }, "DataService-Init");

        initThread.setDaemon(true);
        initThread.start();
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public boolean isDatabaseReady() {
        return databaseProvider != null && databaseProvider.isHealthy();
    }

    @Override
    public boolean isRedisReady() {
        return cacheProvider != null && cacheProvider.isHealthy();
    }

    @Override
    public UserStatus getUserStatus(Long userId) {
        if (!isDatabaseReady()) {
            logger.warn("Database not ready, cannot fetch user status for user: {}", userId);
            return null;
        }

        String cacheKey = "user_status_" + userId;
        long ttl = configManager.getLongProperty("cache.user.status.ttl", 3600);

        // Try Redis first if available
        if (isRedisReady()) {
            try {
                UserStatus userStatus = cacheProvider.getObject(cacheKey, UserStatus.class);
                if (userStatus != null) {
                    logger.debug("User status found in cache for user: {}", userId);
                    return userStatus;
                }
            } catch (Exception e) {
                logger.warn("Error reading from Redis cache: {}", e.getMessage());
            }
        }

        // If not in cache, fetch from database
        logger.debug("User status not in cache, fetching from database for user: {}", userId);
        String sql = "SELECT status, country_code FROM user_master WHERE user_master_id = ?";

        try (Connection conn = databaseProvider.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                UserStatus userStatus = new UserStatus(
                        rs.getString("status"),
                        rs.getString("country_code"));

                // Cache for future use if Redis is available
                if (userStatus.getStatus() != null && isRedisReady()) {
                    try {
                        cacheProvider.setObject(cacheKey, userStatus, ttl);
                        logger.debug("User status cached for user: {}", userId);
                    } catch (Exception e) {
                        logger.warn("Error caching user status: {}", e.getMessage());
                    }
                }

                return userStatus;
            }
        } catch (SQLException e) {
            logger.error("Error fetching user status for user: {}", userId, e);
        }

        return null;
    }

    @Override
    public RegionInfo getRegionInfo(String regionCode) {
        if (!isDatabaseReady()) {
            logger.warn("Database not ready, cannot fetch region info for region: {}", regionCode);
            return null;
        }

        String cacheKey = "chRegion_" + regionCode;
        long ttl = configManager.getLongProperty("cache.region.ttl", 86400);

        // Try Redis first if available
        if (isRedisReady()) {
            try {
                RegionInfo regionInfo = cacheProvider.getObject(cacheKey, RegionInfo.class);
                if (regionInfo != null) {
                    logger.debug("Region info found in cache for region: {}", regionCode);
                    return regionInfo;
                }
            } catch (Exception e) {
                logger.warn("Error reading region info from Redis: {}", e.getMessage());
            }
        }

        // If not in cache, fetch from database
        logger.debug("Region info not in cache, fetching from database for region: {}", regionCode);
        String sql = "SELECT time_zone FROM master_country WHERE iso = ?";

        try (Connection conn = databaseProvider.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, regionCode);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                RegionInfo regionInfo = new RegionInfo(rs.getString("time_zone"));

                // Cache for future use if Redis is available
                if (isRedisReady()) {
                    try {
                        cacheProvider.setObject(cacheKey, regionInfo, ttl);
                        logger.debug("Region info cached for region: {}", regionCode);
                    } catch (Exception e) {
                        logger.warn("Error caching region info: {}", e.getMessage());
                    }
                }

                return regionInfo;
            }
        } catch (SQLException e) {
            logger.error("Error fetching region info for region: {}", regionCode, e);
        }

        return null;
    }

    @Override
    public RegionInfo getRegionInfoByCountryId(String countryId) {
        if (!isDatabaseReady()) {
            logger.warn("Database not ready, cannot fetch region info for country: {}", countryId);
            return null;
        }

        String cacheKey = "countryRegion_" + countryId;
        long ttl = configManager.getLongProperty("cache.region.ttl", 86400);

        // Try Redis first if available
        if (isRedisReady()) {
            try {
                RegionInfo regionInfo = cacheProvider.getObject(cacheKey, RegionInfo.class);
                if (regionInfo != null) {
                    logger.debug("Region info found in cache for country: {}", countryId);
                    return regionInfo;
                }
            } catch (Exception e) {
                logger.warn("Error reading region info from Redis: {}", e.getMessage());
            }
        }

        // If not in cache, fetch from database
        logger.debug("Region info not in cache, fetching from database for country: {}", countryId);
        String sql = "SELECT time_zone FROM master_country WHERE country_id = ?";

        try (Connection conn = databaseProvider.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, countryId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                RegionInfo regionInfo = new RegionInfo(rs.getString("time_zone"));

                // Cache for future use if Redis is available
                if (isRedisReady()) {
                    try {
                        cacheProvider.setObject(cacheKey, regionInfo, ttl);
                        logger.debug("Region info cached for country: {}", countryId);
                    } catch (Exception e) {
                        logger.warn("Error caching region info: {}", e.getMessage());
                    }
                }

                return regionInfo;
            }
        } catch (SQLException e) {
            logger.error("Error fetching region info for country: {}", countryId, e);
        }

        return null;
    }

    @Override
    public List<SurveyData> getStreamingPolls(String sessionId, Long userId) {
        if (!isDatabaseReady()) {
            logger.warn("Database not ready, returning empty survey data for session: {}, user: {}", sessionId, userId);
            return new ArrayList<>();
        }

        String cacheKey = "streaming_polls_" + sessionId + "_" + userId;

        // Try Redis first if available
        if (isRedisReady()) {
            try {
                @SuppressWarnings("unchecked")
                List<SurveyData> surveyList = (List<SurveyData>) cacheProvider.getObject(cacheKey, List.class);
                if (surveyList != null) {
                    logger.debug("Streaming polls found in cache for session: {}, user: {}", sessionId, userId);
                    return surveyList;
                }
            } catch (Exception e) {
                logger.warn("Error reading streaming polls from Redis: {}", e.getMessage());
            }
        }

        // If not in cache, fetch from database
        logger.debug("Streaming polls not in cache, fetching from database for session: {}, user: {}", sessionId,
                userId);
        List<SurveyData> surveyList = new ArrayList<>();

        String sql = "SELECT csts.survey_id, csts.is_show, csts.is_answer_show, " +
                "CASE WHEN sua.id IS NOT NULL THEN sua.id ELSE 0 END as is_answered " +
                "FROM cme_streaming_to_survey csts " +
                "LEFT JOIN cme_survey_user_answer sua ON (sua.survey_id = csts.survey_id AND sua.user_master_id = ?) " +
                "WHERE csts.session_id = ?";

        try (Connection conn = databaseProvider.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.setString(2, sessionId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                SurveyData survey = new SurveyData(
                        rs.getLong("survey_id"),
                        rs.getInt("is_show"),
                        rs.getInt("is_answer_show"),
                        rs.getLong("is_answered"));
                surveyList.add(survey);
            }

            // Cache for a short time if Redis is available
            if (isRedisReady()) {
                try {
                    cacheProvider.setObject(cacheKey, surveyList, 30);
                    logger.debug("Streaming polls cached for session: {}, user: {}", sessionId, userId);
                } catch (Exception e) {
                    logger.warn("Error caching streaming polls: {}", e.getMessage());
                }
            }

        } catch (SQLException e) {
            logger.error("Error fetching streaming polls for session: {}, user: {}", sessionId, userId, e);
        }

        return surveyList;
    }

    @Override
    public List<CommentData> getComments(Long userId, String typeId, String type) {
        if (!isDatabaseReady()) {
            logger.warn("Database not ready, returning empty comments for user: {}, typeId: {}, type: {}", userId,
                    typeId, type);
            return new ArrayList<>();
        }

        String cacheKey = "comments_" + userId + "_" + typeId + "_" + type;

        // Try Redis first if available
        if (isRedisReady()) {
            try {
                @SuppressWarnings("unchecked")
                List<CommentData> commentList = (List<CommentData>) cacheProvider.getObject(cacheKey, List.class);
                if (commentList != null) {
                    logger.debug("Comments found in cache for user: {}, typeId: {}, type: {}", userId, typeId, type);
                    return commentList;
                }
            } catch (Exception e) {
                logger.warn("Error reading comments from Redis: {}", e.getMessage());
            }
        }

        // If not in cache, fetch from database
        logger.debug("Comments not in cache, fetching from database for user: {}, typeId: {}, type: {}", userId, typeId,
                type);
        List<CommentData> commentList = new ArrayList<>();

        String sql = "SELECT " +
                "ud.first_name, ud.last_name, ud.profile_image, " +
                "cmnt.knwlg_session_qna_id, cmnt.user_master_id, cmnt.comment, " +
                "cmnt.comment_approve_status, cmnt.status, cmnt.type_id, cmnt.type " +
                // "cmnt.created_at " +
                "FROM knwlg_session_qna cmnt " +
                "LEFT JOIN user_detail ud ON ud.user_master_id = cmnt.user_master_id " +
                "WHERE (cmnt.user_master_id = ? OR cmnt.comment_approve_status = 1) " +
                "AND cmnt.type_id = ? AND cmnt.type = ? " +
                "ORDER BY cmnt.knwlg_session_qna_id ASC";

        try (Connection conn = databaseProvider.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.setString(2, typeId);
            stmt.setString(3, type);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                CommentData comment = new CommentData();
                comment.setId(rs.getLong("knwlg_session_qna_id"));
                comment.setUserMasterId(rs.getLong("user_master_id"));
                comment.setFirstName(rs.getString("first_name"));
                comment.setLastName(rs.getString("last_name"));
                comment.setProfileImage(rs.getString("profile_image"));
                comment.setComment(rs.getString("comment"));
                comment.setCommentApproveStatus(rs.getInt("comment_approve_status"));
                comment.setStatus(rs.getString("status"));
                comment.setTypeId(rs.getString("type_id"));
                comment.setType(rs.getString("type"));
                // Set default timestamp since created_at column doesn't exist in database
                // comment.setCreatedAt(String.valueOf(System.currentTimeMillis())); //
                // Commented out - causing issues

                commentList.add(comment);
            }

            // Cache for a short time if Redis is available
            if (isRedisReady()) {
                try {
                    cacheProvider.setObject(cacheKey, commentList, 30);
                    logger.debug("Comments cached for user: {}, typeId: {}, type: {}", userId, typeId, type);
                } catch (Exception e) {
                    logger.warn("Error caching comments: {}", e.getMessage());
                }
            }

        } catch (SQLException e) {
            logger.error("Error fetching comments for user: {}, typeId: {}, type: {}", userId, typeId, type, e);
        }

        return commentList;
    }

    @Override
    public void invalidateUserCache(Long userId) {
        if (isRedisReady()) {
            try {
                String userStatusKey = "user_status_" + userId;
                cacheProvider.delete(userStatusKey);
                logger.debug("Invalidated user cache for user: {}", userId);
            } catch (Exception e) {
                logger.warn("Error invalidating user cache: {}", e.getMessage());
            }
        }
    }

    @Override
    public void invalidateSessionCache(String sessionId, Long userId) {
        if (isRedisReady()) {
            try {
                String pollsKey = "streaming_polls_" + sessionId + "_" + userId;
                String commentsKey = "comments_" + userId + "_" + sessionId + "_session";
                cacheProvider.delete(pollsKey);
                cacheProvider.delete(commentsKey);
                logger.debug("Invalidated session cache for session: {}, user: {}", sessionId, userId);
            } catch (Exception e) {
                logger.warn("Error invalidating session cache: {}", e.getMessage());
            }
        }
    }

    @Override
    public String getSessionStatus(String sessionId) {
        if (!isDatabaseReady()) {
            logger.warn("Database not ready, cannot fetch session status for session: {}", sessionId);
            throw new RuntimeException("Database service is not available");
        }

        // Validate session ID format
        if (sessionId == null || sessionId.trim().isEmpty()) {
            logger.warn("Invalid session ID provided: {}", sessionId);
            throw new IllegalArgumentException("Session ID cannot be null or empty");
        }

        String cacheKey = "openapi_detail_sse_session_status_" + sessionId;
        long ttl = configManager.getLongProperty("cache.session.status.ttl", 30);

        // Try Redis first if available
        if (isRedisReady()) {
            try {
                String cachedStatus = cacheProvider.get(cacheKey);
                if (cachedStatus != null) {
                    logger.debug("Session status found in cache for session: {}", sessionId);
                    return cachedStatus;
                }
            } catch (Exception e) {
                logger.warn("Error reading session status from Redis for session: {} - {}", sessionId, e.getMessage());
                // Continue to database fallback
            }
        }

        // If not in cache, fetch from database
        logger.debug("Session status not in cache, fetching from database for session: {}", sessionId);
        String sql = "SELECT session_status FROM knwlg_sessions_V1 WHERE session_id = ?";

        try (Connection conn = databaseProvider.getConnection()) {
            if (conn == null) {
                logger.error("Unable to obtain database connection for session: {}", sessionId);
                throw new RuntimeException("Database connection is not available");
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, sessionId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String status = rs.getString("session_status");

                        // Cache for future use if Redis is available and status is not null
                        if (status != null && isRedisReady()) {
                            try {
                                cacheProvider.set(cacheKey, status, ttl);
                                logger.debug("Session status cached for session: {}", sessionId);
                            } catch (Exception e) {
                                logger.warn("Error caching session status for session: {} - {}", sessionId,
                                        e.getMessage());
                                // Don't fail the request if caching fails
                            }
                        }

                        return status;
                    } else {
                        logger.info("No session found with ID: {}", sessionId);
                        return null; // Session not found
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Database error while fetching session status for session: {} - Error: {}", sessionId,
                    e.getMessage());

            // Check if it's a connection issue vs. other SQL issues
            if (e.getMessage().contains("connection") || e.getMessage().contains("timeout")) {
                throw new RuntimeException("Database connection error", e);
            } else {
                throw new RuntimeException("Database query error", e);
            }
        } catch (Exception e) {
            logger.error("Unexpected error fetching session status for session: {} - {}", sessionId, e.getMessage());
            throw new RuntimeException("Unexpected database error", e);
        }
    }
}