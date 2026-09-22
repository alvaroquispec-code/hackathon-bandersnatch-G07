package com.tuckersoft.branchengine.service;

import com.tuckersoft.branchengine.dto.*;
import com.tuckersoft.branchengine.entity.Decision;
import com.tuckersoft.branchengine.entity.Playthrough;
import com.tuckersoft.branchengine.entity.StoryNode;
import com.tuckersoft.branchengine.entity.User;
import com.tuckersoft.branchengine.exception.ApiException;
import com.tuckersoft.branchengine.repository.DecisionRepository;
import com.tuckersoft.branchengine.repository.PlaythroughRepository;
import com.tuckersoft.branchengine.repository.StoryNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlaythroughService {
    private final PlaythroughRepository playthroughRepository;
    private final StoryNodeRepository nodeRepository;
    private final DecisionRepository decisionRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public PlaythroughResponse create(PlaythroughRequest req) {
        User me = currentUserService.get();
        StoryNode node = nodeRepository.findByNodeCode(req.startNodeCode().trim())
                .orElseThrow(() -> ApiException.notFound("Nodo de inicio no encontrado"));
        String tag = req.playerTag().trim();
        if (playthroughRepository.existsByPlayerTag(tag)) throw ApiException.conflict("playerTag ya existe");
        if (node.getCurrentBranches() >= node.getBranchCapacity()) throw ApiException.badRequest("El nodo esta lleno");

        Instant now = Instant.now();
        Playthrough p = new Playthrough();
        p.setPlayerTag(tag);
        p.setUser(me);
        p.setCurrentNode(node);
        p.setStartNodeCode(node.getNodeCode());
        p.setLucidity(100);
        p.setControlLevel(0);
        p.setStatus("ACTIVA");
        p.setEndingCode(null);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);

        node.setCurrentBranches(node.getCurrentBranches() + 1);
        nodeRepository.save(node);
        return DtoMapper.toPlaythrough(playthroughRepository.save(p));
    }

    @Transactional(readOnly = true)
    public List<PlaythroughResponse> list() {
        User me = currentUserService.get();
        List<Playthrough> list = CurrentUserService.isAdmin(me)
                ? playthroughRepository.findAllByOrderByCreatedAtDescIdDesc()
                : playthroughRepository.findByUserIdOrderByCreatedAtDescIdDesc(me.getId());
        return list.stream().map(DtoMapper::toPlaythrough).toList();
    }

    @Transactional(readOnly = true)
    public PlaythroughResponse get(Long id) {
        return DtoMapper.toPlaythrough(loadReadable(id));
    }

    @Transactional(readOnly = true)
    public PathResponse path(Long id) {
        Playthrough p = loadReadable(id);
        List<Decision> decisions = decisionRepository
                .findByPlaythroughIdAndResolvedNodeCodeIsNotNullOrderByCreatedAtAscIdAsc(p.getId());
        List<PathStep> steps = new ArrayList<>();
        int order = 1;
        for (Decision d : decisions) {
            steps.add(new PathStep(order++, d.getId(),
                    d.getNode() != null ? d.getNode().getNodeCode() : null,
                    d.getResolvedNodeCode(), d.getBranchType(), d.getImpactLevel(), d.getCreatedAt()));
        }
        return new PathResponse(p.getId(), p.getPlayerTag(), p.getStatus(), p.getEndingCode(),
                p.getStartNodeCode(), p.getCurrentNode().getNodeCode(), steps);
    }

    /** Lectura: dueno o admin (supervisor). */
    private Playthrough loadReadable(Long id) {
        User me = currentUserService.get();
        Playthrough p = playthroughRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Partida no encontrada"));
        if (!CurrentUserService.isAdmin(me) && !p.getUser().getId().equals(me.getId())) {
            throw ApiException.forbidden("La partida no te pertenece");
        }
        return p;
    }
}
