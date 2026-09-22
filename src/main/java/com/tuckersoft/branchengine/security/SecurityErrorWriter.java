package com.tuckersoft.branchengine.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuckersoft.branchengine.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

final class SecurityErrorWriter {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private SecurityErrorWriter() {}

    static void write(HttpServletResponse res, int status, String error, String message, String path) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        MAPPER.writeValue(res.getOutputStream(),
                new ErrorResponse(error, message, Instant.now().toString(), path));
    }
}
