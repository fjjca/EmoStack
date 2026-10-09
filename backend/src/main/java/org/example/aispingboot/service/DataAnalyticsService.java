package org.example.aispingboot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataAnalyticsService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("systemOverview", systemOverview());
        result.put("emotionTrend", emotionTrend());
        result.put("consultationStats", consultationStats());
        result.put("userActivity", userActivity());
        return result;
    }

    private Map<String, Object> systemOverview() {
        Map<String, Object> map = new LinkedHashMap<>();

        Long totalUsers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user", Long.class);
        Long totalDiaries = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM emotion_diary", Long.class);
        Long todayNewDiaries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM emotion_diary WHERE diary_date = CURDATE()", Long.class);
        Long totalSessions = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM consultation_session", Long.class);
        Long todayNewSessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consultation_session WHERE started_at >= CURDATE()", Long.class);
        Double avgMoodScore = jdbcTemplate.queryForObject(
                "SELECT ROUND(AVG(mood_score), 2) FROM emotion_diary", Double.class);
        Long activeUsers = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM (" +
                        " SELECT user_id FROM consultation_session WHERE started_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)" +
                        " UNION" +
                        " SELECT user_id FROM emotion_diary WHERE diary_date >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)" +
                        ") t", Long.class);

        map.put("totalUsers", totalUsers != null ? totalUsers : 0);
        map.put("activeUsers", activeUsers != null ? activeUsers : 0);
        map.put("totalDiaries", totalDiaries != null ? totalDiaries : 0);
        map.put("todayNewDiaries", todayNewDiaries != null ? todayNewDiaries : 0);
        map.put("totalSessions", totalSessions != null ? totalSessions : 0);
        map.put("todayNewSessions", todayNewSessions != null ? todayNewSessions : 0);
        map.put("avgMoodScore", avgMoodScore != null ? avgMoodScore : 0);
        return map;
    }

    private List<Map<String, Object>> emotionTrend() {
        List<LocalDate> days = lastNDays(7);
        Map<String, Double> avgMap = new HashMap<>();
        Map<String, Long> countMap = new HashMap<>();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DATE_FORMAT(diary_date, '%Y-%m-%d') AS d, AVG(mood_score) AS avg_score, COUNT(*) AS cnt " +
                        "FROM emotion_diary WHERE diary_date >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) GROUP BY diary_date");
        for (Map<String, Object> row : rows) {
            String d = String.valueOf(row.get("d"));
            avgMap.put(d, toDouble(row.get("avg_score")));
            countMap.put(d, toLong(row.get("cnt")));
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        for (LocalDate day : days) {
            String key = day.format(DATE_FMT);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", key);
            item.put("avgMoodScore", avgMap.getOrDefault(key, 0.0));
            item.put("recordCount", countMap.getOrDefault(key, 0L));
            trend.add(item);
        }
        return trend;
    }

    private Map<String, Object> consultationStats() {
        Map<String, Object> map = new LinkedHashMap<>();
        Long totalSessions = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM consultation_session", Long.class);
        map.put("totalSessions", totalSessions != null ? totalSessions : 0);
        map.put("avgDurationMinutes", 0);

        List<LocalDate> days = lastNDays(7);
        Map<String, Long> sessionCountMap = new HashMap<>();
        Map<String, Long> userCountMap = new HashMap<>();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DATE_FORMAT(started_at, '%Y-%m-%d') AS d, COUNT(*) AS session_count, COUNT(DISTINCT user_id) AS user_count " +
                        "FROM consultation_session WHERE started_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) GROUP BY DATE_FORMAT(started_at, '%Y-%m-%d')");
        for (Map<String, Object> row : rows) {
            String d = String.valueOf(row.get("d"));
            sessionCountMap.put(d, toLong(row.get("session_count")));
            userCountMap.put(d, toLong(row.get("user_count")));
        }

        List<Map<String, Object>> dailyTrend = new ArrayList<>();
        for (LocalDate day : days) {
            String key = day.format(DATE_FMT);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", key);
            item.put("sessionCount", sessionCountMap.getOrDefault(key, 0L));
            item.put("userCount", userCountMap.getOrDefault(key, 0L));
            dailyTrend.add(item);
        }
        map.put("dailyTrend", dailyTrend);
        return map;
    }

    private List<Map<String, Object>> userActivity() {
        List<LocalDate> days = lastNDays(7);
        Map<String, Long> newUsersMap = new HashMap<>();
        Map<String, Long> diaryUsersMap = new HashMap<>();
        Map<String, Long> consultationUsersMap = new HashMap<>();
        Map<String, Long> activeUsersMap = new HashMap<>();

        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS d, COUNT(*) AS cnt FROM user " +
                        "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d')")) {
            newUsersMap.put(String.valueOf(row.get("d")), toLong(row.get("cnt")));
        }
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT DATE_FORMAT(diary_date, '%Y-%m-%d') AS d, COUNT(DISTINCT user_id) AS cnt FROM emotion_diary " +
                        "WHERE diary_date >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) GROUP BY diary_date")) {
            diaryUsersMap.put(String.valueOf(row.get("d")), toLong(row.get("cnt")));
        }
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT DATE_FORMAT(started_at, '%Y-%m-%d') AS d, COUNT(DISTINCT user_id) AS cnt FROM consultation_session " +
                        "WHERE started_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) GROUP BY DATE_FORMAT(started_at, '%Y-%m-%d')")) {
            consultationUsersMap.put(String.valueOf(row.get("d")), toLong(row.get("cnt")));
        }
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT d, COUNT(DISTINCT user_id) AS cnt FROM (" +
                        " SELECT diary_date AS d, user_id FROM emotion_diary WHERE diary_date >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)" +
                        " UNION" +
                        " SELECT DATE(started_at) AS d, user_id FROM consultation_session WHERE started_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)" +
                        ") t GROUP BY d")) {
            activeUsersMap.put(String.valueOf(row.get("d")), toLong(row.get("cnt")));
        }

        List<Map<String, Object>> activity = new ArrayList<>();
        for (LocalDate day : days) {
            String key = day.format(DATE_FMT);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", key);
            item.put("activeUsers", activeUsersMap.getOrDefault(key, 0L));
            item.put("newUsers", newUsersMap.getOrDefault(key, 0L));
            item.put("diaryUsers", diaryUsersMap.getOrDefault(key, 0L));
            item.put("consultationUsers", consultationUsersMap.getOrDefault(key, 0L));
            activity.add(item);
        }
        return activity;
    }

    private List<LocalDate> lastNDays(int n) {
        LocalDate today = LocalDate.now();
        List<LocalDate> days = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            days.add(today.minusDays(i));
        }
        return days;
    }

    private Double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number n) return n.doubleValue();
        return Double.valueOf(String.valueOf(value));
    }

    private Long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return Long.valueOf(String.valueOf(value));
    }
}