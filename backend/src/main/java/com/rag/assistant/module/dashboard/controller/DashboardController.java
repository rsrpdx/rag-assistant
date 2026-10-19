package com.rag.assistant.module.dashboard.controller;

import com.rag.assistant.common.model.R;
import com.rag.assistant.module.dashboard.model.vo.DashboardStatsVO;
import com.rag.assistant.module.dashboard.service.DashboardService;
import com.rag.assistant.security.model.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public R<DashboardStatsVO> stats(@AuthenticationPrincipal SecurityUser securityUser) {
        return R.ok(dashboardService.getStats(securityUser.getUserId()));
    }

    @GetMapping("/recent-activity")
    public R<List<Map<String, Object>>> recentActivity(@AuthenticationPrincipal SecurityUser securityUser) {
        return R.ok(dashboardService.getRecentActivity(securityUser.getUserId()));
    }

    @GetMapping("/search")
    public R<List<Map<String, Object>>> search(
            @RequestParam String q,
            @AuthenticationPrincipal SecurityUser securityUser) {
        if (q == null || q.isBlank()) {
            return R.ok(List.of());
        }
        return R.ok(dashboardService.search(securityUser.getUserId(), q.trim()));
    }
}