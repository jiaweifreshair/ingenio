package com.ingenio.backend.module.g3.dto;

import lombok.Data;

@Data
public class G3TaskResponse {
    private String task_id;
    private String status;
    private String message;
}
