package com.tuckersoft.branchengine.dto;

import com.tuckersoft.branchengine.entity.*;

public final class DtoMapper {
    private DtoMapper() {}

    public static UserResponse toUser(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.getCreatedAt());
    }

    public static NodeResponse toNode(StoryNode n) {
        return new NodeResponse(n.getId(), n.getNodeCode(), n.getTitle(), n.getSceneText(),
                n.getBranchCapacity(), n.getCurrentBranches(), n.getPrimaryBranchCode(),
                n.getGlitchBranchCode(), n.getCreatedAt());
    }

    public static PlaythroughResponse toPlaythrough(Playthrough p) {
        return new PlaythroughResponse(p.getId(), p.getPlayerTag(), p.getUser().getEmail(), p.getStartNodeCode(),
                p.getCurrentNode().getNodeCode(), p.getLucidity(), p.getControlLevel(), p.getStatus(),
                p.getEndingCode(), p.getCreatedAt(), p.getUpdatedAt());
    }

    public static DecisionResponse toDecision(Decision d) {
        Playthrough p = d.getPlaythrough();
        return new DecisionResponse(d.getId(), p.getId(), p.getPlayerTag(),
                d.getNode() != null ? d.getNode().getNodeCode() : null,
                d.getResolvedNodeCode(), d.getRawInput(), d.getBranchType(), d.getImpactLevel(),
                d.getHandlerUnit(), d.getOutcomeCode(), d.getStatus(), p.getStatus(),
                p.getLucidity(), p.getControlLevel(), p.getEndingCode(), d.getCreatedAt(), d.getUpdatedAt());
    }

    public static RealityLogResponse toLog(RealityLog l) {
        return new RealityLogResponse(l.getId(), l.getDecision().getId(), l.getRecipientEmail(), l.getSubject(),
                l.getLogStatus(), l.getErrorMessage(), l.getSentAt(), l.getCreatedAt());
    }
}
