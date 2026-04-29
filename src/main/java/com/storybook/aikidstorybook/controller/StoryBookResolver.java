package com.storybook.aikidstorybook.controller;

import com.storybook.aikidstorybook.entity.StoryBook;
import com.storybook.aikidstorybook.repository.StoryBookRepository;
import com.storybook.aikidstorybook.service.StoryGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class StoryBookResolver {

    private static final Logger logger = LoggerFactory.getLogger(StoryBookResolver.class);

    @Autowired
    private StoryBookRepository storyBookRepository;

    @Autowired
    private StoryGenerationService storyGenerationService;

    @QueryMapping
    public StoryBook getStoryBook(@Argument Long id) {
        return storyBookRepository.findById(id).orElse(null);
    }

    @QueryMapping
    public List<StoryBook> getAllStoryBooks() {
        return storyBookRepository.findAll();
    }

    @MutationMapping
    public StoryBook generateStoryBook(@Argument String title) {
        logger.info("Received request to generate story book with title: {}", title);
        // Create new story book record
        StoryBook storyBook = new StoryBook(title);
        storyBook = storyBookRepository.save(storyBook);
        logger.info("Saved initial story book record with ID: {}", storyBook.getId());

        // Start async generation process
        try {
            storyGenerationService.generateCompleteStoryBook(storyBook);
            logger.info("Triggered async generation for book ID: {}", storyBook.getId());
        } catch (Exception e) {
            logger.error("Failed to trigger async generation for book ID: {}", storyBook.getId(), e);
        }

        // Return immediately with pending status
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
