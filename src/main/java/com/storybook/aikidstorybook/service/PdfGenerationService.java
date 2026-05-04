package com.storybook.aikidstorybook.service;

import com.storybook.aikidstorybook.entity.StoryBook;
import com.storybook.aikidstorybook.entity.StoryPage;
import com.storybook.aikidstorybook.repository.StoryBookRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PdfGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(PdfGenerationService.class);

    @Autowired
    private StoryBookRepository storyBookRepository;

    @Autowired
    private StatusEmitterService statusEmitterService;

    @Value("${pollinations.api.key:demo}")
    private String pollinationsApiKey;

    @Value("${storybook.output-dir:./storage/storybooks}")
    private String outputDirectory;

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
        List<StoryPage> pages = normalizePages(storyBook.getPages());
        int totalPdfPages = pages.size() + 1;

        try (PDDocument document = new PDDocument()) {
            setDocumentMetadata(document, storyBook);
            addCoverPage(document, storyBook, pages);

            for (int i = 0; i < pages.size(); i++) {
                updateStatus(storyBook, "Adding page " + (i + 1) + " of " + pages.size() + " to PDF...");
                addStoryPage(document, pages.get(i), storyBook, i + 1, pages.size(), totalPdfPages);
            }

            Path outputDir = Paths.get(outputDirectory);
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            String safeTitle = storyBook.getTitle() != null ? storyBook.getTitle().replaceAll("[^a-zA-Z0-9_-]", "_") : "storybook";
            String fileName = safeTitle + "-" + storyBook.getId() + "-print-ready.pdf";
            File file = new File(outputDir.toFile(), fileName);

            document.save(file);
            return file.getAbsolutePath();
        }
    }

    private void setDocumentMetadata(PDDocument document, StoryBook storyBook) {
        PDDocumentInformation info = document.getDocumentInformation();
        info.setTitle(storyBook.getTitle() != null ? storyBook.getTitle() : "AI Kid Storybook");
        info.setAuthor("AI Kid Storybook Publisher");
        info.setSubject(storyBook.getTheme() != null ? storyBook.getTheme() : storyBook.getDescription());
        info.setKeywords(createKeywords(storyBook));
        info.setProducer("AI Kid Storybook Publisher");
        info.setCreator("AI Kid Storybook Publisher Generator");
        Calendar now = Calendar.getInstance();
        info.setCreationDate(now);
        info.setModificationDate(now);
    }

    private String createKeywords(StoryBook storyBook) {
        List<String> keywords = new ArrayList<>();
        keywords.add("storybook");
        if (storyBook.getGenre() != null) keywords.add(storyBook.getGenre());
        if (storyBook.getAgeGroup() != null) keywords.add(storyBook.getAgeGroup());
        if (storyBook.getTheme() != null) keywords.add(storyBook.getTheme());
        if (storyBook.getIllustrationStyle() != null) keywords.add(storyBook.getIllustrationStyle());
        return String.join(", ", keywords);
    }

    private List<StoryPage> normalizePages(List<StoryPage> pages) {
        if (pages == null) {
            return List.of();
        }

        Map<Integer, StoryPage> uniquePages = new LinkedHashMap<>();
        for (StoryPage page : pages) {
            if (!uniquePages.containsKey(page.getPageNumber())) {
                uniquePages.put(page.getPageNumber(), page);
            }
        }

        return new ArrayList<>(uniquePages.values());
    }

    private void addCoverPage(PDDocument document, StoryBook storyBook, List<StoryPage> pages) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            String coverImageUrl = storyBook.getCoverImageUrl();
            if ((coverImageUrl == null || coverImageUrl.isBlank()) && !pages.isEmpty()) {
                coverImageUrl = pages.get(0).getImageUrl();
            }

            if (coverImageUrl != null && !coverImageUrl.isBlank()) {
                drawCoverImage(document, contentStream, coverImageUrl);
            } else {
                contentStream.setNonStrokingColor(Color.WHITE);
                contentStream.addRect(0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
                contentStream.fill();
            }

            drawCoverOverlay(contentStream, storyBook);
        }
    }

    private void drawCoverImage(PDDocument document, PDPageContentStream contentStream, String imageData) throws IOException {
        try {
            PDImageXObject pdImage = loadImage(document, imageData);
            if (pdImage != null) {
                float pageWidth = PDRectangle.A4.getWidth();
                float pageHeight = PDRectangle.A4.getHeight();
                float imageWidth = pdImage.getWidth();
                float imageHeight = pdImage.getHeight();
                float scale = Math.max(pageWidth / imageWidth, pageHeight / imageHeight);
                float width = imageWidth * scale;
                float height = imageHeight * scale;
                float x = (pageWidth - width) / 2;
                float y = (pageHeight - height) / 2;
                contentStream.drawImage(pdImage, x, y, width, height);
            }
        } catch (Exception e) {
            logger.warn("Unable to render cover image: {}", e.getMessage());
        }
    }

    private void drawCoverOverlay(PDPageContentStream contentStream, StoryBook storyBook) throws IOException {
        String title = sanitizeText(storyBook.getTitle(), "My Story Book");
        float titleFontSize = 52;
        float pageWidth = PDRectangle.A4.getWidth();
        float pageHeight = PDRectangle.A4.getHeight();
        float padding = 24;
        float maxLineWidth = pageWidth - 140;

        List<String> lines = wrapText(title, PDType1Font.HELVETICA_BOLD, titleFontSize, maxLineWidth);
        float lineHeight = titleFontSize * 1.25f;
        float boxWidth = 0;
        for (String line : lines) {
            boxWidth = Math.max(boxWidth, PDType1Font.HELVETICA_BOLD.getStringWidth(line) / 1000 * titleFontSize);
        }
        boxWidth += padding * 2;
        float boxHeight = lines.size() * lineHeight + padding * 2;
        float rectX = (pageWidth - boxWidth) / 2;
        float rectY = pageHeight * 0.58f - boxHeight / 2;

        // soft shadow behind the title panel
        addRoundedRectangle(contentStream, rectX + 8, rectY - 8, boxWidth, boxHeight, 24);
        contentStream.setNonStrokingColor(new Color(0, 0, 0, 55));
        contentStream.fill();

        addRoundedRectangle(contentStream, rectX, rectY, boxWidth, boxHeight, 24);
        contentStream.setNonStrokingColor(new Color(255, 255, 255, 240));
        contentStream.fill();

        contentStream.setStrokingColor(new Color(195, 205, 220, 220));
        contentStream.setLineWidth(2);
        addRoundedRectangle(contentStream, rectX, rectY, boxWidth, boxHeight, 24);
        contentStream.stroke();

        float textY = rectY + boxHeight - padding - titleFontSize;
        for (String line : lines) {
            float lineWidth = PDType1Font.HELVETICA_BOLD.getStringWidth(line) / 1000 * titleFontSize;
            float textX = rectX + (boxWidth - lineWidth) / 2;
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, titleFontSize);
            contentStream.setNonStrokingColor(new Color(24, 32, 56));
            contentStream.newLineAtOffset(textX, textY);
            contentStream.showText(line);
            contentStream.endText();
            textY -= lineHeight;
        }

        String subtitle = "A magical children's story";
        float subtitleFontSize = 18;
        float subtitleWidth = PDType1Font.HELVETICA_OBLIQUE.getStringWidth(subtitle) / 1000 * subtitleFontSize;
        float subtitleX = (pageWidth - subtitleWidth) / 2;
        float subtitleY = rectY + padding - subtitleFontSize;
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, subtitleFontSize);
        contentStream.setNonStrokingColor(new Color(90, 100, 125));
        contentStream.newLineAtOffset(subtitleX, subtitleY);
        contentStream.showText(subtitle);
        contentStream.endText();
    }

    private void addTitlePage(PDDocument document, StoryBook storyBook, int totalPages) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.setNonStrokingColor(Color.WHITE);
            contentStream.addRect(0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
            contentStream.fill();

            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 32);
            contentStream.setNonStrokingColor(new Color(20, 30, 60));
            contentStream.newLineAtOffset(70, 530);
            contentStream.showText("Title");
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA, 24);
            contentStream.newLineAtOffset(70, 490);
            contentStream.showText(sanitizeText(storyBook.getTitle(), "Untitled Story"));
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 18);
            contentStream.newLineAtOffset(70, 430);
            contentStream.showText("Author:");
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA, 16);
            contentStream.newLineAtOffset(70, 400);
            contentStream.showText("AI Kid Storybook Publisher");
            contentStream.endText();

            if (storyBook.getSetting() != null) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 18);
                contentStream.newLineAtOffset(70, 360);
                contentStream.showText("Setting:");
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 14);
                contentStream.newLineAtOffset(70, 335);
                contentStream.showText(sanitizeText(storyBook.getSetting(), ""));
                contentStream.endText();
            }

            addPageHeaderFooter(contentStream, 2, totalPages);
        }
    }

    private void addCopyrightPage(PDDocument document, StoryBook storyBook, int totalPages) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.setNonStrokingColor(Color.WHITE);
            contentStream.addRect(0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
            contentStream.fill();

            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 22);
            contentStream.setNonStrokingColor(new Color(45, 55, 75));
            contentStream.newLineAtOffset(70, 520);
            contentStream.showText("Copyright");
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA, 14);
            contentStream.newLineAtOffset(70, 480);
            contentStream.showText("© " + Calendar.getInstance().get(Calendar.YEAR) + " AI Kid Storybook Publisher. All rights reserved.");
            contentStream.endText();

            String dedication = storyBook.getDescription() != null && !storyBook.getDescription().isBlank()
                    ? storyBook.getDescription()
                    : "For every curious reader and their grown-up companion.";

            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_BOLD, 18);
            contentStream.newLineAtOffset(70, 420);
            contentStream.showText("Dedication");
            contentStream.endText();

            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA, 14);
            contentStream.newLineAtOffset(70, 390);
            contentStream.showText(sanitizeText(dedication, "For every curious reader and their grown-up companion."));
            contentStream.endText();

            addPageHeaderFooter(contentStream, 3, totalPages);
        }
    }

    private void addStoryPage(PDDocument document, StoryPage storyPageData, StoryBook storyBook, int pageIndex, int storyPageCount, int totalPdfPages) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            renderFullBleedLayout(contentStream, document, storyPageData, storyBook);
            addPageHeaderFooter(contentStream, 2 + pageIndex, totalPdfPages);
        }
    }

    private void renderFullBleedLayout(PDPageContentStream contentStream, PDDocument document, StoryPage storyPageData, StoryBook storyBook) throws IOException {
        if (storyPageData.getImageUrl() != null && !storyPageData.getImageUrl().isBlank()) {
            PDImageXObject pdImage = loadImage(document, storyPageData.getImageUrl());
            if (pdImage != null) {
                contentStream.drawImage(pdImage, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
            }
        }

        drawTextPanel(contentStream, storyPageData.getText(), storyBook, 50, 120, PDRectangle.A4.getWidth() - 100, 220);
    }

    private void renderSplitLayout(PDPageContentStream contentStream, PDDocument document, StoryPage storyPageData, StoryBook storyBook) throws IOException {
        float imageHeight = 320;
        if (storyPageData.getImageUrl() != null && !storyPageData.getImageUrl().isBlank()) {
            PDImageXObject pdImage = loadImage(document, storyPageData.getImageUrl());
            if (pdImage != null) {
                float targetWidth = PDRectangle.A4.getWidth() - 100;
                float scale = Math.min(targetWidth / pdImage.getWidth(), imageHeight / pdImage.getHeight());
                float width = pdImage.getWidth() * scale;
                float height = pdImage.getHeight() * scale;
                float x = 50;
                float y = PDRectangle.A4.getHeight() - 80 - height;
                contentStream.drawImage(pdImage, x, y, width, height);
            }
        }

        drawTextPanel(contentStream, storyPageData.getText(), storyBook, 50, 120, PDRectangle.A4.getWidth() - 100, 260);
    }

    private void drawTextPanel(PDPageContentStream contentStream, String text, StoryBook storyBook, float x, float y, float width, float height) throws IOException {
        float fontSize = storyBook.getFontSize() != null ? storyBook.getFontSize().floatValue() : 18;
        String fontStyle = storyBook.getFontStyle() != null ? storyBook.getFontStyle() : "HELVETICA";
        PDType1Font font = PDType1Font.HELVETICA;
        if ("HELVETICA_BOLD".equals(fontStyle)) font = PDType1Font.HELVETICA_BOLD;
        else if ("TIMES_ROMAN".equals(fontStyle)) font = PDType1Font.TIMES_ROMAN;
        else if ("COURIER".equals(fontStyle)) font = PDType1Font.COURIER;

        Color fontColor = Color.decode(storyBook.getFontColor() != null ? storyBook.getFontColor() : "#000000");
        float leading = fontSize * 1.4f;
        List<String> lines = wrapText(text, font, fontSize, width - 60);

        float maxLineWidth = 0;
        for (String line : lines) {
            maxLineWidth = Math.max(maxLineWidth, font.getStringWidth(line) / 1000 * fontSize);
        }

        float padding = 16;
        float boxWidth = Math.min(width, maxLineWidth + padding * 2);
        float boxHeight = lines.size() * leading + padding * 2;
        float boxX = x + (width - boxWidth) / 2;
        float boxY = y + (height - boxHeight) / 2;

        addRoundedRectangle(contentStream, boxX, boxY, boxWidth, boxHeight, 16);
        contentStream.setNonStrokingColor(new Color(255, 255, 255, 180));
        contentStream.fill();

        float textY = boxY + boxHeight - padding - fontSize;
        for (String line : lines) {
            float lineWidth = font.getStringWidth(line) / 1000 * fontSize;
            float textX = boxX + (boxWidth - lineWidth) / 2;
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.setNonStrokingColor(fontColor);
            contentStream.newLineAtOffset(textX, textY);
            contentStream.showText(line);
            contentStream.endText();
            textY -= leading;
        }
    }

    private List<String> wrapText(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        String sanitizedText = sanitizeText(text, "");
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : sanitizedText.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            float size = font.getStringWidth(next) / 1000 * fontSize;
            if (size > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0) {
                    line.append(" ");
                }
                line.append(word);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private String sanitizeText(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        return text.replaceAll("\\r\\n|\\r|\\n", " ").trim();
    }

    private PDImageXObject loadImage(PDDocument document, String imageData) throws IOException {
        if (imageData == null || imageData.isBlank()) {
            return null;
        }

        BufferedImage bufferedImage;
        if (imageData.startsWith("data:image")) {
            String base64Part = imageData.split(",", 2)[1];
            byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Part);
            try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imageBytes)) {
                bufferedImage = ImageIO.read(bais);
            }
        } else {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) URI.create(imageData).toURL().openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            try (java.io.InputStream inputStream = connection.getInputStream()) {
                bufferedImage = ImageIO.read(inputStream);
            }
        }

        if (bufferedImage == null) {
            return null;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", baos);
        return PDImageXObject.createFromByteArray(document, baos.toByteArray(), "page-image");
    }

    private void addRoundedRectangle(PDPageContentStream contentStream, float x, float y, float width, float height, float radius) throws IOException {
        float b = radius * 0.552284749831f;
        contentStream.moveTo(x + radius, y);
        contentStream.lineTo(x + width - radius, y);
        contentStream.curveTo(x + width - radius + b, y, x + width, y + radius - b, x + width, y + radius);
        contentStream.lineTo(x + width, y + height - radius);
        contentStream.curveTo(x + width, y + height - radius + b, x + width - radius + b, y + height, x + width - radius, y + height);
        contentStream.lineTo(x + radius, y + height);
        contentStream.curveTo(x + radius - b, y + height, x, y + height - radius + b, x, y + height - radius);
        contentStream.lineTo(x, y + radius);
        contentStream.curveTo(x, y + radius - b, x + radius - b, y, x + radius, y);
        contentStream.closePath();
    }

    private void addPageHeaderFooter(PDPageContentStream contentStream, int pageNumber, int totalPages) throws IOException {
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 10);
        contentStream.setNonStrokingColor(new Color(120, 120, 120));
        contentStream.newLineAtOffset(50, PDRectangle.A4.getHeight() - 30);
        contentStream.showText("AI Kid Storybook Publisher");
        contentStream.endText();

        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 10);
        contentStream.setNonStrokingColor(new Color(120, 120, 120));
        String pageLabel = "Page " + pageNumber + " of " + totalPages;
        float textWidth = PDType1Font.HELVETICA_OBLIQUE.getStringWidth(pageLabel) / 1000 * 10;
        contentStream.newLineAtOffset(PDRectangle.A4.getWidth() - 50 - textWidth, 20);
        contentStream.showText(pageLabel);
        contentStream.endText();
    }
}
