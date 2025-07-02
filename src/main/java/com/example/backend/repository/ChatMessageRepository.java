package com.example.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entiity.ChatMessage;

import jakarta.transaction.Transactional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    // Найти все сообщения для конкретного полигона, отсортированные по времени
    List<ChatMessage> findByPolygonArea_IdOrderByTimestampAsc(UUID polygonAreaId);
    // Найти все сообщения для конкретного пользователя и полигона
    List<ChatMessage> findByUser_IdAndPolygonArea_IdOrderByTimestampAsc(Long userId, UUID polygonAreaId);
    // Метод для удаления всех сообщений по ID полигона и ID пользователя
    @Transactional // Аннотация @Transactional необходима для операций удаления
    void deleteByPolygonArea_IdAndUser_Id(UUID polygonId, Long userId);
}