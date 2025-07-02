package com.example.backend.service;

import java.util.List;

import org.springframework.security.core.Authentication; // Убедитесь, что это правильный путь к вашей сущности User
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.backend.entiity.PolygonArea;
import com.example.backend.entiity.User;
import com.example.backend.repository.PolygonAreaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PolygonService {

    private final PolygonAreaRepository polygonAreaRepository;
    // Возможно, вам также понадобится UserRepository, если UserDetailsServiceImpl
    // не возвращает полный объект User, а только его ID или email.
    // private final UserRepository userRepository; // Раскомментируйте, если нужно

    public List<PolygonArea> getPolygonsForCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("Пользователь не аутентифицирован.");
        }

        // Получаем объект User из Principal.
        // Предполагается, что ваш User (который является UserDetails) имеет метод getId().
        // Если ваш principal - это просто строка (например, email), вам нужно будет
        // найти User по этому email через UserRepository.
        Object principal = authentication.getPrincipal();
        Long userId = null;

        if (principal instanceof User) {
            // Если principal - это ваш объект User, который содержит getId()
            userId = ((User) principal).getId();
        } else if (principal instanceof String) {
            // Если principal - это просто строка (например, email пользователя),
            // вам нужно будет найти пользователя по этому email, чтобы получить его ID.
            // Для этого понадобится UserRepository.
            // String email = (String) principal;
            // Optional<User> userOptional = userRepository.findByEmail(email);
            // if (userOptional.isPresent()) {
            //     userId = userOptional.get().getId();
            // }
            // В вашем UserDetailsServiceImpl вы загружаете User, поэтому principal должен быть User.
            // Если User.getId() возвращает UUID, а не Long, то тип userId должен быть UUID.
            // Для примера, если User.getId() возвращает Long, как предполагается для findByUser_Id(Long userId)
            System.err.println("Principal is a String, but expected User object. Check UserDetailsServiceImpl.");
            // Временно для отладки, если UserDetailsServiceImpl возвращает не User объект, а что-то другое
            // userId = Long.parseLong((String) principal); // Осторожно, это может быть не ID
            throw new IllegalStateException("Не удалось получить ID пользователя из контекста безопасности.");
        }

        if (userId == null) {
            throw new IllegalStateException("ID пользователя не найден в контексте безопасности.");
        }

        return polygonAreaRepository.findByUser_Id(userId);
    }
}
