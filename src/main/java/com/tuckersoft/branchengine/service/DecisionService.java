package com.tuckersoft.branchengine.service;

import com.tuckersoft.branchengine.dto.*;
import com.tuckersoft.branchengine.entity.Decision;
import com.tuckersoft.branchengine.entity.Playthrough;
import com.tuckersoft.branchengine.entity.StoryNode;
import com.tuckersoft.branchengine.entity.User;
import com.tuckersoft.branchengine.event.DecisionCommittedEvent;
import com.tuckersoft.branchengine.exception.ApiException;
import com.tuckersoft.branchengine.repository.DecisionRepository;
import com.tuckersoft.branchengine.repository.PlaythroughRepository;
import com.tuckersoft.branchengine.repository.RealityLogRepository;
import com.tuckersoft.branchengine.repository.StoryNodeRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DecisionService {
    public static final String SIMULATE_MAIL_FAILURE = "MAIL_FAILURE";

    private final PlaythroughRepository playthroughRepository;
    private final DecisionRepository decisionRepository;
    private final StoryNodeRepository nodeRepository;
    private final RealityLogRepository realityLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrentUserService currentUserService;

    @Transactional
    public DecisionResponse create(DecisionRequest req, String simulateHeader) {
        // 1. Usuario del token y propiedad (el admin tampoco decide sobre partidas ajenas)
        User me = currentUserService.get();
        Playthrough p = playthroughRepository.findById(req.playthroughId())
                .orElseThrow(() -> ApiException.notFound("Partida no encontrada"));
        if (!p.getUser().getId().equals(me.getId())) throw ApiException.forbidden("La partida no te pertenece");

        // 2. Partida ACTIVA
        if (!"ACTIVA".equals(p.getStatus())) throw ApiException.conflict("La partida ya esta FINALIZADA");

        // 3. Clasificar y derivar
        String branchType = BranchRules.classify(req.rawInput());
        StoryNode source = p.getCurrentNode();
        Instant now = Instant.now();

        Decision d = new Decision();
        d.setPlaythrough(p);
        d.setNode(source);
        d.setRawInput(req.rawInput());
        d.setBranchType(branchType);
        d.setImpactLevel(req.impactLevel());
        d.setHandlerUnit(BranchRules.handlerUnit(branchType));
        d.setOutcomeCode(BranchRules.outcomeCode(branchType));
        d.setCreatedAt(now);
        d.setUpdatedAt(now);

        // 4. Entrada corrupta: se guarda y no produce nada mas
        if ("ENTRADA_CORRUPTA".equals(branchType)) {
            d.setResolvedNodeCode(null);
            d.setStatus("ERROR");
            return DtoMapper.toDecision(decisionRepository.save(d));
        }

        // 5a. Stats
        int[] delta = BranchRules.impactDeltas(req.impactLevel());
        int lucidity = Math.max(0, Math.min(100, p.getLucidity() + delta[0]));
        int control = Math.max(0, Math.min(100, p.getControlLevel() + delta[1]));
        p.setLucidity(lucidity);
        p.setControlLevel(control);

        // 5b. Nodo destino
        boolean glitch = "RUPTURA_CUARTA_PARED".equals(branchType) || "CRITICO".equals(req.impactLevel());
        String targetCode = glitch ? source.getGlitchBranchCode() : source.getPrimaryBranchCode();

        // 5c. Estado de la partida (orden exacto: control, lucidez, destino)
        if (control >= 100) {
            finish(p, "ENDING_PAC_SYMBOL");
        } else if (lucidity <= 0) {
            finish(p, "ENDING_WHITE_BEAR");
        } else {
            Optional<StoryNode> target = targetCode == null ? Optional.empty() : nodeRepository.findByNodeCode(targetCode);
            if (target.isEmpty()) {
                finish(p, "ENDING_NETFLIX_CUT");
            } else {
                p.setStatus("ACTIVA");
                p.setCurrentNode(target.get());
            }
        }
        p.setUpdatedAt(now);

        // 6. Guardar partida  7. Guardar decision REGISTRADA
        playthroughRepository.save(p);
        d.setResolvedNodeCode(targetCode);
        d.setStatus("REGISTRADA");
        d = decisionRepository.save(d);

        // 8. Publicar evento (el listener corre AFTER_COMMIT en otro hilo)
        boolean simulate = simulateHeader != null && SIMULATE_MAIL_FAILURE.equalsIgnoreCase(simulateHeader.trim());
        eventPublisher.publishEvent(new DecisionCommittedEvent(
                d.getId(), p.getUser().getEmail(), p.getUser().getDisplayName(), p.getPlayerTag(),
                branchType, d.getImpactLevel(), d.getHandlerUnit(), d.getOutcomeCode(),
                source.getNodeCode(), targetCode, p.getStatus(), p.getLucidity(), p.getControlLevel(),
                p.getEndingCode(), d.getCreatedAt(), d.getRawInput(), simulate));

        // 9. 201
        return DtoMapper.toDecision(d);
    }

    private static void finish(Playthrough p, String ending) {
        p.setStatus("FINALIZADA");
        p.setEndingCode(ending);
    }

    @Transactional(readOnly = true)
    public PageResponse<DecisionResponse> list(String branchType, String impactLevel, String status,
                                               Long playthroughId, int page, int size) {
        User me = currentUserService.get();
        boolean admin = CurrentUserService.isAdmin(me);
        Specification<Decision> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            Join<Decision, Playthrough> pt = root.join("playthrough");
            if (!admin) ps.add(cb.equal(pt.get("user").get("id"), me.getId()));
            if (branchType != null && !branchType.isBlank()) ps.add(cb.equal(root.get("branchType"), branchType));
            if (impactLevel != null && !impactLevel.isBlank()) ps.add(cb.equal(root.get("impactLevel"), impactLevel));
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), status));
            if (playthroughId != null) ps.add(cb.equal(pt.get("id"), playthroughId));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? 10 : size;
        Page<Decision> result = decisionRepository.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new PageResponse<>(result.getContent().stream().map(DtoMapper::toDecision).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public DecisionResponse get(Long id) {
        return DtoMapper.toDecision(loadReadable(id));
    }

    @Transactional(readOnly = true)
    public List<RealityLogResponse> realityLogs(Long id) {
        Decision d = loadReadable(id);
        return realityLogRepository.findByDecisionIdOrderByCreatedAtAscIdAsc(d.getId())
                .stream().map(DtoMapper::toLog).toList();
    }

    private Decision loadReadable(Long id) {
        User me = currentUserService.get();
        Decision d = decisionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Decision no encontrada"));
        if (!CurrentUserService.isAdmin(me) && !d.getPlaythrough().getUser().getId().equals(me.getId())) {
            throw ApiException.forbidden("La decision no te pertenece");
        }
        return d;
    }
}
