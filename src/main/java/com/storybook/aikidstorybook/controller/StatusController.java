package com.storybook.aikidstorybook.controller;

import com.storybook.aikidstorybook.service.StatusEmitterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/status")
@CrossOrigin(origins = "*")
public class StatusController {

    @Autowired
    private StatusEmitterService statusEmitterService;

    @GetMapping("/{id}")
    public SseEmitter getStatus(@PathVariable Long id) {
        return statusEmitterService.createEmitter(id);
    }
}
