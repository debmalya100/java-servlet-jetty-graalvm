package com.example.sse.service;

import com.example.sse.model.DataModels.CommentData;
import com.example.sse.model.DataModels.RegionInfo;
import com.example.sse.model.DataModels.SurveyData;
import com.example.sse.model.DataModels.UserStatus;

import java.util.List;

public interface DataService {
    boolean isInitialized();

    boolean isDatabaseReady();

    boolean isRedisReady();

    UserStatus getUserStatus(Long userId);

    RegionInfo getRegionInfo(String regionCode);

    RegionInfo getRegionInfoByCountryId(String countryId);

    List<SurveyData> getStreamingPolls(String sessionId, Long userId);

    List<CommentData> getComments(Long userId, String typeId, String type);

    void invalidateUserCache(Long userId);

    void invalidateSessionCache(String sessionId, Long userId);

    String getSessionStatus(String sessionId);
}
