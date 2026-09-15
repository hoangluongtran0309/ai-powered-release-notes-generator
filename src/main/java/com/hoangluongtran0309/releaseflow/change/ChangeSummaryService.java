package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.audience.AudienceBrief;
import com.hoangluongtran0309.releaseflow.audience.AudienceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Records a person's version of a change's neutral summary and narratives. It works
 * whether or not the AI wrote a summary, and the AI never replaces it afterwards.
 * Narratives are kept only for the Organization's current audiences.
 */
@Service
public class ChangeSummaryService {

    private final ChangeRepository changeRepository;
    private final AudienceService audienceService;
    private final OutputLanguageService outputLanguageService;
    private final Clock clock;

    ChangeSummaryService(
            ChangeRepository changeRepository,
            AudienceService audienceService,
            OutputLanguageService outputLanguageService,
            Clock clock
    ) {
        this.changeRepository = changeRepository;
        this.audienceService = audienceService;
        this.outputLanguageService = outputLanguageService;
        this.clock = clock;
    }

    @Transactional
    public ChangeView edit(ReleaseFlowPrincipal editor, UUID projectId, UUID changeId, ChangeSummaryRequest request) {
        UUID organizationId = editor.organizationId();
        Change change = changeRepository.findByIdAndOrganizationIdAndProjectId(changeId, organizationId, projectId)
                .orElseThrow(ChangeNotFoundException::new);
        change.editSummary(
                request.summary(),
                request.narratives(audienceService.briefs(organizationId).stream().map(AudienceBrief::code).toList()),
                outputLanguageService.outputLanguage(organizationId).tag(),
                editor.userId(),
                editor.displayName(),
                clock.instant()
        );
        changeRepository.flush();
        return ChangeView.from(change);
    }
}
