package com.storybook.aikidstorybook.repository;

import com.storybook.aikidstorybook.entity.StoryBook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoryBookRepository extends JpaRepository<StoryBook, Long> {
}