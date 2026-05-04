package com.storybook.aikidstorybook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {

    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);

    private final ConcurrentHashMap<String, AtomicInteger> successCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> failureCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> totalDurations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> operationStartTimes = new ConcurrentHashMap<>();

    /**
     * Track the start of an operation
     */
    public void recordOperationStart(Long bookId, String operationType) {
        String key = operationType + "_" + bookId;
        operationStartTimes.put(key, LocalDateTime.now());
        logger.info("Operation started: {} for book ID: {}", operationType, bookId);
    }

    /**
     * Record successful operation completion
     */
    public void recordOperationSuccess(Long bookId, String operationType) {
        String countKey = operationType + "_success";
        String durationKey = operationType + "_duration";
        String startKey = operationType + "_" + bookId;

        successCounts.computeIfAbsent(countKey, k -> new AtomicInteger(0)).incrementAndGet();

        LocalDateTime startTime = operationStartTimes.remove(startKey);
        if (startTime != null) {
            long duration = ChronoUnit.MILLIS.between(startTime, LocalDateTime.now());
            totalDurations.computeIfAbsent(durationKey, k -> new AtomicLong(0)).addAndGet(duration);
            logger.info("Operation completed successfully: {} for book ID: {} in {} ms", 
                       operationType, bookId, duration);
        }
    }

    /**
     * Record operation failure
     */
    public void recordOperationFailure(Long bookId, String operationType, String errorMessage) {
        String countKey = operationType + "_failure";
        failureCounts.computeIfAbsent(countKey, k -> new AtomicInteger(0)).incrementAndGet();

        operationStartTimes.remove(operationType + "_" + bookId);
        logger.error("Operation failed: {} for book ID: {} - Error: {}", 
                    operationType, bookId, errorMessage);
    }

    /**
     * Record page-specific metrics
     */
    public void recordPageMetric(Long bookId, int pageNumber, String metricType, String status) {
        String key = "book_" + bookId + "_page_" + pageNumber + "_" + metricType + "_" + status;
        logger.debug("Page metric recorded: {}", key);
    }

    /**
     * Get metrics summary as structured data
     */
    public MetricsSummary getMetricsSummary() {
        int totalSuccesses = successCounts.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();
        int totalFailures = failureCounts.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();

        long totalDuration = totalDurations.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();

        int totalOperations = totalSuccesses + totalFailures;
        double successRate = totalOperations > 0 ? (double) totalSuccesses / totalOperations * 100 : 0;
        double avgDuration = totalSuccesses > 0 ? (double) totalDuration / totalSuccesses : 0;

        return new MetricsSummary(
                totalSuccesses,
                totalFailures,
                totalOperations,
                successRate,
                avgDuration,
                LocalDateTime.now()
        );
    }

    /**
     * Get operation-specific metrics
     */
    public OperationMetrics getOperationMetrics(String operationType) {
        String successKey = operationType + "_success";
        String failureKey = operationType + "_failure";
        String durationKey = operationType + "_duration";

        int successes = successCounts.getOrDefault(successKey, new AtomicInteger(0)).get();
        int failures = failureCounts.getOrDefault(failureKey, new AtomicInteger(0)).get();
        long totalDuration = totalDurations.getOrDefault(durationKey, new AtomicLong(0)).get();

        int total = successes + failures;
        double successRate = total > 0 ? (double) successes / total * 100 : 0;
        double avgDuration = successes > 0 ? (double) totalDuration / successes : 0;

        return new OperationMetrics(
                operationType,
                successes,
                failures,
                total,
                successRate,
                avgDuration
        );
    }

    /**
     * Log metrics summary periodically
     */
    public void logMetricsSummary() {
        MetricsSummary summary = getMetricsSummary();
        logger.info("=== Metrics Summary ===");
        logger.info("Total Operations: {}", summary.getTotalOperations());
        logger.info("Successful: {} | Failed: {}", summary.getTotalSuccesses(), summary.getTotalFailures());
        logger.info("Success Rate: {:.2f}%", summary.getSuccessRate());
        logger.info("Average Duration: {:.2f} ms", summary.getAverageDuration());
    }

    /**
     * Reset all metrics
     */
    public void resetMetrics() {
        successCounts.clear();
        failureCounts.clear();
        totalDurations.clear();
        operationStartTimes.clear();
        logger.info("All metrics have been reset");
    }

    /**
     * Metrics Summary DTO
     */
    public static class MetricsSummary {
        private final int totalSuccesses;
        private final int totalFailures;
        private final int totalOperations;
        private final double successRate;
        private final double averageDuration;
        private final LocalDateTime timestamp;

        public MetricsSummary(int totalSuccesses, int totalFailures, int totalOperations,
                             double successRate, double averageDuration, LocalDateTime timestamp) {
            this.totalSuccesses = totalSuccesses;
            this.totalFailures = totalFailures;
            this.totalOperations = totalOperations;
            this.successRate = successRate;
            this.averageDuration = averageDuration;
            this.timestamp = timestamp;
        }

        public int getTotalSuccesses() { return totalSuccesses; }
        public int getTotalFailures() { return totalFailures; }
        public int getTotalOperations() { return totalOperations; }
        public double getSuccessRate() { return successRate; }
        public double getAverageDuration() { return averageDuration; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    /**
     * Operation Metrics DTO
     */
    public static class OperationMetrics {
        private final String operationType;
        private final int successes;
        private final int failures;
        private final int total;
        private final double successRate;
        private final double averageDuration;

        public OperationMetrics(String operationType, int successes, int failures, int total,
                               double successRate, double averageDuration) {
            this.operationType = operationType;
            this.successes = successes;
            this.failures = failures;
            this.total = total;
            this.successRate = successRate;
            this.averageDuration = averageDuration;
        }

        public String getOperationType() { return operationType; }
        public int getSuccesses() { return successes; }
        public int getFailures() { return failures; }
        public int getTotal() { return total; }
        public double getSuccessRate() { return successRate; }
        public double getAverageDuration() { return averageDuration; }
    }
}
