package com.storybook.aikidstorybook.controller;

import com.storybook.aikidstorybook.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MetricsController {

    private static final Logger logger = LoggerFactory.getLogger(MetricsController.class);

    @Autowired
    private MetricsService metricsService;

    @GetMapping("/metrics/summary")
    public ResponseEntity<Map<String, Object>> getMetricsSummary() {
        MetricsService.MetricsSummary summary = metricsService.getMetricsSummary();
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalOperations", summary.getTotalOperations());
        response.put("totalSuccesses", summary.getTotalSuccesses());
        response.put("totalFailures", summary.getTotalFailures());
        response.put("successRate", String.format("%.2f%%", summary.getSuccessRate()));
        response.put("averageDuration", String.format("%.2f ms", summary.getAverageDuration()));
        response.put("timestamp", summary.getTimestamp());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/metrics/operation/{operationType}")
    public ResponseEntity<Map<String, Object>> getOperationMetrics(@PathVariable String operationType) {
        MetricsService.OperationMetrics metrics = metricsService.getOperationMetrics(operationType);
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("operationType", metrics.getOperationType());
        response.put("total", metrics.getTotal());
        response.put("successes", metrics.getSuccesses());
        response.put("failures", metrics.getFailures());
        response.put("successRate", String.format("%.2f%%", metrics.getSuccessRate()));
        response.put("averageDuration", String.format("%.2f ms", metrics.getAverageDuration()));
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/metrics/reset")
    public ResponseEntity<Map<String, String>> resetMetrics() {
        metricsService.resetMetrics();
        logger.info("Metrics have been reset");
        return ResponseEntity.ok(Map.of("status", "Metrics reset successfully"));
    }
}

@Controller
class MetricsGraphQLController {

    @Autowired
    private MetricsService metricsService;

    @QueryMapping
    public Map<String, Object> getMetricsSummaryGQL() {
        MetricsService.MetricsSummary summary = metricsService.getMetricsSummary();
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalOperations", summary.getTotalOperations());
        response.put("totalSuccesses", summary.getTotalSuccesses());
        response.put("totalFailures", summary.getTotalFailures());
        response.put("successRate", summary.getSuccessRate());
        response.put("averageDuration", summary.getAverageDuration());
        response.put("timestamp", summary.getTimestamp().toString());
        
        return response;
    }

    @QueryMapping
    public Map<String, Object> getOperationMetricsGQL(@Argument String operationType) {
        MetricsService.OperationMetrics metrics = metricsService.getOperationMetrics(operationType);
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("operationType", metrics.getOperationType());
        response.put("total", metrics.getTotal());
        response.put("successes", metrics.getSuccesses());
        response.put("failures", metrics.getFailures());
        response.put("successRate", metrics.getSuccessRate());
        response.put("averageDuration", metrics.getAverageDuration());
        
        return response;
    }
}
