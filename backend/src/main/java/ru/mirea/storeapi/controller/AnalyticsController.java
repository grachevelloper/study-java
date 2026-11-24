package ru.mirea.storeapi.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.mirea.storeapi.dto.AnalyticsDtos.AnalyticsResponse;
import ru.mirea.storeapi.service.AnalyticsService;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {
    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/api/analytics/summary")
    public AnalyticsResponse summary() {
        return analytics.summary();
    }
}
