package com.gameexpert.ws.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ChatResponse {
    private final String type = "chat";
    private final String sender;
    private final String content;
    private final LocalDateTime timestamp;
}
