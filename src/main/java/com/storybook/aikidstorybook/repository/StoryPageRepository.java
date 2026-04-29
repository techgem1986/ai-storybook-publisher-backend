package com.storybook.aikidstorybook.repository;

import com.storybook.aikidstorybook.entity.StoryPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoryPageRepository extends JpaRepository<StoryPage, Long> {
}