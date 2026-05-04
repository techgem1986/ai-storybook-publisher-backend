package com.storybook.aikidstorybook.service;

import com.storybook.aikidstorybook.entity.StoryBook;
import com.storybook.aikidstorybook.entity.StoryPage;
import com.storybook.aikidstorybook.repository.StoryBookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.util.ArrayList;
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

    @Autowired
    private StoryValidationService storyValidationService;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${pollinations.api.key:}")
    private String pollinationsApiKey;

    @Value("${pollinations.text.url:https://text.pollinations.ai}")
    private String pollinationsTextUrl;

    @Value("${image.generator.url:http://python:5000}")
    private String imageGeneratorUrl;

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
                String imageUrl = generateImageForPage(storyBook, storyPages.get(i), i + 1, totalPages);
                StoryPage page = new StoryPage(i + 1, storyPages.get(i), imageUrl);
                newPages.add(page);
            }
            
            // Add all pages at once after full generation to prevent duplicates
            storyBook.getPages().clear();
            newPages.forEach(storyBook::addPage);
            normalizePageCollection(storyBook);
            storyBookRepository.save(storyBook);

            String coverImageUrl = generateCoverImageForBook(storyBook);
            if (coverImageUrl != null && !coverImageUrl.isBlank()) {
                storyBook.setCoverImageUrl(coverImageUrl);
                storyBookRepository.save(storyBook);
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
            normalizePageCollection(storyBook);
            storyBookRepository.save(storyBook);
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

            normalizePageCollection(storyBook);
            List<StoryPage> pages = storyBook.getPages();
            int totalPages = pages.size();
            for (int i = 0; i < totalPages; i++) {
                StoryPage page = pages.get(i);
                updateStatus(storyBook, "Creating illustration for page " + (i + 1) + " of " + totalPages + "...");
                String imageUrl = generateImageForPage(storyBook, page.getText(), i + 1, totalPages);
                page.setImageUrl(imageUrl);
            }

            if (storyBook.getCoverImageUrl() == null || storyBook.getCoverImageUrl().isBlank()) {
                String coverImageUrl = generateCoverImageForBook(storyBook);
                if (coverImageUrl != null && !coverImageUrl.isBlank()) {
                    storyBook.setCoverImageUrl(coverImageUrl);
                }
            }
            storyBookRepository.save(storyBook);
            storyBook.setStatus("COMPLETED");
            updateStatus(storyBook, "Illustrations ready! Starting PDF creation...");

            pdfGenerationService.generatePdf(storyBook.getId());

        } catch (Exception e) {
            logger.error("Error generating illustrations for book ID: {}", storyBook.getId(), e);
            storyBook.setStatus("FAILED");
            updateStatus(storyBook, "Error: Illustration generation failed.");
        }
    }

    public StoryBook saveReviewEdits(Long bookId,
                                     List<Map<String, Object>> pageData,
                                     String fontColor,
                                     Integer fontSize,
                                     String fontStyle,
                                     String textBackground,
                                     String illustrationStyle) {
        StoryBook storyBook = storyBookRepository.findByIdWithPages(bookId).orElseThrow();
        storyBook.setFontColor(fontColor);
        storyBook.setFontSize(fontSize);
        storyBook.setFontStyle(fontStyle);
        storyBook.setTextBackground(textBackground);
        storyBook.setIllustrationStyle(illustrationStyle);
        storyBook.setStatus("REVIEW_PENDING");

        normalizePageCollection(storyBook);
        for (Map<String, Object> pageEntry : pageData) {
            int pageNum = asInt(pageEntry.get("pageNumber"));
            String text = pageEntry.get("text") instanceof String ? (String) pageEntry.get("text") : "";
            storyBook.getPages().stream()
                    .filter(p -> p.getPageNumber() == pageNum)
                    .findFirst()
                    .ifPresent(p -> p.setText(text));
        }

        List<String> validatedPages = new ArrayList<>();
        for (StoryPage page : storyBook.getPages()) {
            String safeText = storyValidationService.filterUnsafeWords(page.getText());
            safeText = storyValidationService.correctCommonGrammar(safeText);
            page.setText(safeText);
            validatedPages.add(safeText);
        }

        List<String> warnings = storyValidationService.validateReadability(validatedPages, resolveAgeGroup(storyBook.getAgeGroup()));
        if (!warnings.isEmpty()) {
            storyBook.setLastStatus("Review validation warnings: " + String.join(" ", warnings));
        } else {
            storyBook.setLastStatus("Review edits saved and validated successfully.");
        }

        return storyBookRepository.save(storyBook);
    }

    public StoryPage regeneratePageImage(Long bookId, int pageNumber) {
        StoryBook storyBook = storyBookRepository.findByIdWithPages(bookId).orElseThrow();
        normalizePageCollection(storyBook);

        StoryPage page = storyBook.getPages().stream()
                .filter(p -> p.getPageNumber() == pageNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Page " + pageNumber + " not found."));

        storyBook.setStatus("GENERATING");
        updateStatus(storyBook, "Regenerating illustration for page " + pageNumber + "...");

        String imageUrl = generateImageForPage(storyBook, page.getText(), pageNumber, storyBook.getPages().size());
        page.setImageUrl(imageUrl);

        storyBookRepository.save(storyBook);
        storyBook.setStatus("REVIEW_PENDING");
        updateStatus(storyBook, "Regenerated illustration for page " + pageNumber + ".");

        return page;
    }

    private int asInt(Object value) {
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        if (value instanceof Long longValue) {
            return longValue.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException ignored) {
            }
        }
        throw new IllegalArgumentException("Cannot convert page number to int: " + value);
    }

    private void updateStatus(StoryBook storyBook, String status) {
        logger.debug("Updating status for book ID {}: {}", storyBook.getId(), status);
        storyBookRepository.updateStatus(storyBook.getId(), storyBook.getStatus(), status);
        statusEmitterService.sendStatus(storyBook.getId(), status);
    }

    private List<String> generateStoryPages(StoryBook storyBook) {
        if (storyBook == null) {
            return List.of();
        }

        prepareStoryMetadata(storyBook);

        String title = storyBook.getTitle();
        String description = storyBook.getDescription();
        String ageGroup = resolveAgeGroup(storyBook.getAgeGroup());
        String writingStyle = resolveWritingStyle(storyBook.getWritingStyle());
        String genre = resolveGenre(storyBook.getGenre());
        int numberOfPages = storyBook.getNumberOfPages() != null ? storyBook.getNumberOfPages() : 5;

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Write a ").append(genre).append(" story for ").append(ageGroup)
                .append(" children titled '").append(title).append("'. ");
        if (description != null && !description.trim().isEmpty()) {
            promptBuilder.append("The story should be about: ").append(description).append(". ");
        }
        if (storyBook.getOutline() != null && !storyBook.getOutline().trim().isEmpty()) {
            promptBuilder.append("Use this outline: ").append(storyBook.getOutline()).append(". ");
        }
        promptBuilder.append("The tone should be ").append(writingStyle).append(". ");
        promptBuilder.append("Split the story into exactly ").append(numberOfPages).append(" short pages. ");
        promptBuilder.append("Each page should be 2-3 simple sentences. Use simple, age-appropriate vocabulary. ");
        promptBuilder.append("Return only the ").append(numberOfPages).append(" pages separated by '---PAGE---' marker. No extra text, no titles, no page numbers.");

        List<String> pages = callAiTextPages(promptBuilder.toString(), numberOfPages);

        List<String> validatedPages = new ArrayList<>();
        for (String page : pages) {
            String safeText = storyValidationService.filterUnsafeWords(page);
            safeText = storyValidationService.correctCommonGrammar(safeText);
            validatedPages.add(safeText);
        }

        List<String> warnings = storyValidationService.validateReadability(validatedPages, ageGroup);
        if (!warnings.isEmpty()) {
            logger.warn("Story readability warnings for book {}: {}", title, warnings);
            storyBook.setLastStatus(String.join(" ", warnings));
            storyBookRepository.save(storyBook);
        }

        return validatedPages;
    }

    private void prepareStoryMetadata(StoryBook storyBook) {
        if (storyBook == null) {
            return;
        }

        if (storyBook.getOutline() != null && !storyBook.getOutline().isBlank() &&
                storyBook.getMainCharacters() != null && !storyBook.getMainCharacters().isBlank()) {
            return;
        }

        String ageGroup = resolveAgeGroup(storyBook.getAgeGroup());
        String writingStyle = resolveWritingStyle(storyBook.getWritingStyle());
        String genre = resolveGenre(storyBook.getGenre());
        String description = storyBook.getDescription();

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("You are a gentle children's book editor. Create a structured story plan for a ")
                .append(genre).append(" story titled '").append(storyBook.getTitle()).append("' for ")
                .append(ageGroup).append(". ");
        if (description != null && !description.trim().isEmpty()) {
            promptBuilder.append("The story should be about ").append(description).append(". ");
        }
        promptBuilder.append("Provide the output with the following sections exactly: Outline:, Main Characters:, Setting:, Theme:, Moral:. ");
        promptBuilder.append("Keep each section one or two sentences, easy to read and child friendly.");

        String metadataText = callAiForText(promptBuilder.toString());
        if (metadataText == null || metadataText.isBlank()) {
            metadataText = buildFallbackMetadata(storyBook, ageGroup, genre, writingStyle, description);
        }

        storyBook.setOutline(extractSection(metadataText, "Outline:", "Main Characters:", "Outline"));
        storyBook.setMainCharacters(extractSection(metadataText, "Main Characters:", "Setting:", "A playful group of friends"));
        storyBook.setSetting(extractSection(metadataText, "Setting:", "Theme:", "A bright, friendly world"));
        storyBook.setTheme(extractSection(metadataText, "Theme:", "Moral:", "Friendship and kindness"));
        storyBook.setMoral(extractSection(metadataText, "Moral:", null, "Be kind to others."));
        storyBook.setGenre(genre);
        storyBook.setWritingStyle(writingStyle);
        storyBookRepository.save(storyBook);
    }

    private String resolveAgeGroup(String ageGroup) {
        if (ageGroup == null) {
            return "5-7";
        }
        return switch (ageGroup) {
            case "3-5", "5-7", "7-9", "9-12" -> ageGroup;
            default -> "5-7";
        };
    }

    private String resolveGenre(String genre) {
        if (genre == null || genre.isBlank()) {
            return "fantasy";
        }
        return genre;
    }

    private String resolveWritingStyle(String writingStyle) {
        if (writingStyle == null || writingStyle.isBlank()) {
            return "gentle and whimsical";
        }
        return writingStyle;
    }

    private List<String> callAiTextPages(String prompt, int numberOfPages) {
        List<String> pages = new ArrayList<>();
        try {
            String text = callAiForText(prompt);
            if (text != null && !text.isBlank()) {
                String[] splitPages = text.split("---PAGE---");
                for (String page : splitPages) {
                    String trimmedPage = page.trim();
                    if (!trimmedPage.isEmpty()) {
                        trimmedPage = trimmedPage.replaceAll("^Page \\d+:?", "").trim();
                        pages.add(trimmedPage);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Page generation AI call failed for prompt {}: {}", prompt, e.getMessage());
        }

        if (pages.isEmpty()) {
            pages.add("Once upon a time, a gentle story began in a warm, colorful world.");
        }

        while (pages.size() < numberOfPages) {
            pages.add("The adventure continued with gentle, simple sentences that children can enjoy.");
        }
        return pages.subList(0, numberOfPages);
    }

    private String callAiForText(String prompt) {
        try {
            logger.info("Calling Pollinations AI for prompt");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (pollinationsApiKey != null && !pollinationsApiKey.isBlank()) {
                headers.setBearerAuth(pollinationsApiKey);
            }

            Map<String, Object> requestBody = Map.of(
                    "model", "openai",
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are a creative children's book author."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.8,
                    "max_tokens", 500,
                    "stream", false
            );
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    pollinationsTextUrl + "/openai",
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getBody() != null) {
                Object choices = response.getBody().get("choices");
                if (choices instanceof List<?> choicesList && !choicesList.isEmpty()) {
                    Object firstChoice = choicesList.get(0);
                    if (firstChoice instanceof Map<?, ?> firstChoiceMap) {
                        Object message = firstChoiceMap.get("message");
                        if (message instanceof Map<?, ?> messageMap) {
                            Object content = messageMap.get("content");
                            if (content instanceof String) {
                                return (String) content;
                            }
                        }
                        Object text = firstChoiceMap.get("text");
                        if (text instanceof String) {
                            return (String) text;
                        }
                    }
                }
            }
            logger.warn("Text AI service returned no valid content for prompt");
            return null;
        } catch (Exception e) {
            logger.error("Text AI service failed: {}", e.getMessage());
            return null;
        }
    }

    private String extractSection(String text, String startToken, String endToken, String fallback) {
        if (text == null) {
            return fallback;
        }

        int startIndex = text.indexOf(startToken);
        if (startIndex == -1) {
            return fallback;
        }
        startIndex += startToken.length();
        int endIndex = endToken != null ? text.indexOf(endToken, startIndex) : text.length();
        if (endIndex == -1) {
            endIndex = text.length();
        }

        String section = text.substring(startIndex, endIndex).trim();
        return section.isEmpty() ? fallback : section;
    }

    private String buildFallbackMetadata(StoryBook storyBook, String ageGroup, String genre, String writingStyle, String description) {
        return "Outline: " + storyBook.getTitle() + " is a short " + genre + " adventure for " + ageGroup + ". " +
                "Main Characters: A curious child and a friendly animal companion. " +
                "Setting: A bright and welcoming world. " +
                "Theme: Friendship, kindness, and discovery. " +
                "Moral: Be kind and brave. ";
    }

    private void normalizePageCollection(StoryBook storyBook) {
        if (storyBook == null || storyBook.getPages() == null) {
            return;
        }

        Map<Integer, StoryPage> uniquePages = new LinkedHashMap<>();
        for (StoryPage page : storyBook.getPages()) {
            if (!uniquePages.containsKey(page.getPageNumber())) {
                uniquePages.put(page.getPageNumber(), page);
            }
        }

        storyBook.getPages().clear();
        storyBook.getPages().addAll(uniquePages.values());
    }

    public String generateImageForPage(StoryBook storyBook, String storyText, int pageNumber, int totalPages) {
        String prompt = buildIllustrationPrompt(storyBook, storyText, pageNumber, totalPages);
        return generateImageFromPrompt(storyBook, prompt, pageNumber, totalPages);
    }

    private String generateCoverImageForBook(StoryBook storyBook) {
        String prompt = buildCoverPrompt(storyBook);
        logger.info("Generating cover illustration for book ID: {}", storyBook.getId());
        return generateImageFromPrompt(storyBook, prompt, 0, 0);
    }

    private String generateImageFromPrompt(StoryBook storyBook, String prompt, int pageNumber, int totalPages) {
        String encodedPrompt;
        try {
            encodedPrompt = URLEncoder.encode(prompt, "UTF-8");
        } catch (Exception e) {
            logger.warn("Unable to encode image prompt, continuing with raw prompt.", e);
            encodedPrompt = prompt.replaceAll("\\s+", "%20");
        }

        String endpoint = imageGeneratorUrl + "/generate-image";
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("prompt", prompt);
        requestBody.put("width", 1024);
        requestBody.put("height", 1024);
        requestBody.put("return_type", "base64");

        RetryTemplate retryTemplate = new RetryTemplate();
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, Map.of(
                RestClientException.class, true,
                HttpServerErrorException.class, true
        ));
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(2000);
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        Map<String, Object> responseBody = retryTemplate.execute(context -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            if (pageNumber > 0 && totalPages > 0) {
                logger.info("Image generation attempt {} for book {} page {}/{}",
                        context.getRetryCount() + 1, storyBook.getId(), pageNumber, totalPages);
            } else {
                logger.info("Image generation attempt {} for book {} cover image",
                        context.getRetryCount() + 1, storyBook.getId());
            }
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            if (response.getBody() == null) {
                throw new RestClientException("Image generator returned empty response body");
            }
            return response.getBody();
        }, context -> {
            logger.warn("Image generation failed after {} attempts", context.getRetryCount());
            return null;
        });

        if (responseBody != null) {
            Object success = responseBody.get("success");
            if (Boolean.TRUE.equals(success)) {
                Object imageValue = responseBody.get("image");
                if (imageValue instanceof String) {
                    return (String) imageValue;
                }
                Object imageUrl = responseBody.get("image_url");
                if (imageUrl instanceof String) {
                    return (String) imageUrl;
                }
            }
            logger.warn("Image generator returned unexpected response payload: {}", responseBody);
        }

        logger.warn("Falling back to public image placeholder for prompt.");
        return String.format("https://image.pollinations.ai/prompt/%s?width=1024&height=1024&seed=%d",
                encodedPrompt,
                System.currentTimeMillis()
        );
    }

    private String buildIllustrationPrompt(StoryBook storyBook, String pageText, int pageNumber, int totalPages) {
        String style = resolveIllustrationStyle(storyBook.getIllustrationStyle());
        String characters = storyBook.getMainCharacters();
        if (characters == null || characters.isBlank()) {
            characters = "A kind child and a playful animal friend";
        }
        String setting = storyBook.getSetting();
        if (setting == null || setting.isBlank()) {
            setting = "a bright, friendly world";
        }
        String theme = storyBook.getTheme();
        if (theme == null || theme.isBlank()) {
            theme = "friendship and wonder";
        }

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("A high quality children's book illustration in a ")
                .append(style)
                .append(" style. ")
                .append("Make the image feel warm, playful, and suitable for children. ")
                .append("Main characters: ").append(characters).append(". ")
                .append("Setting: ").append(setting).append(". ")
                .append("Theme: ").append(theme).append(". ")
                .append("Illustrate the scene described by the text: ").append(pageText).append(". ")
                .append("Use bright colors, soft shapes, and a clear composition. ")
                .append("Do not include any text, letters, words, logos, signage, or typography anywhere in the image. ")
                .append("Keep the style consistent across all pages and avoid harsh shadows.");

        return promptBuilder.toString();
    }

    private String buildCoverPrompt(StoryBook storyBook) {
        String style = resolveIllustrationStyle(storyBook.getIllustrationStyle());
        String characters = storyBook.getMainCharacters();
        if (characters == null || characters.isBlank()) {
            characters = "A kind child and a playful animal friend";
        }
        String setting = storyBook.getSetting();
        if (setting == null || setting.isBlank()) {
            setting = "a bright, friendly world";
        }
        String theme = storyBook.getTheme();
        if (theme == null || theme.isBlank()) {
            theme = "friendship and wonder";
        }

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("A high quality children's book cover illustration in a ")
                .append(style)
                .append(" style. ")
                .append("Create a joyful, magical cover scene featuring ")
                .append(characters).append(" in a beautiful ")
                .append(setting).append(" setting. ")
                .append("The illustration should feel inviting and whimsical, with bright colors and soft shapes. ")
                .append("Do not include any text, title lettering, letters, words, logos, or signage anywhere in the image. ")
                .append("The cover title will be added separately on the page, so keep the illustration free of typography.");

        return promptBuilder.toString();
    }

    private String resolveIllustrationStyle(String style) {
        if (style == null || style.isBlank()) {
            return "storybook watercolor";
        }
        return switch (style.toLowerCase()) {
            case "cartoon" -> "cartoon";
            case "digital flat" -> "digital flat";
            case "paper-cut" -> "paper-cut";
            case "storybook watercolor" -> "storybook watercolor";
            default -> style;
        };
    }
}