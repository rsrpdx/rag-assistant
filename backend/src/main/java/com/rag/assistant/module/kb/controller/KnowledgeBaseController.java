package com.rag.assistant.module.kb.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.rag.assistant.common.model.PageResult;
import com.rag.assistant.common.model.R;
import com.rag.assistant.module.kb.model.dto.CreateKbRequest;
import com.rag.assistant.module.kb.model.dto.UpdateKbRequest;
import com.rag.assistant.module.kb.model.vo.KbDetailVO;
import com.rag.assistant.module.kb.model.vo.CategoryVO;
import com.rag.assistant.module.kb.model.vo.KbListVO;
import com.rag.assistant.module.kb.model.vo.KbStatsVO;
import com.rag.assistant.module.kb.service.KnowledgeBaseService;
import com.rag.assistant.security.model.SecurityUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping
    public R<PageResult<KbListVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String category,
            @AuthenticationPrincipal SecurityUser securityUser) {
        IPage<KbListVO> result = knowledgeBaseService.pageByUser(securityUser.getUserId(), page, size, category);
        return R.ok(new PageResult<>(result.getRecords(), (int) result.getTotal(), (int) result.getCurrent(), (int) result.getSize()));
    }

    @PostMapping
    public R<KbDetailVO> create(
            @Valid @RequestBody CreateKbRequest request,
            @AuthenticationPrincipal SecurityUser securityUser) {
        return R.ok(knowledgeBaseService.create(securityUser.getUserId(), request));
    }

    @GetMapping("/{id}")
    public R<KbDetailVO> detail(@PathVariable Long id,
                                @AuthenticationPrincipal SecurityUser securityUser) {
        return R.ok(knowledgeBaseService.getDetail(id, securityUser.getUserId()));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody UpdateKbRequest request,
                          @AuthenticationPrincipal SecurityUser securityUser) {
        knowledgeBaseService.update(id, request, securityUser.getUserId());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @AuthenticationPrincipal SecurityUser securityUser) {
        knowledgeBaseService.delete(id, securityUser.getUserId());
        return R.ok();
    }

    @GetMapping("/categories")
    public R<java.util.List<CategoryVO>> categories(
            @AuthenticationPrincipal SecurityUser securityUser) {
        return R.ok(knowledgeBaseService.getCategories(securityUser.getUserId()));
    }

    @GetMapping("/{id}/stats")
    public R<KbStatsVO> stats(@PathVariable Long id,
                              @AuthenticationPrincipal SecurityUser securityUser) {
        return R.ok(knowledgeBaseService.getStats(id, securityUser.getUserId()));
    }
}
