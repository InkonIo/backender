package com.example.backend.JWT;

import java.security.Key;
import java.util.Date;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    // Секретный ключ для подписи JWT токенов. Должен быть достаточно длинным (>= 256 бит или 32+ символов).
    private static final String SECRET_KEY = "supersecurekeysupersecurekeysupersecurekey"; 

    // Возвращает ключ для подписи токенов
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
    }

    // Извлекает email (subject) из токена
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject); 
    }

    // Извлекает конкретное утверждение (claim) из токена
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Извлекает все утверждения (claims) из токена
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // Генерирует новый JWT токен
    public String generateToken(String email) {
        return Jwts.builder()
                .setSubject(email) // Устанавливаем email как subject токена
                .setIssuedAt(new Date()) // Устанавливаем время выдачи токена (сейчас)
                // Устанавливаем срок действия токена: текущее время + 1000 мс/сек * 60 сек/мин * 60 мин/час = 1 час
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60)) // 1 час
                .signWith(getSigningKey(), SignatureAlgorithm.HS256) // Подписываем токен с использованием секретного ключа и алгоритма HS256
                .compact(); // Комбинируем все части в компактную строку JWT
    }

    // Проверяет валидность токена: соответствует ли email и не истек ли срок действия
    public boolean isTokenValid(String token, String expectedEmail) {
        String actualEmail = extractEmail(token);
        return actualEmail.equals(expectedEmail) && !isTokenExpired(token);
    }

    // Проверяет, истек ли срок действия токена
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // Извлекает дату истечения срока действия токена
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
}
