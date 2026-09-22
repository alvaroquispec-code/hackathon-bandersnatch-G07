package com.tuckersoft.branchengine.service;

import com.tuckersoft.branchengine.dto.DtoMapper;
import com.tuckersoft.branchengine.dto.NodeRequest;
import com.tuckersoft.branchengine.dto.NodeResponse;
import com.tuckersoft.branchengine.entity.StoryNode;
import com.tuckersoft.branchengine.exception.ApiException;
import com.tuckersoft.branchengine.repository.StoryNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NodeService {
    private final StoryNodeRepository nodeRepository;

    @Transactional
    public NodeResponse create(NodeRequest req) {
        String code = req.nodeCode().trim();
        if (nodeRepository.existsByNodeCode(code)) throw ApiException.conflict("nodeCode ya existe");
        StoryNode n = new StoryNode();
        n.setNodeCode(code);
        n.setTitle(req.title());
        n.setSceneText(req.sceneText());
        n.setBranchCapacity(req.branchCapacity());
        n.setCurrentBranches(0);
        n.setPrimaryBranchCode(blankToNull(req.primaryBranchCode()));
        n.setGlitchBranchCode(blankToNull(req.glitchBranchCode()));
        n.setCreatedAt(Instant.now());
        return DtoMapper.toNode(nodeRepository.save(n));
    }

    @Transactional(readOnly = true)
    public List<NodeResponse> list() {
        return nodeRepository.findAllByOrderByIdAsc().stream().map(DtoMapper::toNode).toList();
    }

    @Transactional(readOnly = true)
    public NodeResponse get(Long id) {
        return nodeRepository.findById(id).map(DtoMapper::toNode)
                .orElseThrow(() -> ApiException.notFound("Nodo no encontrado"));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
