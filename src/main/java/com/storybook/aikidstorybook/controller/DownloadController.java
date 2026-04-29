package com.storybook.aikidstorybook.controller;

import com.storybook.aikidstorybook.entity.StoryBook;
import com.storybook.aikidstorybook.repository.StoryBookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

@RestController
@RequestMapping("/api/download")
public class DownloadController {

    @Autowired
    private StoryBookRepository storyBookRepository;

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> downloadStoryBookPDF(@PathVariable Long id) {
        Optional<StoryBook> storyBookOptional = storyBookRepository.findById(id);

        if (storyBookOptional.isEmpty() || !"COMPLETED".equals(storyBookOptional.get().getPdfStatus())) {
            return ResponseEntity.notFound().build();
        }

        StoryBook storyBook = storyBookOptional.get();
        File file = new File(storyBook.getPdfPath());

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] contents = Files.readAllBytes(file.toPath());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", file.getName());
            return new ResponseEntity<>(contents, headers, HttpStatus.OK);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}