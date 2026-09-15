package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.project.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * The patterns administrators add to the baseline for one Project. They apply to changes
 * classified afterwards; changes already classified keep their review triggers.
 */
@Service
public class ProjectSensitivePathService {

    private final ProjectService projectService;
    private final ProjectSensitivePathPolicyRepository policyRepository;
    private final SensitivePathRules rules;
    private final Clock clock;

    ProjectSensitivePathService(
            ProjectService projectService,
            ProjectSensitivePathPolicyRepository policyRepository,
            SensitivePathRules rules,
            Clock clock
    ) {
        this.projectService = projectService;
        this.policyRepository = policyRepository;
        this.rules = rules;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SensitivePathsView view(UUID organizationId, UUID projectId) {
        projectService.get(organizationId, projectId);
        return view(projectId, policyRepository.findByProjectIdAndOrganizationId(projectId, organizationId).orElse(null));
    }

    @Transactional
    public SensitivePathsView replace(ReleaseFlowPrincipal administrator, UUID projectId, SensitivePathsRequest request) {
        UUID organizationId = administrator.organizationId();
        projectService.get(organizationId, projectId);
        List<String> additions = SensitivePathAdditions.normalize(request.getAdditions());
        ProjectSensitivePathPolicy policy = policyRepository.findByProjectIdAndOrganizationId(projectId, organizationId)
                .orElseGet(() -> new ProjectSensitivePathPolicy(organizationId, projectId));
        policy.replace(additions, administrator.userId(), administrator.displayName(), clock.instant());
        return view(projectId, policyRepository.saveAndFlush(policy));
    }

    /** The rules a Project's changes are checked against: the baseline plus its additions. */
    @Transactional(readOnly = true)
    SensitivePaths forProject(UUID organizationId, UUID projectId) {
        return rules.forProject(policyRepository.findByProjectIdAndOrganizationId(projectId, organizationId)
                .map(ProjectSensitivePathPolicy::getAdditionalGlobs)
                .orElse(List.of()));
    }

    private SensitivePathsView view(UUID projectId, ProjectSensitivePathPolicy policy) {
        List<String> additions = policy == null ? List.of() : policy.getAdditionalGlobs();
        return new SensitivePathsView(
                projectId,
                rules.baseline(),
                additions,
                rules.effective(additions),
                policy == null ? null : policy.getUpdaterName(),
                policy == null ? null : policy.getUpdatedAt()
        );
    }
}
