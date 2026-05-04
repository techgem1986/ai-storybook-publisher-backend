package com.storybook.aikidstorybook.controller;

import com.storybook.aikidstorybook.entity.StoryBook;
import com.storybook.aikidstorybook.entity.StoryPage;
import com.storybook.aikidstorybook.repository.StoryBookRepository;
import com.storybook.aikidstorybook.service.StoryGenerationService;
import com.storybook.aikidstorybook.service.InputSanitizationService;
import com.storybook.aikidstorybook.service.MetricsService;
import com.storybook.aikidstorybook.service.MarketplaceExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class StoryBookResolver {

    private static final Logger logger = LoggerFactory.getLogger(StoryBookResolver.class);

    @Autowired
    private StoryBookRepository storyBookRepository;

    @Autowired
    private StoryGenerationService storyGenerationService;

    @Autowired
    private InputSanitizationService sanitizationService;

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private MarketplaceExportService marketplaceExportService;

    @QueryMapping
    public StoryBook getStoryBook(@Argument Long id) {
        StoryBook book = storyBookRepository.findByIdWithPages(id).orElse(null);
        return normalizePages(book);
    }

    @QueryMapping
    public List<StoryBook> getAllStoryBooks() {
        return storyBookRepository.findAll().stream()
                .map(this::normalizePages)
                .toList();
    }

    private StoryBook normalizePages(StoryBook storyBook) {
        if (storyBook == null || storyBook.getPages() == null) {
            return storyBook;
        }

        Map<Integer, StoryPage> uniquePages = new LinkedHashMap<>();
        for (StoryPage page : storyBook.getPages()) {
            if (!uniquePages.containsKey(page.getPageNumber())) {
                uniquePages.put(page.getPageNumber(), page);
            }
        }

        storyBook.getPages().clear();
        storyBook.getPages().addAll(uniquePages.values());
        return storyBook;
    }

    @MutationMapping
    public StoryBook generateStoryBook(
            @Argument String title,
            @Argument String description,
            @Argument String ageGroup,
            @Argument String writingStyle,
            @Argument String genre,
            @Argument String illustrationStyle,
            @Argument Integer numberOfPages,
            @Argument String exportPreset) {
        logger.info("Received request to generate story book with title: {}", title);
        
        try {
            // Sanitize inputs
            String sanitizedTitle = sanitizationService.sanitizeTitle(title);
            String sanitizedDescription = sanitizationService.sanitizeDescription(description);
            
            // Validate enum values
            String[] ageGroups = {"3-5", "5-7", "7-9", "9-12"};
            String[] genres = {"fantasy", "adventure", "educational", "bedtime"};
            String validatedAgeGroup = sanitizationService.validateEnumValue(ageGroup, ageGroups, "ageGroup");
            String validatedGenre = sanitizationService.validateEnumValue(genre, genres, "genre");
            int validatedNumberOfPages = sanitizationService.validateNumberInRange(numberOfPages, 1, 50, "numberOfPages");
            
            StoryBook storyBook = new StoryBook(sanitizedTitle);
            storyBook.setDescription(sanitizedDescription);
            storyBook.setAgeGroup(validatedAgeGroup);
            storyBook.setWritingStyle(writingStyle);
            storyBook.setGenre(validatedGenre);
            storyBook.setIllustrationStyle(illustrationStyle);
            storyBook.setNumberOfPages(validatedNumberOfPages);
            storyBook.setExportPreset(exportPreset != null ? exportPreset : "digital-download");
            
            storyBook = storyBookRepository.save(storyBook);
            logger.info("Saved initial story book record with ID: {}", storyBook.getId());

            metricsService.recordOperationStart(storyBook.getId(), "story_generation");
            
            try {
                storyGenerationService.generateCompleteStoryBook(storyBook.getId());
                logger.info("Triggered async generation for book ID: {}", storyBook.getId());
            } catch (Exception e) {
                logger.error("Failed to trigger async generation for book ID: {}", storyBook.getId(), e);
                metricsService.recordOperationFailure(storyBook.getId(), "story_generation", e.getMessage());
            }

            return storyBook;
        } catch (IllegalArgumentException e) {
            logger.error("Input validation failed: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid input: " + e.getMessage());
        }
    }

    @MutationMapping
    public StoryBook generateStoryDraft(
            @Argument String title,
            @Argument String description,
            @Argument String ageGroup,
            @Argument String writingStyle,
            @Argument String genre,
            @Argument String illustrationStyle,
            @Argument Integer numberOfPages,
            @Argument String exportPreset) {
        logger.info("Received request to generate story draft with title: {}", title);
        
        try {
            // Sanitize inputs
            String sanitizedTitle = sanitizationService.sanitizeTitle(title);
            String sanitizedDescription = sanitizationService.sanitizeDescription(description);
            
            // Validate enum values
            String[] ageGroups = {"3-5", "5-7", "7-9", "9-12"};
            String[] genres = {"fantasy", "adventure", "educational", "bedtime"};
            String validatedAgeGroup = sanitizationService.validateEnumValue(ageGroup, ageGroups, "ageGroup");
            String validatedGenre = sanitizationService.validateEnumValue(genre, genres, "genre");
            int validatedNumberOfPages = sanitizationService.validateNumberInRange(numberOfPages, 1, 50, "numberOfPages");
            
            StoryBook storyBook = new StoryBook(sanitizedTitle);
            storyBook.setDescription(sanitizedDescription);
            storyBook.setAgeGroup(validatedAgeGroup);
            storyBook.setWritingStyle(writingStyle);
            storyBook.setGenre(validatedGenre);
            storyBook.setIllustrationStyle(illustrationStyle);
            storyBook.setNumberOfPages(validatedNumberOfPages);
            storyBook.setExportPreset(exportPreset != null ? exportPreset : "digital-download");
            
            storyBook = storyBookRepository.save(storyBook);
            
            metricsService.recordOperationStart(storyBook.getId(), "story_draft");
            
            try {
                storyGenerationService.generateStoryDraft(storyBook.getId());
            } catch (Exception e) {
                logger.error("Failed to trigger story drafting for book ID: {}", storyBook.getId(), e);
                metricsService.recordOperationFailure(storyBook.getId(), "story_draft", e.getMessage());
            }
            
            return storyBook;
        } catch (IllegalArgumentException e) {
            logger.error("Input validation failed: {}", e.getMessage());
            throw new IllegalArgumentException("Invalid input: " + e.getMessage());
        }
    }

    @MutationMapping
    public StoryBook updateStoryContent(
            @Argument Long id,
            @Argument List<Map<String, Object>> pages,
            @Argument String fontColor,
            @Argument Integer fontSize,
            @Argument String fontStyle,
            @Argument String textBackground,
            @Argument String illustrationStyle) {
        logger.info("Received request to update story content for book ID: {}", id);
        return storyGenerationService.saveReviewEdits(id, pages, fontColor, fontSize, fontStyle, textBackground, illustrationStyle);
    }

    @MutationMapping
    public StoryPage regeneratePageImage(@Argument Long id, @Argument Integer pageNumber) {
        logger.info("Received request to regenerate image for book ID: {} page {}", id, pageNumber);
        return storyGenerationService.regeneratePageImage(id, pageNumber);
    }

    @MutationMapping
    public StoryBook finalizeAndGenerateImages(@Argument Long id) {
        StoryBook storyBook = storyBookRepository.findById(id).orElseThrow();
        
        try {
            storyGenerationService.generateIllustrationsAndPdf(storyBook.getId());
        } catch (Exception e) {
            logger.error("Failed to trigger illustration generation for book ID: {}", storyBook.getId(), e);
        }
        
        return storyBook;
    }

    @MutationMapping
    public String downloadStoryBookPDF(@Argument Long id) {
        // Return full absolute URL for frontend download
        return "http://localhost:8080/api/download/" + id;
    }

    @MutationMapping
    public Boolean deleteStoryBook(@Argument Long id) {
        try {
            storyBookRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
