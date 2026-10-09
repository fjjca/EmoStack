package org.example.aispingboot.DTO.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConsultationSessionResponseDTO {
    private Long id;
    private Long userId;
    private String sessionTitle;
    private LocalDateTime startedAt;
    private String userNickname;
    private String lastMessageContent;
    private LocalDateTime lastMessageTime;
    private Integer messageCount;
    private Integer durationMinutes;
}