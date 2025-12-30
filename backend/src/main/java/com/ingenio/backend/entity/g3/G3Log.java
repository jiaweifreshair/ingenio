package com.ingenio.backend.entity.g3;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class G3Log {
    private String timestamp;
    private String role; // PLAYER, COACH, EXECUTOR
    private String message;
    private String level; // info, warn, error, success
}
