package com.rag.assistant.module.chat.controller;

import com.rag.assistant.common.model.R;
import com.rag.assistant.module.chat.model.dto.AskRequest;
import com.rag.assistant.module.chat.model.dto.CreateSessionRequest;
import com.rag.assistant.module.chat.model.vo.MessageVO;
import com.rag.assistant.module.chat.model.vo.SessionVO;
import com.rag.assistant.module.chat.service.ChatService;
import com.rag.assistant.module.chat.service.ChatSessionService;
import com.rag.assistant.security.model.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;
import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatSessionService chatSessionService;
    private final ChatService chatService;

    @GetMapping("/session")
    public R<List<SessionVO>> listSessions(
            @RequestParam Long kbId,
            @AuthenticationPrincipal SecurityUser securityUser) {
        return R.ok(chatSessionService.listByUserAndKb(securityUser.getUserId(), kbId));
    }

    @PostMapping("/session")
    public R<SessionVO> createSession(
            @Valid @RequestBody CreateSessionRequest request,
            @AuthenticationPrincipal SecurityUser securityUser) {
        return R.ok(chatSessionService.create(securityUser.getUserId(), request.getKbId(), request.getTitle()));
    }

    @DeleteMapping("/session/{id}")
    public R<Void> deleteSession(
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUser securityUser) {
        chatSessionService.deleteSession(id, securityUser.getUserId());
        return R.ok();
    }

    @GetMapping("/session/{id}/messages")
    public R<List<MessageVO>> messages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") Integer offset,
            @RequestParam(defaultValue = "50") Integer limit,
            @AuthenticationPrincipal SecurityUser securityUser) {
        return R.ok(chatService.getMessages(id, securityUser.getUserId(), offset, limit));
    }

    @GetMapping(value = "/session/{id}/ask-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(
            @PathVariable Long id,
            @RequestParam String question,
            @AuthenticationPrincipal SecurityUser securityUser) {
        return chatService.askStream(id, question, securityUser.getUserId());
    }

    @PostMapping("/session/{id}/ask")
    public R<MessageVO> ask(
            @PathVariable Long id,
            @Valid @RequestBody AskRequest request,
            @AuthenticationPrincipal SecurityUser securityUser) {
        return R.ok(chatService.ask(id, request.getQuestion(), securityUser.getUserId()));
    }
}
