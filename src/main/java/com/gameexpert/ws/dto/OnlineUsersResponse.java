package com.gameexpert.ws.dto;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class OnlineUsersResponse {
    private final String type = "onlineUsers";
    private final List<String> users;
    private final int count;
}
