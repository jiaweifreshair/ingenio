package com.ingenio.backend.module.g3.dto;

import lombok.Data;

@Data
public class G3LogEntry {
    private Double timestamp;
    private String role;
    private String content;
    private String level;
}
