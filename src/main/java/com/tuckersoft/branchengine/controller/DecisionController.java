package com.tuckersoft.branchengine.controller;

import com.tuckersoft.branchengine.dto.DecisionRequest;
import com.tuckersoft.branchengine.dto.DecisionResponse;
import com.tuckersoft.branchengine.dto.PageResponse;
import com.tuckersoft.branchengine.dto.RealityLogResponse;
import com.tuckersoft.branchengine.service.DecisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/decisions")
@RequiredArgsConstructor
public class DecisionController {
    private final DecisionService decisionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DecisionResponse create(@Valid @RequestBody DecisionRequest req,
                                   @RequestHeader(value = "X-Bandersnatch-Simulate", required = false) String simulate) {
        return decisionService.create(req, simulate);
    }

    @GetMapping
    public PageResponse<DecisionResponse> list(@RequestParam(required = false) String branchType,
                                               @RequestParam(required = false) String impactLevel,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) Long playthroughId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return decisionService.list(branchType, impactLevel, status, playthroughId, page, size);
    }

    @GetMapping("/{id}")
    public DecisionResponse get(@PathVariable Long id) {
        return decisionService.get(id);
    }

    @GetMapping("/{id}/reality-logs")
    public List<RealityLogResponse> realityLogs(@PathVariable Long id) {
        return decisionService.realityLogs(id);
    }
}
