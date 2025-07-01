// src/main/java/com/example/backend/dto/PolygonRequestDto.java
package com.example.backend.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class PolygonRequestDto {
    private UUID id; // Добавляем ID для использования в PUT запросах
    private String geoJson; // Теперь это поле будет содержать только GeoJSON Geometry
    private String name;    // Отдельное поле для имени
    private String crop;    // Отдельное поле для культуры
    private String comment; // Новое поле для комментария
}
