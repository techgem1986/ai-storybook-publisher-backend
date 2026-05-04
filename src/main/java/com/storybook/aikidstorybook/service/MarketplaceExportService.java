package com.storybook.aikidstorybook.service;

import com.storybook.aikidstorybook.entity.StoryBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class MarketplaceExportService {

    private static final Logger logger = LoggerFactory.getLogger(MarketplaceExportService.class);

    /**
     * Compression level enum
     */
    public enum CompressionLevel {
        NONE("none", 0),
        LOW("low", 1),
        MEDIUM("medium", 5),
        HIGH("high", 9);

        private final String name;
        private final int level;

        CompressionLevel(String name, int level) {
            this.name = name;
            this.level = level;
        }

        public String getName() { return name; }
        public int getLevel() { return level; }
    }

    /**
     * Marketplace preset definitions
     */
    public enum MarketplacePreset {
        AMAZON_KDP("amazon-kdp", "Amazon KDP"),
        ETSY_PRINTABLES("etsy-printables", "Etsy Printables"),
        SELF_PUBLISH("self-publish", "Self-Publish"),
        DIGITAL_DOWNLOAD("digital-download", "Digital Download");

        private final String id;
        private final String displayName;

        MarketplacePreset(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Get export configuration for a specific marketplace preset
     */
    public ExportConfiguration getExportConfiguration(String presetId) {
        MarketplacePreset preset = null;
        try {
            preset = MarketplacePreset.valueOf(presetId.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid preset ID: {}, defaulting to DIGITAL_DOWNLOAD", presetId);
            preset = MarketplacePreset.DIGITAL_DOWNLOAD;
        }

        return createExportConfiguration(preset);
    }

    /**
     * Create export configuration based on marketplace preset
     */
    private ExportConfiguration createExportConfiguration(MarketplacePreset preset) {
        return switch (preset) {
            case AMAZON_KDP -> createAmazonKdpConfiguration();
            case ETSY_PRINTABLES -> createEtsyPrintablesConfiguration();
            case SELF_PUBLISH -> createSelfPublishConfiguration();
            case DIGITAL_DOWNLOAD -> createDigitalDownloadConfiguration();
        };
    }

    /**
     * Amazon KDP (Kindle Direct Publishing) configuration
     * Requirements: 6.625 x 9.25 inches, PDF format, 300 DPI
     */
    private ExportConfiguration createAmazonKdpConfiguration() {
        return ExportConfiguration.builder()
                .presetId(MarketplacePreset.AMAZON_KDP.getId())
                .presetName(MarketplacePreset.AMAZON_KDP.getDisplayName())
                .paperSize("letter") // 8.5 x 11 inches
                .pageWidth(6.625)
                .pageHeight(9.25)
                .marginTop(0.5)
                .marginBottom(0.5)
                .marginLeft(0.75)
                .marginRight(0.75)
                .bleedArea(0.125)
                .dpi(300)
                .colorMode("RGB")
                .includeCoverPage(true)
                .includeTitlePage(true)
                .includePageNumbers(true)
                .compression(CompressionLevel.MEDIUM)
                .fileFormat("PDF")
                .description("Optimized for Amazon KDP print-on-demand. Includes bleed area and crop marks.")
                .recommendedPageCount("20-50 pages")
                .additionalNotes("Use RGB color mode. Include 0.125\" bleed area on all sides.")
                .build();
    }

    /**
     * Etsy Printables configuration
     * Digital product optimized for screen viewing and printing
     */
    private ExportConfiguration createEtsyPrintablesConfiguration() {
        return ExportConfiguration.builder()
                .presetId(MarketplacePreset.ETSY_PRINTABLES.getId())
                .presetName(MarketplacePreset.ETSY_PRINTABLES.getDisplayName())
                .paperSize("letter")
                .pageWidth(8.5)
                .pageHeight(11.0)
                .marginTop(0.5)
                .marginBottom(0.5)
                .marginLeft(0.5)
                .marginRight(0.5)
                .bleedArea(0.0) // No bleed needed for printables
                .dpi(150) // Lower DPI for digital downloads
                .colorMode("RGB")
                .includeCoverPage(true)
                .includeTitlePage(false)
                .includePageNumbers(true)
                .compression(CompressionLevel.HIGH)
                .fileFormat("PDF")
                .description("Optimized for Etsy Printables. Digital format suitable for customer printing.")
                .recommendedPageCount("10-30 pages")
                .additionalNotes("Include thumbnail for preview. Use standard letter size for easy printing.")
                .build();
    }

    /**
     * Self-Publish configuration (general printing service like Lulu, CreateSpace)
     */
    private ExportConfiguration createSelfPublishConfiguration() {
        return ExportConfiguration.builder()
                .presetId(MarketplacePreset.SELF_PUBLISH.getId())
                .presetName(MarketplacePreset.SELF_PUBLISH.getDisplayName())
                .paperSize("letter")
                .pageWidth(6.0)
                .pageHeight(9.0)
                .marginTop(0.75)
                .marginBottom(0.75)
                .marginLeft(1.0)
                .marginRight(1.0)
                .bleedArea(0.25)
                .dpi(300)
                .colorMode("CMYK")
                .includeCoverPage(true)
                .includeTitlePage(true)
                .includePageNumbers(true)
                .compression(CompressionLevel.MEDIUM)
                .fileFormat("PDF")
                .description("Optimized for self-publishing services. Print-ready with CMYK color mode.")
                .recommendedPageCount("24-400 pages")
                .additionalNotes("Use CMYK for best color accuracy. Include back matter and ISBN placement.")
                .build();
    }

    /**
     * Digital Download configuration (generic e-reader compatible)
     */
    private ExportConfiguration createDigitalDownloadConfiguration() {
        return ExportConfiguration.builder()
                .presetId(MarketplacePreset.DIGITAL_DOWNLOAD.getId())
                .presetName(MarketplacePreset.DIGITAL_DOWNLOAD.getDisplayName())
                .paperSize("letter")
                .pageWidth(8.5)
                .pageHeight(11.0)
                .marginTop(0.5)
                .marginBottom(0.5)
                .marginLeft(0.5)
                .marginRight(0.5)
                .bleedArea(0.0)
                .dpi(72) // Screen resolution
                .colorMode("RGB")
                .includeCoverPage(true)
                .includeTitlePage(true)
                .includePageNumbers(true)
                .compression(CompressionLevel.HIGH)
                .fileFormat("PDF")
                .description("Optimized for digital viewing and screen reading.")
                .recommendedPageCount("10-100 pages")
                .additionalNotes("Optimized file size for faster download and email delivery.")
                .build();
    }

    /**
     * Export configuration builder class
     */
    public static class ExportConfiguration {
        private final String presetId;
        private final String presetName;
        private final String paperSize;
        private final double pageWidth;
        private final double pageHeight;
        private final double marginTop;
        private final double marginBottom;
        private final double marginLeft;
        private final double marginRight;
        private final double bleedArea;
        private final int dpi;
        private final String colorMode;
        private final boolean includeCoverPage;
        private final boolean includeTitlePage;
        private final boolean includePageNumbers;
        private final CompressionLevel compression;
        private final String fileFormat;
        private final String description;
        private final String recommendedPageCount;
        private final String additionalNotes;

        private ExportConfiguration(Builder builder) {
            this.presetId = builder.presetId;
            this.presetName = builder.presetName;
            this.paperSize = builder.paperSize;
            this.pageWidth = builder.pageWidth;
            this.pageHeight = builder.pageHeight;
            this.marginTop = builder.marginTop;
            this.marginBottom = builder.marginBottom;
            this.marginLeft = builder.marginLeft;
            this.marginRight = builder.marginRight;
            this.bleedArea = builder.bleedArea;
            this.dpi = builder.dpi;
            this.colorMode = builder.colorMode;
            this.includeCoverPage = builder.includeCoverPage;
            this.includeTitlePage = builder.includeTitlePage;
            this.includePageNumbers = builder.includePageNumbers;
            this.compression = builder.compression;
            this.fileFormat = builder.fileFormat;
            this.description = builder.description;
            this.recommendedPageCount = builder.recommendedPageCount;
            this.additionalNotes = builder.additionalNotes;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public String getPresetId() { return presetId; }
        public String getPresetName() { return presetName; }
        public String getPaperSize() { return paperSize; }
        public double getPageWidth() { return pageWidth; }
        public double getPageHeight() { return pageHeight; }
        public double getMarginTop() { return marginTop; }
        public double getMarginBottom() { return marginBottom; }
        public double getMarginLeft() { return marginLeft; }
        public double getMarginRight() { return marginRight; }
        public double getBleedArea() { return bleedArea; }
        public int getDpi() { return dpi; }
        public String getColorMode() { return colorMode; }
        public boolean isIncludeCoverPage() { return includeCoverPage; }
        public boolean isIncludeTitlePage() { return includeTitlePage; }
        public boolean isIncludePageNumbers() { return includePageNumbers; }
        public CompressionLevel getCompression() { return compression; }
        public String getFileFormat() { return fileFormat; }
        public String getDescription() { return description; }
        public String getRecommendedPageCount() { return recommendedPageCount; }
        public String getAdditionalNotes() { return additionalNotes; }

        public static class Builder {
            private String presetId;
            private String presetName;
            private String paperSize;
            private double pageWidth;
            private double pageHeight;
            private double marginTop;
            private double marginBottom;
            private double marginLeft;
            private double marginRight;
            private double bleedArea;
            private int dpi;
            private String colorMode;
            private boolean includeCoverPage;
            private boolean includeTitlePage;
            private boolean includePageNumbers;
            private CompressionLevel compression;
            private String fileFormat;
            private String description;
            private String recommendedPageCount;
            private String additionalNotes;

            public Builder presetId(String presetId) { this.presetId = presetId; return this; }
            public Builder presetName(String presetName) { this.presetName = presetName; return this; }
            public Builder paperSize(String paperSize) { this.paperSize = paperSize; return this; }
            public Builder pageWidth(double pageWidth) { this.pageWidth = pageWidth; return this; }
            public Builder pageHeight(double pageHeight) { this.pageHeight = pageHeight; return this; }
            public Builder marginTop(double marginTop) { this.marginTop = marginTop; return this; }
            public Builder marginBottom(double marginBottom) { this.marginBottom = marginBottom; return this; }
            public Builder marginLeft(double marginLeft) { this.marginLeft = marginLeft; return this; }
            public Builder marginRight(double marginRight) { this.marginRight = marginRight; return this; }
            public Builder bleedArea(double bleedArea) { this.bleedArea = bleedArea; return this; }
            public Builder dpi(int dpi) { this.dpi = dpi; return this; }
            public Builder colorMode(String colorMode) { this.colorMode = colorMode; return this; }
            public Builder includeCoverPage(boolean includeCoverPage) { this.includeCoverPage = includeCoverPage; return this; }
            public Builder includeTitlePage(boolean includeTitlePage) { this.includeTitlePage = includeTitlePage; return this; }
            public Builder includePageNumbers(boolean includePageNumbers) { this.includePageNumbers = includePageNumbers; return this; }
            public Builder compression(CompressionLevel compression) { this.compression = compression; return this; }
            public Builder fileFormat(String fileFormat) { this.fileFormat = fileFormat; return this; }
            public Builder description(String description) { this.description = description; return this; }
            public Builder recommendedPageCount(String recommendedPageCount) { this.recommendedPageCount = recommendedPageCount; return this; }
            public Builder additionalNotes(String additionalNotes) { this.additionalNotes = additionalNotes; return this; }

            public ExportConfiguration build() {
                return new ExportConfiguration(this);
            }
        }
    }

    /**
     * Get all available marketplace presets
     */
    public Map<String, String> getAllPresets() {
        Map<String, String> presets = new HashMap<>();
        for (MarketplacePreset preset : MarketplacePreset.values()) {
            presets.put(preset.getId(), preset.getDisplayName());
        }
        return presets;
    }
}
