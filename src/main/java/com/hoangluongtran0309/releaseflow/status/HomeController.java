package com.hoangluongtran0309.releaseflow.status;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import com.hoangluongtran0309.releaseflow.overview.OverviewService;
import com.hoangluongtran0309.releaseflow.overview.OverviewView;
import com.hoangluongtran0309.releaseflow.overview.SelectedProject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
final class HomeController {

    private final OverviewService overviewService;
    private final SelectedProject selectedProject;

    HomeController(OverviewService overviewService, SelectedProject selectedProject) {
        this.overviewService = overviewService;
        this.selectedProject = selectedProject;
    }

    @GetMapping("/")
    String home(
            Authentication authentication,
            @RequestParam(name = "project", required = false) UUID projectId,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ReleaseFlowPrincipal principal)) {
            return "home";
        }
        UUID requested = projectId != null ? projectId : selectedProject.remembered(request).orElse(null);
        OverviewView overview = overviewService.overview(principal.organizationId(), requested);
        selectedProject.remember(request, response, overview.projectId());
        model.addAttribute("overview", overview);
        return "overview";
    }
}
