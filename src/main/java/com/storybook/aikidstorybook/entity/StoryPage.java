package com.storybook.aikidstorybook.entity;

import jakarta.persistence.*;

@Entity
public class StoryPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int pageNumber;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_book_id")
    private StoryBook storyBook;

    public StoryPage() {
    }

    public StoryPage(int pageNumber, String text, String imageUrl) {
        this.pageNumber = pageNumber;
        this.text = text;
        this.imageUrl = imageUrl;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getPageNumber() { return pageNumber; }
    public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public StoryBook getStoryBook() { return storyBook; }
    public void setStoryBook(StoryBook storyBook) { this.storyBook = storyBook; }
}