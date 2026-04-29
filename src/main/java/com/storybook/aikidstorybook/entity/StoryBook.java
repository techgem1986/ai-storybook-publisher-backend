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

    private LocalDateTime createdAt;

    private String status; // PENDING, GENERATING, COMPLETED, FAILED

    private String pdfStatus; // NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED

    private String lastStatus;

    @OneToMany(mappedBy = "storyBook", cascade = CascadeType.ALL, orphanRemoval = true)
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