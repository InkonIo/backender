package com.example.backend.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.PolygonAreaResponseDto;
import com.example.backend.dto.PolygonRequestDto; // Убедитесь, что этот DTO существует и содержит поле 'color'
import com.example.backend.entiity.PolygonArea;
import com.example.backend.entiity.User;
import com.example.backend.repository.ChatMessageRepository;
import com.example.backend.repository.PolygonAreaRepository;
import com.example.backend.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/polygons")
@Slf4j // Для логирования
public class PolygonAreaController {

    @Autowired
    private PolygonAreaRepository polygonRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    // Метод для создания нового полигона
    @PostMapping
    public ResponseEntity<?> createPolygonArea(@RequestBody PolygonRequestDto requestDto, @AuthenticationPrincipal User user) {
        log.info("PolygonAreaController: Received request to create polygon.");

        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        try {
            // Фронтенд теперь отправляет geoJson как чистую геометрию, а name, crop, comment, color - как отдельные поля
            PolygonArea newPolygon = PolygonArea.builder()
                    .id(UUID.randomUUID()) // Генерируем новый UUID для ID полигона
                    .user(user)
                    .geoJson(requestDto.getGeoJson()) // Сохраняем только строку GeoJSON Geometry
                    .name(requestDto.getName())       // Сохраняем имя
                    .crop(requestDto.getCrop())       // Сохраняем культуру
                    .comment(requestDto.getComment()) // Сохраняем комментарий
                    .color(requestDto.getColor())     // <-- НОВОЕ: Сохраняем цвет из DTO запроса
                    .build();

            PolygonArea savedPolygon = polygonRepo.save(newPolygon);
            log.info("Polygon created successfully with ID: {}", savedPolygon.getId());
            // Возвращаем ID нового полигона в ответе, возможно в DTO или как строку
            return ResponseEntity.status(HttpStatus.CREATED).body(savedPolygon.getId().toString()); // Возвращаем ID как строку
        } catch (Exception e) {
            log.error("Error creating polygon: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error creating polygon: " + e.getMessage());
        }
    }

    // Метод для обновления существующего полигона
    @PutMapping("/{polygonId}")
    public ResponseEntity<?> updatePolygonArea(@PathVariable UUID polygonId, @RequestBody PolygonRequestDto requestDto, @AuthenticationPrincipal User user) {
        log.info("PolygonAreaController: Received request to update polygon with ID: {}", polygonId);

        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        return polygonRepo.findById(polygonId).map(polygon -> {
            if (!polygon.getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not authorized to update this polygon.");
            }

            try {
                // Обновляем все поля из requestDto
                polygon.setGeoJson(requestDto.getGeoJson()); // Обновляем только строку GeoJSON Geometry
                polygon.setName(requestDto.getName());       // Обновляем имя
                polygon.setCrop(requestDto.getCrop());       // Обновляем культуру
                polygon.setComment(requestDto.getComment()); // Обновляем комментарий
                polygon.setColor(requestDto.getColor());     // <-- НОВОЕ: Обновляем цвет из DTO запроса
                
                polygonRepo.save(polygon);
                log.info("Polygon updated successfully with ID: {}", polygonId);
                return ResponseEntity.ok("Polygon updated successfully.");
            } catch (Exception e) {
                log.error("Error updating polygon {}: {}", polygonId, e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error updating polygon: " + e.getMessage());
            }
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Polygon not found."));
    }

    // Метод для получения полигона по ID
    @GetMapping("/{polygonId}")
    public ResponseEntity<?> getPolygonAreaById(@PathVariable UUID polygonId, @AuthenticationPrincipal User user) {
        log.info("PolygonAreaController: Received request to get polygon with ID: {}", polygonId);
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        return polygonRepo.findById(polygonId)
                .map(polygon -> {
                    if (!polygon.getUser().getId().equals(user.getId())) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not authorized to view this polygon.");
                    }
                    // PolygonAreaResponseDto теперь берет name, crop, comment, color напрямую из сущности
                    return ResponseEntity.ok(new PolygonAreaResponseDto(polygon)); 
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Polygon not found."));
    }

    // Метод для получения всех полигонов пользователя
    @GetMapping("/my")
    public ResponseEntity<?> getAllPolygonsForUser(@AuthenticationPrincipal User user) {
        log.info("PolygonAreaController: Received request to get all polygons for user.");
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        // Использование findByUser_Id вместо findAllByUserId
        List<PolygonArea> polygons = polygonRepo.findByUser_Id(user.getId());
        List<PolygonAreaResponseDto> responseDtos = polygons.stream()
                .map(PolygonAreaResponseDto::new) // PolygonAreaResponseDto теперь берет name, crop, comment, color напрямую из сущности
                .collect(Collectors.toList());
        log.info("Found {} polygons for user {}.", responseDtos.size(), user.getEmail());
        return ResponseEntity.ok(responseDtos);
    }
    
    // Метод для удаления полигона по ID
    @DeleteMapping("/{polygonId}")
    @Transactional // Добавлено @Transactional
    public ResponseEntity<?> deletePolygonArea(@PathVariable UUID polygonId, @AuthenticationPrincipal User user) {
        log.info("PolygonAreaController: Received request to delete polygon with ID: {}", polygonId);

        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        return polygonRepo.findById(polygonId).map(polygon -> {
            if (!polygon.getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not authorized to delete this polygon.");
            }
            try {
                // ДОБАВЛЕНО: Сначала удаляем все связанные сообщения чата для этого полигона и пользователя
                chatMessageRepository.deleteByPolygonArea_IdAndUser_Id(polygon.getId(), user.getId());

                // Затем удаляем сам полигон
                polygonRepo.delete(polygon);
                log.info("Polygon deleted successfully with ID: {}", polygonId);
                return ResponseEntity.noContent().build(); // Изменено на 204 No Content
            } catch (Exception e) {
                log.error("Error deleting polygon {}: {}", polygonId, e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete polygon due to internal server error: " + e.getMessage()); // Добавлено сообщение об ошибке
            }
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("Polygon not found."));
    }

    // Метод для очистки всех полигонов пользователя
    @DeleteMapping("/clear-all")
    @Transactional // Добавлено @Transactional
    public ResponseEntity<?> deleteAllPolygonsForUser(@AuthenticationPrincipal User user) {
        log.info("PolygonAreaController: Received request to clear all polygons for user.");

        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        try {
            // Использование findByUser_Id вместо findAllByUserId
            List<PolygonArea> userPolygons = polygonRepo.findByUser_Id(user.getId());
            if (userPolygons.isEmpty()) {
                log.info("No polygons found to delete for user {}.", user.getEmail());
                return ResponseEntity.noContent().build(); // 204 No Content, даже если нечего удалять
            }

            // ДОБАВЛЕНО: Сначала удаляем сообщения чата для каждого полигона
            for (PolygonArea polygon : userPolygons) {
                chatMessageRepository.deleteByPolygonArea_IdAndUser_Id(polygon.getId(), user.getId());
            }

            polygonRepo.deleteAll(userPolygons);
            log.info("All polygons cleared successfully for user {}.", user.getEmail());
            return ResponseEntity.noContent().build(); // 204 No Content
        } catch (Exception e) {
            log.error("Error clearing all polygons for user {}: {}", user.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to clear all polygons due to internal server error: " + e.getMessage());
        }
    }
}
