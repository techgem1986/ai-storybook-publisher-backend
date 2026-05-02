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
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    @Value("${leonardo.api.key}")
    private String leonardoApiKey;

    @Async
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
            // Clear existing pages if any (useful for retries)
            storyBook.getPages().clear();
            
            List<StoryPage> newPages = new ArrayList<>();
            
            // Generate images for each page
            for (int i = 0; i < totalPages; i++) {
                logger.info("Generating image for page {} of book ID: {}", i + 1, storyBook.getId());
                updateStatus(storyBook, "Creating illustration for page " + (i + 1) + " of " + totalPages + "...");
                String imageUrl = generateImageForPage(storyPages.get(i));
                StoryPage page = new StoryPage(i + 1, storyPages.get(i), imageUrl);
                newPages.add(page);
                
                // Add a 60-second delay between image URL generation steps
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // Add all pages at once after full generation to prevent duplicates
            storyBook.getPages().clear();
            storyBook.getPages().addAll(newPages);

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
    public void generateStoryDraft(Long bookId) {
        StoryBook storyBook = storyBookRepository.findByIdWithPages(bookId).orElseThrow();
        logger.info("Generating story draft for book: {} (ID: {})", storyBook.getTitle(), storyBook.getId());
        try {
            storyBook.setStatus("DRAFTING");
            updateStatus(storyBook, "Generating story text for your review...");

            List<String> storyPages = generateStoryPages(storyBook);
            storyBook.getPages().clear();
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
                
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
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

    @Retryable(
            value = {RestClientException.class, HttpServerErrorException.class},
            maxAttempts = 4,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
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

            // Pollinations AI public endpoint doesn't require authentication
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Pollinations.ai public Text API expects standard OpenAI messages format
            Map<String, Object> requestBody = Map.of(
                    "model", "openai",
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a creative children's book author."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "stream", false
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            logger.debug("AI Request body: {}", requestBody);
            // Use Pollinations public text endpoint that doesn't require authentication
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

        } catch (HttpServerErrorException e) {
            logger.warn("AI Generation failed for book title: {}. Retrying... Error: {}", title, e.getMessage());
            throw e; // Re-throw to trigger retry
        } catch (Exception e) {
            logger.error("AI Generation failed for book title: {}. Using fallback story. Error: {}", title, e.getMessage());
            
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

    private String pollForImage(String generationId) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long timeout = 180000; // 3 minutes

        while (System.currentTimeMillis() - startTime < timeout) {
            // Configure RestTemplate with extended timeouts for Leonardo API
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(30000); // 30 seconds connection timeout
            requestFactory.setReadTimeout(120000); // 2 minutes read timeout
            RestTemplate restTemplate = new RestTemplate(requestFactory);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(leonardoApiKey);
            headers.add("accept", "application/json");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                "https://cloud.leonardo.ai/api/rest/v1/generations/" + generationId,
                org.springframework.http.HttpMethod.GET,
                requestEntity,
                Map.class
            );

            if (response.getBody() != null) {
                Map<String, Object> generationData = (Map<String, Object>) response.getBody().get("generations_by_pk");
                if (generationData != null) {
                    String status = (String) generationData.get("status");
                    if ("COMPLETE".equals(status)) {
                        List<LinkedHashMap<String, String>> generatedImages = (List<LinkedHashMap<String, String>>) generationData.get("generated_images");
                        if (generatedImages != null && !generatedImages.isEmpty()) {
                            return generatedImages.get(0).get("url");
                        }
                    } else if ("FAILED".equals(status)) {
                        throw new RuntimeException("Image generation failed.");
                    }
                }
            }

            Thread.sleep(10000); // Wait 10 seconds before polling again
        }

        throw new RuntimeException("Image generation timed out.");
    }

    @Retryable(
            value = {RestClientException.class, HttpServerErrorException.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 3000)
    )
    private String generateImageForPage(String storyText) {
        String prompt = "cartoon style, colorful, children book illustration, " + storyText + ", friendly, happy, no text";
        String encodedPrompt = null;
        try {
            // Use Leonardo AI for professional quality children's book illustrations
            encodedPrompt = java.net.URLEncoder.encode(prompt, "UTF-8");
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(60000); // 30 seconds connection timeout
            requestFactory.setReadTimeout(120000); // 2 minutes read timeout
            RestTemplate restTemplate = new RestTemplate(requestFactory);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(leonardoApiKey);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("prompt", prompt);
            requestBody.put("modelId", "b2614463-296c-462a-9586-aafdb8f00e36");
            requestBody.put("num_images", 1);
            requestBody.put("width", 832);
            requestBody.put("height", 1248);
            requestBody.put("promptMagic", true);
            requestBody.put("presetStyle", "VIBRANT");
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://cloud.leonardo.ai/api/rest/v1/generations",
                request,
                Map.class
            );
            
            if (response.getBody() != null) {
                Map<String, Object> generationData = (Map<String, Object>) response.getBody().get("sdGenerationJob");
                if (generationData != null && generationData.get("generationId") != null) {
                    String generationId = (String) generationData.get("generationId");
                    logger.info("Polling for Image");
                    return pollForImage(generationId);
                }
            }
            return "https://picsum.photos/800/600?random=" + System.currentTimeMillis();

            
        } catch (Exception e) {
            logger.warn("Failed to generate image with Leonardo AI ({}), using fallback image", e.getMessage());
            // Fallback to Pollinations if Leonardo fails
            return String.format("https://image.pollinations.ai/prompt/%s?width=800&height=600&seed=%d", 
                encodedPrompt, 
                System.currentTimeMillis()
            );
        }
    }
}