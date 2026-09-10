package com.hoangluongtran0309.releaseflow.status;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
final class HomeController {

    @GetMapping("/")
    String home(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof ReleaseFlowPrincipal) {
            return "overview";
        }
        return "home";
    }
}
