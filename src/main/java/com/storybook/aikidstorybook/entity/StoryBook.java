package com.storybook.aikidstorybook.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class StoryBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String description;

    private String ageGroup;

    private String writingStyle;

    private String genre;

    private Integer numberOfPages;

    private String fontColor;

    private Integer fontSize;

    private String fontStyle;

    private String textBackground;

    private LocalDateTime createdAt;

    private String status; // PENDING, GENERATING, COMPLETED, FAILED

    private String pdfStatus; // NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED

    private String lastStatus;

    @Column(columnDefinition = "TEXT")
    private String outline;

    @Column(columnDefinition = "TEXT")
    private String mainCharacters;

    private String setting;

    private String theme;

    @Column(columnDefinition = "TEXT")
    private String moral;

    @OneToMany(mappedBy = "storyBook", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("pageNumber ASC")
    private List<StoryPage> pages = new ArrayList<>();

    private String pdfPath;

    public StoryBook() {
        this.createdAt = LocalDateTime.now();
        this.status = "PENDING";
        this.pdfStatus = "NOT_STARTED";
    }

    public StoryBook(String title) {
        this();
        this.title = title;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAgeGroup() { return ageGroup; }
    public void setAgeGroup(String ageGroup) { this.ageGroup = ageGroup; }
    public String getWritingStyle() { return writingStyle; }
    public void setWritingStyle(String writingStyle) { this.writingStyle = writingStyle; }
    public Integer getNumberOfPages() { return numberOfPages; }
    public void setNumberOfPages(Integer numberOfPages) { this.numberOfPages = numberOfPages; }
    public String getFontColor() { return fontColor; }
    public void setFontColor(String fontColor) { this.fontColor = fontColor; }
    public Integer getFontSize() { return fontSize; }
    public void setFontSize(Integer fontSize) { this.fontSize = fontSize; }
    public String getFontStyle() { return fontStyle; }
    public void setFontStyle(String fontStyle) { this.fontStyle = fontStyle; }
    public String getTextBackground() { return textBackground; }
    public void setTextBackground(String textBackground) { this.textBackground = textBackground; }
    public String getOutline() { return outline; }
    public void setOutline(String outline) { this.outline = outline; }
    public String getMainCharacters() { return mainCharacters; }
    public void setMainCharacters(String mainCharacters) { this.mainCharacters = mainCharacters; }
    public String getSetting() { return setting; }
    public void setSetting(String setting) { this.setting = setting; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public String getMoral() { return moral; }
    public void setMoral(String moral) { this.moral = moral; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPdfStatus() { return pdfStatus; }
    public void setPdfStatus(String pdfStatus) { this.pdfStatus = pdfStatus; }
    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }
    public List<StoryPage> getPages() { return pages; }
    public void setPages(List<StoryPage> pages) { this.pages = pages; }
    public String getPdfPath() { return pdfPath; }
    public void setPdfPath(String pdfPath) { this.pdfPath = pdfPath; }

    public void addPage(StoryPage page) {
        pages.add(page);
        page.setStoryBook(this);
    }
}