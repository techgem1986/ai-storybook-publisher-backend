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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
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

    @Async
    public void generatePdf(StoryBook storyBook) {
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
                addStoryPage(document, pages.get(i));
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

    private void addStoryPage(PDDocument document, StoryPage storyPageData) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            // Draw Image as background
            try {
                BufferedImage bufferedImage = ImageIO.read(new URL(storyPageData.getImageUrl()));
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(bufferedImage, "png", baos);
                PDImageXObject pdImage = PDImageXObject.createFromByteArray(document, baos.toByteArray(), "page-image");
                contentStream.drawImage(pdImage, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
            } catch (Exception e) {
                // Could not load image, leave background blank
            }

            // Add Text Overlay with Wrapping
            drawWrappedText(contentStream, storyPageData.getText());
        }
    }

    private void drawWrappedText(PDPageContentStream contentStream, String text) throws IOException {
        float fontSize = 18;
        float leading = 22;
        float margin = 50;
        float width = PDRectangle.A4.getWidth() - 2 * margin;
        float startX = margin;
        float startY = 150; // Position text at the bottom of the page

        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();

        for (String word : words) {
            float size = fontSize * PDType1Font.HELVETICA.getStringWidth(line + word) / 1000;
            if (size > width) {
                lines.add(line.toString());
                line = new StringBuilder(word + " ");
            } else {
                line.append(word).append(" ");
            }
        }
        lines.add(line.toString());

        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, fontSize);
        contentStream.setLeading(leading);
        contentStream.newLineAtOffset(startX, startY);

        for (String l : lines) {
            contentStream.showText(l);
            contentStream.newLine();
        }

        contentStream.endText();
    }
}
