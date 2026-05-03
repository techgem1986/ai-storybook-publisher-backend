package com.storybook.aikidstorybook.controller;

import com.storybook.aikidstorybook.entity.StoryBook;
import com.storybook.aikidstorybook.entity.StoryPage;
import com.storybook.aikidstorybook.repository.StoryBookRepository;
import com.storybook.aikidstorybook.service.StoryGenerationService;
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

    @QueryMapping
    public StoryBook getStoryBook(@Argument Long id) {
        StoryBook book = storyBookRepository.findById(id).orElse(null);
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
            @Argument Integer numberOfPages) {
        logger.info("Received request to generate story book with title: {}", title);
        StoryBook storyBook = new StoryBook(title);
        storyBook.setDescription(description);
        storyBook.setAgeGroup(ageGroup);
        storyBook.setWritingStyle(writingStyle);
        storyBook.setGenre(genre);
        storyBook.setNumberOfPages(numberOfPages != null ? numberOfPages : 5);
        
        storyBook = storyBookRepository.save(storyBook);
        logger.info("Saved initial story book record with ID: {}", storyBook.getId());

        try {
            storyGenerationService.generateCompleteStoryBook(storyBook.getId());
            logger.info("Triggered async generation for book ID: {}", storyBook.getId());
        } catch (Exception e) {
            logger.error("Failed to trigger async generation for book ID: {}", storyBook.getId(), e);
        }

        return storyBook;
    }

    @MutationMapping
    public StoryBook generateStoryDraft(
            @Argument String title,
            @Argument String description,
            @Argument String ageGroup,
            @Argument String writingStyle,
            @Argument String genre,
            @Argument Integer numberOfPages) {
        logger.info("Received request to generate story draft with title: {}", title);
        StoryBook storyBook = new StoryBook(title);
        storyBook.setDescription(description);
        storyBook.setAgeGroup(ageGroup);
        storyBook.setWritingStyle(writingStyle);
        storyBook.setGenre(genre);
        storyBook.setNumberOfPages(numberOfPages != null ? numberOfPages : 5);
        
        storyBook = storyBookRepository.save(storyBook);
        
        try {
            storyGenerationService.generateStoryDraft(storyBook.getId());
        } catch (Exception e) {
            logger.error("Failed to trigger story drafting for book ID: {}", storyBook.getId(), e);
        }
        
        return storyBook;
    }

    @MutationMapping
    public StoryBook updateStoryContent(
            @Argument Long id,
            @Argument List<Map<String, Object>> pages,
            @Argument String fontColor,
            @Argument Integer fontSize,
            @Argument String fontStyle,
            @Argument String textBackground) {
        StoryBook storyBook = storyBookRepository.findById(id).orElseThrow();
        
        // Update font settings
        storyBook.setFontColor(fontColor);
        storyBook.setFontSize(fontSize);
        storyBook.setFontStyle(fontStyle);
        storyBook.setTextBackground(textBackground);
        
        // Update pages
        for (Map<String, Object> pageData : pages) {
            Object pageNumObj = pageData.get("pageNumber");
            int pageNum = (pageNumObj instanceof Integer) ? (Integer) pageNumObj : ((Long) pageNumObj).intValue();
            String text = (String) pageData.get("text");
            
            storyBook.getPages().stream()
                .filter(p -> p.getPageNumber() == pageNum)
                .findFirst()
                .ifPresent(p -> p.setText(text));
        }
        
        return storyBookRepository.save(storyBook);
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
