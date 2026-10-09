package org.example.aispingboot.controller;

import org.example.aispingboot.common.Result;
import org.example.aispingboot.service.DataAnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/data-analytics")
public class DataAnalyticsController {

    @Autowired
    private DataAnalyticsService dataAnalyticsService;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(dataAnalyticsService.overview());
    }
}