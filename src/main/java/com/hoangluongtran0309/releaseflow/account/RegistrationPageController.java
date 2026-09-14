package com.hoangluongtran0309.releaseflow.account;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
public class RegistrationPageController {

    private final RegistrationService registrationService;
    private final OutputLanguageService outputLanguageService;

    public RegistrationPageController(
            RegistrationService registrationService,
            OutputLanguageService outputLanguageService
    ) {
        this.registrationService = registrationService;
        this.outputLanguageService = outputLanguageService;
    }

    @ModelAttribute("outputLanguageOptions")
    List<OutputLanguageSettings.Option> outputLanguageOptions() {
        return outputLanguageService.suggestions();
    }

    @GetMapping("/register")
    public String registrationForm(Model model) {
        if (!model.containsAttribute("registration")) {
            RegistrationRequest registration = new RegistrationRequest();
            registration.setOutputLanguage(OutputLanguage.DEFAULT.tag());
            model.addAttribute("registration", registration);
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("registration") RegistrationRequest request,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            return "register";
        }

        try {
            registrationService.register(request);
        } catch (DuplicateEmailException exception) {
            bindingResult.rejectValue("email", "email.duplicate", exception.getMessage());
            return "register";
        } catch (InvalidOutputLanguageException exception) {
            bindingResult.rejectValue("outputLanguage", "outputLanguage.invalid", exception.getMessage());
            return "register";
        }
        return "redirect:/login?registered";
    }
}
