package com.tuckersoft.branchengine.service;

import com.tuckersoft.branchengine.dto.AuthResponse;
import com.tuckersoft.branchengine.dto.LoginRequest;
import com.tuckersoft.branchengine.dto.RegisterRequest;
import com.tuckersoft.branchengine.entity.User;
import com.tuckersoft.branchengine.exception.ApiException;
import com.tuckersoft.branchengine.repository.UserRepository;
import com.tuckersoft.branchengine.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim();
        if (userRepository.existsByEmail(email)) throw ApiException.conflict("El email ya esta registrado");
        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(req.password()));
        u.setDisplayName(req.displayName().trim());
        u.setRole("ROLE_USER");
        u.setCreatedAt(Instant.now());
        u = userRepository.save(u);
        return toAuth(u);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        if (req == null || req.email() == null || req.password() == null) {
            throw ApiException.unauthorized("Credenciales incorrectas");
        }
        User u = userRepository.findByEmail(req.email().trim())
                .filter(x -> passwordEncoder.matches(req.password(), x.getPassword()))
                .orElseThrow(() -> ApiException.unauthorized("Credenciales incorrectas"));
        return toAuth(u);
    }

    private AuthResponse toAuth(User u) {
        return new AuthResponse(jwtService.generate(u.getEmail()), "Bearer", u.getEmail(), u.getDisplayName(), u.getRole());
    }
}
