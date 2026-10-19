package com.rag.assistant.module.chat.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SseEmitterManager {

    private final Map<Long, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    public SseEmitter create(Long sessionId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitterMap.put(sessionId, emitter);

        emitter.onCompletion(() -> emitterMap.remove(sessionId));
        emitter.onTimeout(() -> emitterMap.remove(sessionId));
        emitter.onError(e -> emitterMap.remove(sessionId));

        return emitter;
    }

    public void send(Long sessionId, String eventName, String data) {
        SseEmitter emitter = emitterMap.get(sessionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                log.warn("SSE send failed for session {}, removing emitter", sessionId);
                emitterMap.remove(sessionId);
            }
        }
    }

    public void complete(Long sessionId) {
        SseEmitter emitter = emitterMap.get(sessionId);
        if (emitter != null) {
            emitter.complete();
            emitterMap.remove(sessionId);
        }
    }

    public void error(Long sessionId, String errorMsg) {
        SseEmitter emitter = emitterMap.get(sessionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("error").data(errorMsg));
                emitter.completeWithError(new RuntimeException(errorMsg));
            } catch (IOException e) {
                log.warn("SSE error send failed for session {}", sessionId);
            }
            emitterMap.remove(sessionId);
        }
    }
}
