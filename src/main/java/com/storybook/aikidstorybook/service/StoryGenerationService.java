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
    public void generateCompleteStoryBook(StoryBook storyBook) {
        logger.info("Starting generation for book: {} (ID: {})", storyBook.getTitle(), storyBook.getId());
        try {
            storyBook.setStatus("GENERATING");
            updateStatus(storyBook, "Started generating story content...");

            // Generate 5 page story for kids 5-8 years old
            logger.info("Generating story pages for book ID: {}", storyBook.getId());
            List<String> storyPages = generateStoryPages(storyBook.getTitle());
            logger.info("Successfully generated {} story pages for book ID: {}", storyPages.size(), storyBook.getId());
            updateStatus(storyBook, "Story text generated. Now creating magical illustrations...");

            // Generate images for each page
            for (int i = 0; i < storyPages.size(); i++) {
                logger.info("Generating image for page {} of book ID: {}", i + 1, storyBook.getId());
                updateStatus(storyBook, "Creating illustration for page " + (i + 1) + " of 5...");
                String imageUrl = generateImageForPage(storyPages.get(i));
                StoryPage page = new StoryPage(i + 1, storyPages.get(i), imageUrl);
                storyBook.addPage(page);
            }

            storyBook.setStatus("COMPLETED");
            updateStatus(storyBook, "Story content ready! Starting PDF creation...");
            logger.info("Story content generation completed for book ID: {}", storyBook.getId());

            // Trigger async PDF generation
            pdfGenerationService.generatePdf(storyBook);

        } catch (Exception e) {
            logger.error("Error generating book ID: {}", storyBook.getId(), e);
            storyBook.setStatus("FAILED");
            updateStatus(storyBook, "Error: Generation failed.");
        }
    }

    private void updateStatus(StoryBook storyBook, String status) {
        logger.debug("Updating status for book ID {}: {}", storyBook.getId(), status);
        storyBook.setLastStatus(status);
        storyBookRepository.save(storyBook);
        statusEmitterService.sendStatus(storyBook.getId(), status);
    }

    private List<String> generateStoryPages(String title) {
        List<String> pages = new ArrayList<>();

        try {
            logger.info("Calling Gemini API for book title: {}", title);
            RestTemplate restTemplate = new RestTemplate();

            String prompt = "Write a beautiful short story for 5-8 year old children titled '" + title + "'. " +
                    "Split the story into exactly 5 short pages. Each page should be 2-3 simple sentences. " +
                    "Make it happy, colorful with animals or magic. Use simple words. " +
                    "Return only the 5 pages separated by '---PAGE---' marker. No extra text.";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    )
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            logger.debug("Gemini API request body: {}", requestBody);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + geminiApiKey,
                    request,
                    Map.class
            );

            if (response.getBody() != null) {
                logger.debug("Gemini API response: {}", response.getBody());
                Map candidates = (Map) ((List) response.getBody().get("candidates")).get(0);
                Map content = (Map) candidates.get("content");
                Map part = (Map) ((List) content.get("parts")).get(0);
                String text = (String) part.get("text");

                String[] splitPages = text.split("---PAGE---");
                for (String page : splitPages) {
                    if (!page.trim().isEmpty()) {
                        pages.add(page.trim());
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("Gemini API failed for book title: {}. Using fallback story. Error: {}", title, e.getMessage());
            // Fallback demo story if API fails
            pages.add("Once upon a time there was a little rabbit who loved carrots. He lived in a cozy burrow under the big oak tree.");
            pages.add("One sunny morning, the rabbit found a magic carrot that glowed bright gold! It was the most beautiful thing he had ever seen.");
            pages.add("The magic carrot took him on an adventure to meet friendly forest animals. They laughed and played all day long.");
            pages.add("All the animals became best friends and shared delicious berries. They promised to meet every day for fun adventures.");
            pages.add("When the sun went down, the rabbit went home happy. He knew that good friends make every day magical. The End!");
        }

        // Ensure exactly 5 pages
        while (pages.size() < 5) {
            pages.add("A wonderful day in the forest with happy friends playing together.");
        }

        return pages.subList(0, 5);
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