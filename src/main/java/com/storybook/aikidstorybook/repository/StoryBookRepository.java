package com.storybook.aikidstorybook.repository;

import com.storybook.aikidstorybook.entity.StoryBook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoryBookRepository extends JpaRepository<StoryBook, Long> {
    @Query("SELECT b FROM StoryBook b LEFT JOIN FETCH b.pages WHERE b.id = :id")
    Optional<StoryBook> findByIdWithPages(@Param("id") Long id);
}