package com.example.backend.JWT;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.util.Optional;

import com.example.backend.repository.UserRepository;
import com.example.backend.entiity.User; 

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Добавьте этот импорт для логирования

@Component
@RequiredArgsConstructor
@Slf4j // Аннотация Lombok для автоматического создания логгера
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
                                        
            final String authHeader = request.getHeader("Authorization");
            log.info("JWT Filter: Request URL: {}", request.getRequestURI());
                                        
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("JWT Filter: No Bearer token found or Authorization header missing. Proceeding without authentication.");
                filterChain.doFilter(request, response);
                return;
            }
        
            final String jwtToken = authHeader.substring(7);
            log.info("JWT Filter: Extracted token: {}", jwtToken);
        
            Optional<String> optionalUserEmail = Optional.empty();
            try {
                String extractedEmail = jwtService.extractEmail(jwtToken);
                optionalUserEmail = Optional.ofNullable(extractedEmail);
                log.info("JWT Filter: Extracted email from token: {}", extractedEmail);
            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                log.warn("JWT Filter: Token expired.");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Token expired\"}");
                return;
            } catch (Exception e) {
                log.error("JWT Filter: Error extracting email from token", e);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Invalid token\"}");
                return;
            }
        
            optionalUserEmail.ifPresent(userEmail -> {
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    userRepository.findByEmail(userEmail).ifPresentOrElse(user -> {
                        if (jwtService.isTokenValid(jwtToken, user.getEmail())) {
                            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                    user, null, user.getAuthorities());
                            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                            log.info("JWT Filter: Authenticated user: {}", user.getEmail());
                        } else {
                            log.warn("JWT Filter: Token is not valid for user: {}", user.getEmail());
                        }
                    }, () -> {
                        log.warn("JWT Filter: No user found with email: {}", userEmail);
                    });
                } else {
                    log.info("JWT Filter: User already authenticated.");
                }
            });
        
            filterChain.doFilter(request, response);
        }
}
