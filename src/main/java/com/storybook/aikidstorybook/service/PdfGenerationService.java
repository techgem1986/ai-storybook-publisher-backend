package com.storybook.aikidstorybook.service;

import com.storybook.aikidstorybook.entity.StoryBook;
import com.storybook.aikidstorybook.entity.StoryPage;
import com.storybook.aikidstorybook.repository.StoryBookRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(PdfGenerationService.class);

    @Autowired
    private StoryBookRepository storyBookRepository;

    @Autowired
    private StatusEmitterService statusEmitterService;

    @Value("${pollinations.api.key:demo}")
    private String pollinationsApiKey;

    @Async
    public void generatePdf(Long bookId) {
        StoryBook storyBook = storyBookRepository.findByIdWithPages(bookId).orElseThrow();
        logger.info("Starting PDF generation for book ID: {}", storyBook.getId());
        try {
            storyBook.setPdfStatus("IN_PROGRESS");
            updateStatus(storyBook, "Wrapping up your story into a PDF...");

            String pdfPath = generateAndSavePdf(storyBook);
            logger.info("PDF generated successfully for book ID: {} at path: {}", storyBook.getId(), pdfPath);
            storyBook.setPdfPath(pdfPath);
            storyBook.setPdfStatus("COMPLETED");
            updateStatus(storyBook, "Success! Your book is ready for download.");
            statusEmitterService.complete(storyBook.getId());

        } catch (Exception e) {
            logger.error("Error generating PDF for book ID: {}", storyBook.getId(), e);
            storyBook.setPdfStatus("FAILED");
            updateStatus(storyBook, "Error: PDF generation failed.");
            statusEmitterService.complete(storyBook.getId());
        }
    }

    private void updateStatus(StoryBook storyBook, String status) {
        logger.debug("Updating status for book ID {}: {}", storyBook.getId(), status);
        storyBook.setLastStatus(status);
        storyBookRepository.save(storyBook);
        statusEmitterService.sendStatus(storyBook.getId(), status);
    }

    private String generateAndSavePdf(StoryBook storyBook) throws IOException {
        try (PDDocument document = new PDDocument()) {
            // Title Page
            addTitlePage(document, storyBook.getTitle());

            // Story Pages
            List<StoryPage> pages = storyBook.getPages();
            for (int i = 0; i < pages.size(); i++) {
                updateStatus(storyBook, "Adding page " + (i + 1) + " of " + pages.size() + " to PDF...");
                addStoryPage(document, pages.get(i), storyBook);
                
                // Add a 15-second delay between pages to avoid rate limits
                try {
                    Thread.sleep(15000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Save PDF to a temporary file
            Path tempDir = Files.createDirectories(Paths.get(System.getProperty("java.io.tmpdir"), "storybooks"));
            String fileName = "storybook-" + storyBook.getId() + ".pdf";
            File file = new File(tempDir.toFile(), fileName);

            document.save(file);
            return file.getAbsolutePath();
        }
    }

    private void addTitlePage(PDDocument document, String title) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 36);
            contentStream.newLineAtOffset(100, 500);
            contentStream.showText(title);
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA, 18);
            contentStream.newLineAtOffset(150, 450);
            contentStream.showText("AI Generated Kids Story Book");
            contentStream.endText();
        }
    }

    private void addStoryPage(PDDocument document, StoryPage storyPageData, StoryBook storyBook) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            // Draw Image as background
            String imageData = storyPageData.getImageUrl();
            if (imageData != null && !imageData.isEmpty()) {
                boolean success = false;
                int maxRetries = 3;
                int retryCount = 0;
                long waitTime = 2000;

                while (!success && retryCount < maxRetries) {
                    try {
                        logger.info("Loading image for page (Attempt {}/{})", retryCount + 1, maxRetries);
                        
                        BufferedImage bufferedImage = null;
                        
                        // Check if it's a base64 encoded image
                        if (imageData.startsWith("data:image")) {
                            logger.info("Processing base64 encoded image");
                            // Extract base64 part after comma
                            String base64Part = imageData.split(",")[1];
                            byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Part);
                            try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imageBytes)) {
                                bufferedImage = ImageIO.read(bais);
                            }
                        } else {
                            // Fallback to URL loading for external images
                            logger.info("Loading image from URL: {}", imageData);
                            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) URI.create(imageData).toURL().openConnection();
                            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                            connection.setConnectTimeout(15000);
                            connection.setReadTimeout(15000);
                            
                            int responseCode = connection.getResponseCode();
                            if (responseCode == 429) {
                                throw new RuntimeException("Rate limited (429)");
                            }

                            try (java.io.InputStream inputStream = connection.getInputStream()) {
                                bufferedImage = ImageIO.read(inputStream);
                            }
                        }

                        if (bufferedImage != null) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(bufferedImage, "png", baos);
                            PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, baos.toByteArray(), "page-image");
                            contentStream.drawImage(pdImage, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
                            logger.info("Successfully added image to PDF page.");
                            success = true;
                        } else {
                            logger.warn("ImageIO.read returned null for image data");
                            break; // Don't retry if image data is invalid
                        }
                    } catch (Exception e) {
                        retryCount++;
                        logger.warn("Attempt {} failed to load image. Error: {}", retryCount, e.getMessage());
                        if (retryCount < maxRetries) {
                            try {
                                Thread.sleep(waitTime);
                                waitTime *= 2; // Exponential backoff
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
            } else {
                logger.warn("No image URL found for page.");
            }

            // Add Text Overlay with Wrapping
            drawWrappedText(contentStream, storyPageData.getText(), storyBook);
        }
    }

    private void drawWrappedText(PDPageContentStream contentStream, String text, StoryBook storyBook) throws IOException {
        float fontSize = storyBook.getFontSize() != null ? storyBook.getFontSize().floatValue() : 18;
        String fontStyle = storyBook.getFontStyle() != null ? storyBook.getFontStyle() : "HELVETICA";
        String fontColorStr = storyBook.getFontColor() != null ? storyBook.getFontColor() : "#000000";
        String textBg = storyBook.getTextBackground() != null ? storyBook.getTextBackground() : "TRANSPARENT";

        // Sanitize text to remove newlines
        String sanitizedText = text.replaceAll("\\r\\n|\\r|\\n", " ");

        PDType1Font font = PDType1Font.HELVETICA;
        if ("HELVETICA_BOLD".equals(fontStyle)) font = PDType1Font.HELVETICA_BOLD;
        else if ("TIMES_ROMAN".equals(fontStyle)) font = PDType1Font.TIMES_ROMAN;
        else if ("COURIER".equals(fontStyle)) font = PDType1Font.COURIER;

        Color fontColor = Color.BLACK;
        try {
            fontColor = Color.decode(fontColorStr);
        } catch (Exception e) {
            // fallback
        }

        float leading = fontSize * 1.2f;
        float margin = 50;
        float width = PDRectangle.A4.getWidth() - 2 * margin;
        float startX = margin;
        float startY = 150; // Position text at the bottom of the page

        List<String> lines = new ArrayList<>();
        String[] words = sanitizedText.split(" ");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            float size = fontSize * font.getStringWidth(line + word) / 1000;
            if (size > width) {
                lines.add(line.toString());
                line = new StringBuilder(word + " ");
            } else {
                line.append(word).append(" ");
            }
        }
        lines.add(line.toString());

        // Draw Background if WHITE
        if ("WHITE".equalsIgnoreCase(textBg)) {
            float boxHeight = lines.size() * leading + 20;
            contentStream.setNonStrokingColor(Color.WHITE);
            contentStream.addRect(startX - 10, startY - 10, width + 20, boxHeight);
            contentStream.fill();
        }

        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.setNonStrokingColor(fontColor);
        contentStream.setLeading(leading);
        contentStream.newLineAtOffset(startX, startY);

        for (String l : lines) {
            contentStream.showText(l.trim());
            contentStream.newLine();
        }

        contentStream.endText();
    }
}
