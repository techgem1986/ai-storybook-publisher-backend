package com.storybook.aikidstorybook.service;

import com.storybook.aikidstorybook.entity.StoryBook;
import com.storybook.aikidstorybook.entity.StoryPage;
import com.storybook.aikidstorybook.repository.StoryBookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class StoryGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(StoryGenerationService.class);

    @Autowired
    private StoryBookRepository storyBookRepository;

    @Autowired
    private PdfGenerationService pdfGenerationService;

    @Autowired
    private StatusEmitterService statusEmitterService;

    @Value("${gemini.api.key:demo}")
    private String geminiApiKey;

    @Async
    @Transactional
    public void generateCompleteStoryBook(Long bookId) {
        StoryBook storyBook = storyBookRepository.findByIdWithPages(bookId).orElseThrow();
        logger.info("Starting generation for book: {} (ID: {})", storyBook.getTitle(), storyBook.getId());
        try {
            storyBook.setStatus("GENERATING");
            updateStatus(storyBook, "Started generating story content...");

            // Generate story pages based on inputs
            logger.info("Generating story pages for book ID: {}", storyBook.getId());
            List<String> storyPages = generateStoryPages(storyBook);
            logger.info("Successfully generated {} story pages for book ID: {}", storyPages.size(), storyBook.getId());
            updateStatus(storyBook, "Story text generated. Now creating magical illustrations...");

            int totalPages = storyPages.size();
            // Generate images for each page
            for (int i = 0; i < totalPages; i++) {
                logger.info("Generating image for page {} of book ID: {}", i + 1, storyBook.getId());
                updateStatus(storyBook, "Creating illustration for page " + (i + 1) + " of " + totalPages + "...");
                String imageUrl = generateImageForPage(storyPages.get(i));
                StoryPage page = new StoryPage(i + 1, storyPages.get(i), imageUrl);
                storyBook.addPage(page);
            }

            storyBook.setStatus("COMPLETED");
            updateStatus(storyBook, "Story content ready! Starting PDF creation...");
            logger.info("Story content generation completed for book ID: {}", storyBook.getId());

            // Trigger async PDF generation
            pdfGenerationService.generatePdf(storyBook.getId());

        } catch (Exception e) {
            logger.error("Error generating book ID: {}", storyBook.getId(), e);
            storyBook.setStatus("FAILED");
            updateStatus(storyBook, "Error: Generation failed.");
        }
    }

    @Async
    @Transactional
    public void generateStoryDraft(Long bookId) {
        StoryBook storyBook = storyBookRepository.findByIdWithPages(bookId).orElseThrow();
        logger.info("Generating story draft for book: {} (ID: {})", storyBook.getTitle(), storyBook.getId());
        try {
            storyBook.setStatus("DRAFTING");
            updateStatus(storyBook, "Generating story text for your review...");

            List<String> storyPages = generateStoryPages(storyBook);
            for (int i = 0; i < storyPages.size(); i++) {
                StoryPage page = new StoryPage(i + 1, storyPages.get(i), null);
                storyBook.addPage(page);
            }

            storyBook.setStatus("REVIEW_PENDING");
            updateStatus(storyBook, "Story draft ready for your review!");
            statusEmitterService.complete(storyBook.getId());

        } catch (Exception e) {
            logger.error("Error drafting book ID: {}", storyBook.getId(), e);
            storyBook.setStatus("FAILED");
            updateStatus(storyBook, "Error: Drafting failed.");
            statusEmitterService.complete(storyBook.getId());
        }
    }

    @Async
    @Transactional
    public void generateIllustrationsAndPdf(Long bookId) {
        StoryBook storyBook = storyBookRepository.findByIdWithPages(bookId).orElseThrow();
        logger.info("Starting illustration generation for book ID: {}", storyBook.getId());
        try {
            storyBook.setStatus("GENERATING");
            updateStatus(storyBook, "Starting magical illustrations...");

            List<StoryPage> pages = storyBook.getPages();
            int totalPages = pages.size();
            for (int i = 0; i < totalPages; i++) {
                StoryPage page = pages.get(i);
                updateStatus(storyBook, "Creating illustration for page " + (i + 1) + " of " + totalPages + "...");
                String imageUrl = generateImageForPage(page.getText());
                page.setImageUrl(imageUrl);
            }

            storyBook.setStatus("COMPLETED");
            updateStatus(storyBook, "Illustrations ready! Starting PDF creation...");

            pdfGenerationService.generatePdf(storyBook.getId());

        } catch (Exception e) {
            logger.error("Error generating illustrations for book ID: {}", storyBook.getId(), e);
            storyBook.setStatus("FAILED");
            updateStatus(storyBook, "Error: Illustration generation failed.");
        }
    }

    private void updateStatus(StoryBook storyBook, String status) {
        logger.debug("Updating status for book ID {}: {}", storyBook.getId(), status);
        storyBook.setLastStatus(status);
        storyBookRepository.save(storyBook);
        statusEmitterService.sendStatus(storyBook.getId(), status);
    }

    private List<String> generateStoryPages(StoryBook storyBook) {
        List<String> pages = new ArrayList<>();
        String title = storyBook.getTitle();
        String description = storyBook.getDescription();
        String ageGroup = storyBook.getAgeGroup() != null ? storyBook.getAgeGroup() : "5-8 year old";
        String writingStyle = storyBook.getWritingStyle() != null ? storyBook.getWritingStyle() : "happy and magical";
        int numberOfPages = storyBook.getNumberOfPages() != null ? storyBook.getNumberOfPages() : 5;

        try {
            logger.info("Calling Pollinations AI for book title: {}", title);
            RestTemplate restTemplate = new RestTemplate();

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("Write a beautiful story for ").append(ageGroup).append(" children titled '").append(title).append("'. ");
            
            if (description != null && !description.trim().isEmpty()) {
                promptBuilder.append("The story should be about: ").append(description).append(". ");
            }
            
            promptBuilder.append("The writing style should be ").append(writingStyle).append(". ");
            promptBuilder.append("Split the story into exactly ").append(numberOfPages).append(" short pages. ");
            promptBuilder.append("Each page should be 2-3 simple sentences. Use simple words suitable for the age group. ");
            promptBuilder.append("CRITICAL: Return only the ").append(numberOfPages).append(" pages separated by '---PAGE---' marker. No extra text, no titles, no page numbers.");

            String prompt = promptBuilder.toString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Using Pollinations.ai Text API (Free, no key required)
            Map<String, Object> requestBody = Map.of(
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a creative children's book author."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "model", "openai" // Options: openai, mistral, llama
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            logger.debug("AI Request body: {}", requestBody);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://text.pollinations.ai/",
                    request,
                    String.class
            );

            if (response.getBody() != null) {
                String text = response.getBody();
                logger.debug("AI Response: {}", text);

                String[] splitPages = text.split("---PAGE---");
                for (String page : splitPages) {
                    String trimmedPage = page.trim();
                    if (!trimmedPage.isEmpty()) {
                        // Remove potential Markdown or extra markers
                        trimmedPage = trimmedPage.replaceAll("^Page \\d+:?", "").trim();
                        pages.add(trimmedPage);
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("AI Generation failed for book title: {}. Using fallback story. Error: {}", title, e.getMessage());
            
            // Even if API fails, we make the fallback use user inputs
            String storyTheme = (description != null && !description.trim().isEmpty()) ? description : title;
            
            pages.add("Once upon a time, there was a story about " + title + ". It was a " + writingStyle + " adventure for a " + ageGroup + ".");
            pages.add("The story was all about " + storyTheme + ". Every page was filled with " + writingStyle + " surprises.");
            pages.add("The characters in " + title + " were very brave and kind. They loved to explore and learn new things together.");
            pages.add("They found that " + storyTheme + " was the most important part of their journey. It brought them so much joy.");
            pages.add("And so, the " + writingStyle + " tale of " + title + " ended happily. Everyone felt " + writingStyle + " and content. The End!");
        }

        // Ensure exactly the requested number of pages
        while (pages.size() < numberOfPages) {
            pages.add("And so the " + writingStyle + " adventure of " + title + " continued with more happy moments.");
        }

        return pages.subList(0, numberOfPages);
    }

    private String generateImageForPage(String storyText) {
        try {
            // Using Pollination.ai free Stable Diffusion API
            return "https://image.pollinations.ai/prompt/" +
                    java.net.URLEncoder.encode(
                            "cartoon style, colorful, children book illustration, " + storyText + ", friendly, happy, no text",
                            "UTF-8"
                    ) + "?width=800&height=600&seed=" + System.currentTimeMillis();
        } catch (Exception e) {
            return "https://picsum.photos/800/600?random=" + System.currentTimeMillis();
        }
    }
}