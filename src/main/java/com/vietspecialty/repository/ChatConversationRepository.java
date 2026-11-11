package com.vietspecialty.repository;

import com.vietspecialty.domain.entity.ChatConversation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    @EntityGraph(attributePaths = {"messages"})
    Optional<ChatConversation> findByChatKey(String chatKey);

    Optional<ChatConversation> findTopByUserIdOrderByLastMessageAtDesc(Long userId);
}

