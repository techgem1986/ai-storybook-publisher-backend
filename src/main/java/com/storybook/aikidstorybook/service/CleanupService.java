package com.storybook.aikidstorybook.service;

import com.storybook.aikidstorybook.entity.StoryBook;
import com.storybook.aikidstorybook.repository.StoryBookRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class CleanupService {
    private static final Logger logger = LoggerFactory.getLogger(CleanupService.class);

    @Autowired
    private StoryBookRepository storyBookRepository;

    @Value("${storybook.output-dir:./storage/storybooks}")
    private String outputDirectory;

    @Value("${cleanup.retention-days:7}")
    private int retentionDays;

    @PostConstruct
    public void cleanupStuckBooks() {
        logger.info("Cleaning up stuck book generation requests on startup...");
        markStuckBooksFailed();
        cleanupOldPdfFiles();
    }

    @Scheduled(fixedDelayString = "${cleanup.interval-ms:3600000}")
    public void scheduledCleanup() {
        logger.info("Running scheduled cleanup of stuck books and old PDF files...");
        markStuckBooksFailed();
        cleanupOldPdfFiles();
    }

    private void markStuckBooksFailed() {
        List<StoryBook> allBooks = storyBookRepository.findAll();
        for (StoryBook book : allBooks) {
            boolean updated = false;
            if ("GENERATING".equals(book.getStatus()) || "PENDING".equals(book.getStatus())) {
                book.setStatus("FAILED");
                book.setLastStatus("Error: Server restarted during generation.");
                updated = true;
            }
            if ("IN_PROGRESS".equals(book.getPdfStatus())) {
                book.setPdfStatus("FAILED");
                book.setLastStatus("Error: Server restarted during PDF generation.");
                updated = true;
            }
            if (updated) {
                storyBookRepository.save(book);
                logger.info("Marked book ID {} as FAILED due to incomplete workflow", book.getId());
            }
        }
    }

    private void cleanupOldPdfFiles() {
        try {
            Path directory = Paths.get(outputDirectory);
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }

            Instant expiration = ZonedDateTime.now(ZoneOffset.UTC).minusDays(retentionDays).toInstant();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.pdf")) {
                for (Path path : stream) {
                    try {
                        Instant lastModified = Files.getLastModifiedTime(path).toInstant();
                        if (lastModified.isBefore(expiration)) {
                            Files.deleteIfExists(path);
                            logger.info("Deleted old PDF file: {}", path);
                        }
                    } catch (IOException inner) {
                        logger.warn("Unable to delete old PDF file {}: {}", path, inner.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to clean up old PDF files from {}: {}", outputDirectory, e.getMessage());
        }
    }
}
