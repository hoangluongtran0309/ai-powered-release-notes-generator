package com.hoangluongtran0309.releaseflow.status;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
final class HomeController {

    @GetMapping("/")
    String home() {
        return "home";
    }
}
