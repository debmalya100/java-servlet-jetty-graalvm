package com.example.sse.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class DataModels {

    public static class UserStatus {
        private String status;
        @JsonProperty("country_code")
        private String countryCode;

        public UserStatus() {
        }

        public UserStatus(String status, String countryCode) {
            this.status = status;
            this.countryCode = countryCode;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getCountryCode() {
            return countryCode;
        }

        public void setCountryCode(String countryCode) {
            this.countryCode = countryCode;
        }
    }

    public static class SessionStatus {
        @JsonProperty("session_id")
        private String sessionId;
        @JsonProperty("session_status")
        private String sessionStatus;
        private long timestamp;

        public SessionStatus() {
            this.timestamp = System.currentTimeMillis();
        }

        public SessionStatus(String sessionId, String sessionStatus) {
            this.sessionId = sessionId;
            this.sessionStatus = sessionStatus;
            this.timestamp = System.currentTimeMillis();
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getSessionStatus() {
            return sessionStatus;
        }

        public void setSessionStatus(String sessionStatus) {
            this.sessionStatus = sessionStatus;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }

    public static class RegionInfo {
        @JsonProperty("time_zone")
        private String timeZone;

        public RegionInfo() {
        }

        public RegionInfo(String timeZone) {
            this.timeZone = timeZone;
        }

        public String getTimeZone() {
            return timeZone;
        }

        public void setTimeZone(String timeZone) {
            this.timeZone = timeZone;
        }
    }

    public static class SurveyData {
        @JsonProperty("survey_id")
        private Long surveyId;
        @JsonProperty("is_show")
        private Integer isShow;
        @JsonProperty("is_answer_show")
        private Integer isAnswerShow;
        @JsonProperty("is_answered")
        private Long isAnswered;

        public SurveyData() {
        }

        public SurveyData(Long surveyId, Integer isShow, Integer isAnswerShow, Long isAnswered) {
            this.surveyId = surveyId;
            this.isShow = isShow;
            this.isAnswerShow = isAnswerShow;
            this.isAnswered = isAnswered;
        }

        public Long getSurveyId() {
            return surveyId;
        }

        public void setSurveyId(Long surveyId) {
            this.surveyId = surveyId;
        }

        public Integer getIsShow() {
            return isShow;
        }

        public void setIsShow(Integer isShow) {
            this.isShow = isShow;
        }

        public Integer getIsAnswerShow() {
            return isAnswerShow;
        }

        public void setIsAnswerShow(Integer isAnswerShow) {
            this.isAnswerShow = isAnswerShow;
        }

        public Long getIsAnswered() {
            return isAnswered;
        }

        public void setIsAnswered(Long isAnswered) {
            this.isAnswered = isAnswered;
        }
    }

    public static class CommentData {
        @JsonProperty("knwlg_session_qna_id")
        private Long id;
        @JsonProperty("user_master_id")
        private Long userMasterId;
        @JsonProperty("first_name")
        private String firstName;
        @JsonProperty("last_name")
        private String lastName;
        @JsonProperty("profile_image")
        private String profileImage;
        private String comment;
        @JsonProperty("comment_approve_status")
        private Integer commentApproveStatus;
        private String status;
        @JsonProperty("type_id")
        private String typeId;
        private String type;
        @JsonProperty("created_at")
        private String createdAt;

        public CommentData() {
        }

        // Getters and setters
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getUserMasterId() {
            return userMasterId;
        }

        public void setUserMasterId(Long userMasterId) {
            this.userMasterId = userMasterId;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getProfileImage() {
            return profileImage;
        }

        public void setProfileImage(String profileImage) {
            this.profileImage = profileImage;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public Integer getCommentApproveStatus() {
            return commentApproveStatus;
        }

        public void setCommentApproveStatus(Integer commentApproveStatus) {
            this.commentApproveStatus = commentApproveStatus;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getTypeId() {
            return typeId;
        }

        public void setTypeId(String typeId) {
            this.typeId = typeId;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static class SSEResponse {
        @JsonProperty("comment_data")
        private List<CommentData> commentData;
        @JsonProperty("survey_data")
        private List<SurveyData> surveyData;
        private long timestamp;

        public SSEResponse() {
            this.timestamp = System.currentTimeMillis();
        }

        public SSEResponse(List<CommentData> commentData, List<SurveyData> surveyData) {
            this.commentData = commentData;
            this.surveyData = surveyData;
            this.timestamp = System.currentTimeMillis();
        }

        public List<CommentData> getCommentData() {
            return commentData;
        }

        public void setCommentData(List<CommentData> commentData) {
            this.commentData = commentData;
        }

        public List<SurveyData> getSurveyData() {
            return surveyData;
        }

        public void setSurveyData(List<SurveyData> surveyData) {
            this.surveyData = surveyData;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }

    public static class ErrorResponse {
        private String message;
        private int code;
        private long timestamp;

        public ErrorResponse(String message, int code) {
            this.message = message;
            this.code = code;
            this.timestamp = System.currentTimeMillis();
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }
}