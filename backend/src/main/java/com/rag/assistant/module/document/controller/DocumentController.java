package com.rag.assistant.module.document.controller;

import com.rag.assistant.common.model.PageResult;
import com.rag.assistant.common.model.R;
import com.rag.assistant.module.document.model.entity.Document;
import com.rag.assistant.module.document.model.vo.DocumentDetailVO;
import com.rag.assistant.module.document.model.vo.DocumentVO;
import com.rag.assistant.module.document.service.DocumentService;
import com.rag.assistant.module.document.mapper.DocumentMapper;
import com.rag.assistant.security.model.SecurityUser;
import com.rag.assistant.thirdparty.minio.MinioService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/doc")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final MinioService minioService;
    private final DocumentMapper documentMapper;

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<DocumentVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("kbId") Long kbId,
            @AuthenticationPrincipal SecurityUser securityUser) {
        return R.ok(documentService.upload(file, kbId, securityUser.getUserId()));
    }

    @GetMapping("/user")
    public R<PageResult<DocumentVO>> listByUser(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @AuthenticationPrincipal SecurityUser securityUser) {
        IPage<DocumentVO> result = documentService.pageByUserId(securityUser.getUserId(), page, size);
        return R.ok(new PageResult<>(result.getRecords(), (int) result.getTotal(), (int) result.getCurrent(), (int) result.getSize()));
    }

    @GetMapping("/{kbId}")
    public R<PageResult<DocumentVO>> list(
            @PathVariable Long kbId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @AuthenticationPrincipal SecurityUser securityUser) {
        IPage<DocumentVO> result = documentService.pageByKbId(kbId, page, size, securityUser.getUserId());
        return R.ok(new PageResult<>(result.getRecords(), (int) result.getTotal(), (int) result.getCurrent(), (int) result.getSize()));
    }

    @GetMapping("/detail/{id}")
    public R<DocumentDetailVO> detail(@PathVariable Long id,
                                      @AuthenticationPrincipal SecurityUser securityUser) {
        return R.ok(documentService.getDetail(id, securityUser.getUserId()));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @AuthenticationPrincipal SecurityUser securityUser) {
        documentService.deleteDocument(id, securityUser.getUserId());
        return R.ok();
    }

    @PostMapping("/{id}/retry")
    public R<Void> retry(@PathVariable Long id,
                         @AuthenticationPrincipal SecurityUser securityUser) {
        documentService.retryParse(id, securityUser.getUserId());
        return R.ok();
    }

    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id,
                         @AuthenticationPrincipal SecurityUser securityUser,
                         HttpServletResponse response) throws Exception {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            response.setStatus(404);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":4005,\"message\":\"文档不存在\"}");
            return;
        }
        if (!doc.getUserId().equals(securityUser.getUserId())) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":4004,\"message\":\"没有操作权限\"}");
            return;
        }

        String encodedName = URLEncoder.encode(doc.getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encodedName + "\"; filename*=UTF-8''" + encodedName);
        response.setHeader("Content-Length", String.valueOf(doc.getFileSize()));

        try (InputStream in = minioService.download(doc.getFilename());
             OutputStream out = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    @GetMapping("/{id}/preview-url")
    public R<String> previewUrl(@PathVariable Long id,
                                 @AuthenticationPrincipal SecurityUser securityUser) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            return R.fail(4005, "文档不存在");
        }
        if (!doc.getUserId().equals(securityUser.getUserId())) {
            return R.fail(4004, "没有操作权限");
        }
        String url = minioService.getFileUrl(doc.getFilename());
        return R.ok(url);
    }
}
