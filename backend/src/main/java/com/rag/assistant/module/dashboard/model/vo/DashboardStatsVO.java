package com.rag.assistant.module.dashboard.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsVO {

    private long kbCount;
    private long docCount;
    private long chatCount;
    private long chunkCount;
}
