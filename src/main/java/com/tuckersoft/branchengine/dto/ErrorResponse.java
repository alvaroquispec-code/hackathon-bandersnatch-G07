package com.tuckersoft.branchengine.dto;

public record ErrorResponse(String error, String message, String timestamp, String path) {}
