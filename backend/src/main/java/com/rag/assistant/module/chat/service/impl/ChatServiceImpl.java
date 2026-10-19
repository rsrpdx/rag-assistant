package com.rag.assistant.module.chat.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.assistant.common.enums.ResultCodeEnum;
import com.rag.assistant.common.exception.BusinessException;
import com.rag.assistant.module.chat.mapper.ChatMessageMapper;
import com.rag.assistant.module.chat.mapper.ChatSessionMapper;
import com.rag.assistant.module.chat.model.entity.ChatMessage;
import com.rag.assistant.module.chat.model.entity.ChatSession;
import com.rag.assistant.module.chat.model.vo.MessageVO;
import com.rag.assistant.module.chat.service.ChatService;
import com.rag.assistant.module.rag.model.RetrievedChunk;
import com.rag.assistant.module.rag.service.QdrantSearchService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final QdrantSearchService qdrantSearchService;
    private final OpenAiStreamingChatModel streamingChatModel;
    private final ObjectMapper objectMapper;

    @Override
    public MessageVO ask(Long sessionId, String question, Long userId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "\u4f1a\u8bdd\u4e0d\u5b58\u5728");
        }

        long startTime = System.currentTimeMillis();
        saveMessage(sessionId, "USER", question, null, 0);

        try {
            List<RetrievedChunk> matches = qdrantSearchService.search(question);
            String context = buildContext(matches);

            String systemPrompt = "\u4f60\u662f\u4e00\u4e2a\u57fa\u4e8e\u77e5\u8bc6\u5e93\u7684AI\u95ee\u7b54\u52a9\u624b\u3002\u8bf7\u6839\u636e\u4ee5\u4e0b\u63d0\u4f9b\u7684\u6587\u6863\u5185\u5bb9\uff0c\u51c6\u786e\u3001\u7b80\u6d01\u5730\u56de\u7b54\u7528\u6237\u95ee\u9898\u3002" +
                    "\u5982\u679c\u6587\u6863\u5185\u5bb9\u4e0d\u8db3\u4ee5\u56de\u7b54\u95ee\u9898\uff0c\u8bf7\u5982\u5b9e\u8bf4\u660e\u4e0d\u8981\u7f16\u9020\u3002\u56de\u7b54\u4f7f\u7528Markdown\u683c\u5f0f\u3002\n\n" +
                    "\u6587\u6863\u5185\u5bb9\uff1a\n" + context;

            CompletableFuture<String> futureAnswer = new CompletableFuture<>();
            AtomicReference<String> collectedAnswer = new AtomicReference<>("");

            streamingChatModel.generate(
                List.of(new SystemMessage(systemPrompt), new UserMessage(question)),
                new StreamingResponseHandler<AiMessage>() {
                    @Override
                    public void onNext(String token) {
                        if (token != null && !token.isEmpty()) {
                            collectedAnswer.updateAndGet(v -> v + token);
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        String answer = collectedAnswer.get();
                        if (answer.isEmpty() && response != null && response.content() != null) {
                            answer = response.content().text();
                        }
                        futureAnswer.complete(answer != null ? answer : "");
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("LLM generate error", error);
                        futureAnswer.completeExceptionally(error);
                    }
                });

            String answer = futureAnswer.get(120, TimeUnit.SECONDS);

            String sourceJson = buildSourceJson(matches);
            saveMessage(sessionId, "AI", answer, sourceJson, 0);

            if (session.getTitle() == null) {
                session.setTitle(question.length() > 30 ? question.substring(0, 30) + "..." : question);
            }
            session.setMessageCount(session.getMessageCount() + 2);
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionMapper.updateById(session);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Chat completed: session={}, elapsed={}ms, answerLen={}", sessionId, elapsed, answer.length());

            return toMessageVO(sessionId, answer, sourceJson);
        } catch (Exception e) {
            log.error("Chat processing error", e);
            String errorMsg = e.getMessage();
            saveMessage(sessionId, "AI", errorMsg, null, 0);
            return toMessageVO(sessionId, errorMsg, null);
        }
    }

    
    @Override
    public SseEmitter askStream(Long sessionId, String question, Long userId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "会话不存在");
        }

        SseEmitter emitter = new SseEmitter(120000L); // 2min timeout

        saveMessage(sessionId, "USER", question, null, 0);

        CompletableFuture.runAsync(() -> {
            try {
                List<RetrievedChunk> matches = qdrantSearchService.search(question);
                String context = buildContext(matches);
                String sourceJson = buildSourceJson(matches);

                String systemPrompt = "你是一个基于知识库的AI问答助手。请根据以下提供的文档内容，准确、简洁地回答用户问题。" +
                    "如果文档内容不足以回答问题，请如实说明不要编造。回答使用Markdown格式。\n\n" +
                    "文档内容：\n" + context;

                AtomicReference<String> fullAnswer = new AtomicReference<>("");
                CompletableFuture<Boolean> streamDone = new CompletableFuture<>();

                streamingChatModel.generate(
                    List.of(new SystemMessage(systemPrompt), new UserMessage(question)),
                    new StreamingResponseHandler<AiMessage>() {
                        @Override
                        public void onNext(String token) {
                            if (token != null && !token.isEmpty()) {
                                fullAnswer.updateAndGet(v -> v + token);
                                try {
                                    emitter.send(SseEmitter.event().data(token));
                                } catch (IOException e) {
                                    streamDone.complete(false);
                                }
                            }
                        }

                        @Override
                        public void onComplete(Response<AiMessage> response) {
                            try {
                                String answer = fullAnswer.get();
                                if (answer.isEmpty() && response != null && response.content() != null) {
                                    answer = response.content().text();
                                }
                                // Save final answer with sources
                                saveMessage(sessionId, "AI", answer, sourceJson, 0);
                                // Update session
                                if (session.getTitle() == null && !answer.isEmpty()) {
                                    session.setTitle(answer.substring(0, Math.min(30, answer.length())));
                                }
                                session.setMessageCount(session.getMessageCount() + 2);
                                session.setUpdatedAt(LocalDateTime.now());
                                chatSessionMapper.updateById(session);
                                // Send done event with sources
                                Map<String, Object> doneData = new HashMap<>();
                                doneData.put("type", "done");
                                doneData.put("sources", sourceJson);
                                emitter.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(doneData)));
                                emitter.complete();
                            } catch (Exception e) {
                                log.error("SSE complete error", e);
                                emitter.completeWithError(e);
                            }
                            streamDone.complete(true);
                        }

                        @Override
                        public void onError(Throwable error) {
                            log.error("LLM stream error", error);
                            try {
                                emitter.send(SseEmitter.event().name("error").data(error.getMessage()));
                            } catch (IOException ignored) {}
                            emitter.completeWithError(error);
                            streamDone.completeExceptionally(error);
                        }
                    });

                streamDone.get(120, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Chat streaming error", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private MessageVO toMessageVO(Long sessionId, String content, String sources) {
        MessageVO vo = new MessageVO();
        vo.setId(null);
        vo.setSessionId(sessionId);
        vo.setRole("AI");
        vo.setContent(content);
        vo.setSources(sources);
        vo.setTokensUsed(0);
        vo.setCreatedAt(LocalDateTime.now());
        return vo;
    }

    @Override
    public List<MessageVO> getMessages(Long sessionId, Long userId, Integer offset, Integer limit) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodeEnum.RESOURCE_NOT_FOUND, "\u4f1a\u8bdd\u4e0d\u5b58\u5728");
        }
        List<ChatMessage> all = chatMessageMapper.selectBySessionId(sessionId);
        int total = all.size();
        int fromIndex = Math.max(0, total - offset - limit);
        int toIndex = Math.max(0, total - offset);
        return all.subList(fromIndex, toIndex).stream().map(this::toVO).toList();
    }

    private MessageVO toVO(ChatMessage m) {
        MessageVO vo = new MessageVO();
        vo.setId(m.getId());
        vo.setSessionId(m.getSessionId());
        vo.setRole(m.getRole());
        vo.setContent(m.getContent());
        vo.setSources(m.getSources());
        vo.setTokensUsed(m.getTokensUsed());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }

    private void saveMessage(Long sessionId, String role, String content, String sources, int tokensUsed) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSources(sources);
        message.setTokensUsed(tokensUsed);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);
    }

    private String buildContext(List<RetrievedChunk> matches) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < matches.size(); i++) {
            sb.append("[").append(i + 1).append("] ")
                    .append(matches.get(i).text())
                    .append("\n\n");
        }
        return sb.toString();
    }

    private String buildSourceJson(List<RetrievedChunk> matches) {
        try {
            List<Map<String, Object>> sources = matches.stream().map(m -> {
                Map<String, Object> item = new HashMap<>();
                item.put("score", m.score());
                item.put("content", m.text());
                item.put("docId", m.metadata().get("docId"));
                item.put("docName", m.metadata().get("docName"));
                return item;
            }).toList();
            return objectMapper.writeValueAsString(sources);
        } catch (Exception e) {
            return "[]";
        }
    }
}