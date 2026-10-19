package com.rag.assistant.module.dashboard.service;

import com.rag.assistant.module.dashboard.model.vo.DashboardStatsVO;

import java.util.List;
import java.util.Map;

public interface DashboardService {

    DashboardStatsVO getStats(Long userId);

    List<Map<String, Object>> getRecentActivity(Long userId);

    List<Map<String, Object>> search(Long userId, String keyword);
}