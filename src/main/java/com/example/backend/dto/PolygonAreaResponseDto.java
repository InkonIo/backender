package com.example.backend.dto;

import java.util.UUID;

import com.example.backend.entiity.PolygonArea;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PolygonAreaResponseDto {
    private UUID id;
    private String name;    // Теперь это поле берется напрямую из сущности
    private String geoJson; // Теперь это поле содержит только GeoJSON Geometry
    private String crop;    // Теперь это поле берется напрямую из сущности
    private String comment; // Новое поле для комментария, берется напрямую из сущности
    private String color;   // НОВОЕ ПОЛЕ: для передачи цвета полигона на фронтенд

    public PolygonAreaResponseDto(PolygonArea polygonArea) {
        this.id = polygonArea.getId();
        this.geoJson = polygonArea.getGeoJson();
        this.name = polygonArea.getName();      // Присваиваем напрямую из сущности
        this.crop = polygonArea.getCrop();      // Присваиваем напрямую из сущности
        this.comment = polygonArea.getComment(); // Присваиваем напрямую из сущности
        this.color = polygonArea.getColor();    // НОВОЕ ПРИСВОЕНИЕ: цвета из сущности
    }
}
