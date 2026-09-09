package com.hoangluongtran0309.releaseflow.status;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
final class HomeController {

    @GetMapping("/")
    String home(Authentication authentication, Model model) {
        if (authentication != null && authentication.getPrincipal() instanceof ReleaseFlowPrincipal principal) {
            model.addAttribute("currentUser", principal.displayName());
        }
        return "home";
    }
}
