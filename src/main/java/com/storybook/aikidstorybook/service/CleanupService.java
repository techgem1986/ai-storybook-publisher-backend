package com.storybook.aikidstorybook.service;

import com.storybook.aikidstorybook.entity.StoryBook;
import com.storybook.aikidstorybook.repository.StoryBookRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CleanupService {
    private static final Logger logger = LoggerFactory.getLogger(CleanupService.class);

    @Autowired
    private StoryBookRepository storyBookRepository;

    @PostConstruct
    public void cleanupStuckBooks() {
        logger.info("Cleaning up stuck book generation requests...");
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
                logger.info("Marked book ID {} as FAILED due to server restart", book.getId());
            }
        }
    }
}