package com.tuckersoft.branchengine.controller;

import com.tuckersoft.branchengine.dto.PathResponse;
import com.tuckersoft.branchengine.dto.PlaythroughRequest;
import com.tuckersoft.branchengine.dto.PlaythroughResponse;
import com.tuckersoft.branchengine.service.PlaythroughService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/playthroughs")
@RequiredArgsConstructor
public class PlaythroughController {
    private final PlaythroughService playthroughService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaythroughResponse create(@Valid @RequestBody PlaythroughRequest req) {
        return playthroughService.create(req);
    }

    @GetMapping
    public List<PlaythroughResponse> list() {
        return playthroughService.list();
    }

    @GetMapping("/{id}")
    public PlaythroughResponse get(@PathVariable Long id) {
        return playthroughService.get(id);
    }

    @GetMapping("/{id}/path")
    public PathResponse path(@PathVariable Long id) {
        return playthroughService.path(id);
    }
}
