package com.rag.assistant.module.kb.model.dto;

import lombok.Data;

@Data
public class UpdateKbRequest {

    private String name;
    private String description;

    private String category;
}
