package com.storybook.aikidstorybook.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StatusEmitterService {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(Long id) {
        SseEmitter emitter = new SseEmitter(600000L); // 10 minutes timeout
        emitters.put(id, emitter);

        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        emitter.onError((e) -> emitters.remove(id));

        // Send initial connection event to keep connection alive
        try {
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data("Connected to status updates..."));
        } catch (IOException e) {
            emitters.remove(id);
        }

        return emitter;
    }

    public void sendStatus(Long id, String status) {
        SseEmitter emitter = emitters.get(id);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(status));
            } catch (IOException e) {
                emitters.remove(id);
            }
        }
    }

    public void complete(Long id) {
        SseEmitter emitter = emitters.remove(id);
        if (emitter != null) {
            emitter.complete();
        }
    }
}
