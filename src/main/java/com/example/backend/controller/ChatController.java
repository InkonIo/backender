package com.example.backend.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
// import org.springframework.web.bind.annotation.CrossOrigin; // Удален импорт CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.JWT.JwtService;
import com.example.backend.dto.ChatMessageDto;
import com.example.backend.entiity.ChatMessage;
import com.example.backend.entiity.PolygonArea;
import com.example.backend.entiity.User;
import com.example.backend.repository.ChatMessageRepository;
import com.example.backend.repository.PolygonAreaRepository;
import com.example.backend.service.PolygonService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@RestController
// Удалена аннотация @CrossOrigin(origins = "*")
@RequestMapping("/api")
public class ChatController {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${openai.api.url}")
    private String openaiApiUrl;

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final PolygonService polygonService;
    private final JwtService jwtService;
    private final ChatMessageRepository chatMessageRepository;
    private final PolygonAreaRepository polygonAreaRepository;

    public ChatController(PolygonService polygonService, JwtService jwtService,
                          ChatMessageRepository chatMessageRepository,
                          PolygonAreaRepository polygonAreaRepository) {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.polygonService = polygonService;
        this.jwtService = jwtService;
        this.chatMessageRepository = chatMessageRepository;
        this.polygonAreaRepository = polygonAreaRepository;
    }

    @GetMapping("/chat/polygons/{polygonId}")
    public ResponseEntity<?> getPolygonChatHistory(@PathVariable UUID polygonId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof User)) {
            return new ResponseEntity<>(
                    Collections.singletonMap("error", "Пользователь не аутентифицирован или ID не найден."),
                    HttpStatus.UNAUTHORIZED
                );
            }
            User currentUser = (User) authentication.getPrincipal();
            Long userId = currentUser.getId();

            Optional<PolygonArea> polygonOptional = polygonAreaRepository.findById(polygonId);
            if (polygonOptional.isEmpty() || !polygonOptional.get().getUser().getId().equals(userId)) {
                return new ResponseEntity<>(
                        Collections.singletonMap("error", "Полигон не найден или не принадлежит текущему пользователю."),
                        HttpStatus.FORBIDDEN
                );
            }

            List<ChatMessage> messages = chatMessageRepository.findByUser_IdAndPolygonArea_IdOrderByTimestampAsc(userId, polygonId);
            
            // Преобразуем ChatMessage в ChatMessageDto
            List<ChatMessageDto> messageDtos = messages.stream()
                    .map(msg -> ChatMessageDto.builder()
                            .id(msg.getId())
                            .sender(msg.getSender())
                            .text(msg.getText())
                            .timestamp(msg.getTimestamp())
                            .build())
                    .collect(Collectors.toList());

            return new ResponseEntity<>(messageDtos, HttpStatus.OK); // Возвращаем DTO
        }


        @PostMapping("/chat/polygons/{polygonId}/messages")
        public ResponseEntity<?> handlePolygonChatMessage(@PathVariable UUID polygonId, @RequestBody Map<String, Object> payload) {
            String userMessage = (String) payload.get("message");
            List<Map<String, String>> history = (List<Map<String, String>>) payload.get("history"); 
            
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = null;
            Long userId = null; // Изменено на Long

            if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof User) {
                currentUser = (User) authentication.getPrincipal();
                userId = currentUser.getId(); // Получаем Long ID
                System.out.println("Authenticated User ID: " + userId + " (from User entity)");
            } else {
                System.err.println("Ошибка: Пользователь не аутентифицирован или Principal не является User.");
                return new ResponseEntity<>(
                        Collections.singletonMap("error", "Пользователь не аутентифицирован."),
                        HttpStatus.UNAUTHORIZED
                );
            }

            if (userMessage == null || userMessage.trim().isEmpty()) {
                return new ResponseEntity<>(
                        Collections.singletonMap("error", "Сообщение не предоставлено."),
                        HttpStatus.BAD_REQUEST
                );
            }

            Optional<PolygonArea> polygonOptional = polygonAreaRepository.findById(polygonId);
            if (polygonOptional.isEmpty() || !polygonOptional.get().getUser().getId().equals(currentUser.getId())) {
                return new ResponseEntity<>(
                        Collections.singletonMap("error", "Полигон не найден или не принадлежит текущему пользователю."),
                        HttpStatus.FORBIDDEN
                );
            }
            PolygonArea polygonArea = polygonOptional.get();

            ChatMessage userChatMessage = ChatMessage.builder()
                    .polygonArea(polygonArea)
                    .user(currentUser)
                    .sender("user")
                    .text(userMessage)
                    .timestamp(LocalDateTime.now())
                    .build();
            chatMessageRepository.save(userChatMessage);

            System.out.println("Получено сообщение от userId: " + userId + ". Сообщение: " + userMessage + " для полигона: " + polygonId);

            try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", "gpt-3.5-turbo");
                
                com.google.gson.JsonArray messagesArray = new com.google.gson.JsonArray();
                if (history != null) {
                    for (Map<String, String> msg : history) {
                        JsonObject msgObj = new JsonObject();
                        msgObj.addProperty("role", msg.get("role"));
                        msgObj.addProperty("content", msg.get("content"));
                        messagesArray.add(msgObj);
                    }
                }
                requestBody.add("messages", messagesArray);
                
                requestBody.addProperty("max_tokens", 150);

                okhttp3.RequestBody body = okhttp3.RequestBody.create(
                        requestBody.toString(), MediaType.parse("application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url(openaiApiUrl)
                        .header("Authorization", "Bearer " + openaiApiKey)
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No error body";
                        System.err.println("OpenAI API Error: " + response.code() + " - " + errorBody);
                        ChatMessage errorChatMessage = ChatMessage.builder()
                                .polygonArea(polygonArea)
                                .user(currentUser)
                                .sender("ai")
                                .text("Ошибка при запросе к OpenAI: " + errorBody)
                                .timestamp(LocalDateTime.now())
                                .build();
                        chatMessageRepository.save(errorChatMessage);

                        return new ResponseEntity<>(
                                Collections.singletonMap("error", "Ошибка при запросе к OpenAI: " + errorBody),
                                HttpStatus.INTERNAL_SERVER_ERROR
                        );
                    }

                    String responseBody = response.body() != null ? response.body().string() : "{}";
                    
                    JsonObject openaiResponse = gson.fromJson(responseBody, JsonObject.class);
                    String botReply = "Извините, не удалось получить ответ.";

                    if (openaiResponse != null && openaiResponse.has("choices")) {
                        com.google.gson.JsonArray choices = openaiResponse.getAsJsonArray("choices");
                        if (choices != null && choices.size() > 0) {
                            JsonObject firstChoice = choices.get(0).getAsJsonObject();
                            if (firstChoice != null && firstChoice.has("message")) {
                                JsonObject message = firstChoice.getAsJsonObject("message");
                                if (message != null && message.has("content")) {
                                    botReply = message.get("content").getAsString();
                                }
                            }
                        }
                    }
                    
                    ChatMessage aiChatMessage = ChatMessage.builder()
                            .polygonArea(polygonArea)
                            .user(currentUser)
                            .sender("ai")
                            .text(botReply)
                            .timestamp(LocalDateTime.now())
                            .build();
                    chatMessageRepository.save(aiChatMessage);

                    return new ResponseEntity<>(
                            Collections.singletonMap("reply", botReply),
                            HttpStatus.OK
                        );
                    }
                } catch (IOException e) {
                    System.err.println("Network/IO Error: " + e.getMessage());
                    ChatMessage errorChatMessage = ChatMessage.builder()
                            .polygonArea(polygonArea)
                            .user(currentUser)
                            .sender("ai")
                            .text("Ошибка сервера: " + e.getMessage())
                            .timestamp(LocalDateTime.now())
                            .build();
                    chatMessageRepository.save(errorChatMessage);
                    return new ResponseEntity<>(
                            Collections.singletonMap("error", "Ошибка сервера: " + e.getMessage()),
                            HttpStatus.INTERNAL_SERVER_ERROR
                    );
                } catch (JsonParseException e) {
                    System.err.println("JSON Parsing Error: " + e.getMessage());
                    ChatMessage errorChatMessage = ChatMessage.builder()
                            .polygonArea(polygonArea)
                            .user(currentUser)
                            .sender("ai")
                            .text("Ошибка обработки ответа от OpenAI.")
                            .timestamp(LocalDateTime.now())
                            .build();
                    chatMessageRepository.save(errorChatMessage);
                    return new ResponseEntity<>(
                            Collections.singletonMap("error", "Ошибка обработки ответа от OpenAI."),
                            HttpStatus.INTERNAL_SERVER_ERROR
                    );
                } catch (Exception e) {
                    System.err.println("Unexpected Error: " + e.getMessage());
                    ChatMessage errorChatMessage = ChatMessage.builder()
                            .polygonArea(polygonArea)
                            .user(currentUser)
                            .sender("ai")
                            .text("Произошла непредвиденная ошибка.")
                            .timestamp(LocalDateTime.now())
                            .build();
                    chatMessageRepository.save(errorChatMessage);
                    return new ResponseEntity<>(
                            Collections.singletonMap("error", "Произошла непредвиденная ошибка."),
                            HttpStatus.INTERNAL_SERVER_ERROR
                    );
                }
            }

    @DeleteMapping("/chat/polygons/{polygonId}/messages")
    @Transactional // Добавлена аннотация @Transactional для операций удаления
    public ResponseEntity<?> clearChatHistory(@PathVariable UUID polygonId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof User)) {
            return new ResponseEntity<>(
                    Collections.singletonMap("error", "Пользователь не аутентифицирован."),
                    HttpStatus.UNAUTHORIZED
                );
            }
            User currentUser = (User) authentication.getPrincipal();
            Long userId = currentUser.getId();

            Optional<PolygonArea> polygonOptional = polygonAreaRepository.findById(polygonId);
            if (polygonOptional.isEmpty() || !polygonOptional.get().getUser().getId().equals(userId)) {
                return new ResponseEntity<>(
                        Collections.singletonMap("error", "Полигон не найден или не принадлежит текущему пользователю."),
                        HttpStatus.FORBIDDEN
                );
            }

            // Удаляем все сообщения чата для данного полигона и пользователя
            chatMessageRepository.deleteByPolygonArea_IdAndUser_Id(polygonId, userId);

            // Возвращаем 204 No Content, что означает успешное выполнение без возврата тела ответа
            return ResponseEntity.noContent().build();
        }
}
