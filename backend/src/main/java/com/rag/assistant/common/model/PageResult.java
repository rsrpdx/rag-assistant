package com.rag.assistant.common.model;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {

    private int code;
    private String message;
    private List<T> records;
    private long total;
    private int page;
    private int size;

    public PageResult(List<T> records, long total, int page, int size) {
        this.code = 200;
        this.message = "操作成功";
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
    }
}
