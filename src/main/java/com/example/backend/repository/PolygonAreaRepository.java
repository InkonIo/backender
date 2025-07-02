package com.example.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entiity.PolygonArea;

public interface PolygonAreaRepository extends JpaRepository<PolygonArea, UUID> {
    // Исправлено: для доступа к ID связанной сущности User используем findByUser_Id
    // Предполагается, что у сущности User есть поле 'id' типа Long.
    List<PolygonArea> findByUser_Id(Long userId);
}
