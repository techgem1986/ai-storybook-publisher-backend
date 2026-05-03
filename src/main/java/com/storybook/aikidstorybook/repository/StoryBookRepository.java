package com.storybook.aikidstorybook.repository;

import com.storybook.aikidstorybook.entity.StoryBook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface StoryBookRepository extends JpaRepository<StoryBook, Long> {
    @Query("SELECT b FROM StoryBook b LEFT JOIN FETCH b.pages WHERE b.id = :id")
    Optional<StoryBook> findByIdWithPages(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE StoryBook b SET b.status = :status, b.lastStatus = :lastStatus WHERE b.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") String status, @Param("lastStatus") String lastStatus);
}