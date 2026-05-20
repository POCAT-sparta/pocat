package com.rocketcrew.pocat.domain.chat.repository;

import com.rocketcrew.pocat.domain.chat.dto.response.ChatRoomListResponse;

import java.util.List;

public interface ChatRepositoryCustom {

    List<ChatRoomListResponse> findMyChatsWithDetails(Long userId);
}
