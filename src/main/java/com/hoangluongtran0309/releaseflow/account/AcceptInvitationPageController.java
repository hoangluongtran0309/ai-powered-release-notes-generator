package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.configuration.UiMessages;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Public invitation acceptance in three server-rendered steps: open (the token arrives
 * in the URL fragment), review (who is inviting whom), accept (create the account).
 */
@Controller
public class AcceptInvitationPageController {

    private final InvitationService invitationService;
    private final UiMessages messages;

    AcceptInvitationPageController(InvitationService invitationService, UiMessages messages) {
        this.invitationService = invitationService;
        this.messages = messages;
    }

    @GetMapping("/accept-invite")
    String open(Model model, HttpServletResponse response) {
        noStore(response);
        model.addAttribute("tokenRequest", new InvitationTokenRequest());
        return "accept-invite";
    }

    @PostMapping("/accept-invite/review")
    String review(
            @ModelAttribute("tokenRequest") InvitationTokenRequest request,
            Model model,
            HttpServletResponse response
    ) {
        noStore(response);
        AcceptInvitationRequest acceptRequest = new AcceptInvitationRequest();
        acceptRequest.setToken(request.getToken());
        model.addAttribute("acceptRequest", acceptRequest);
        return renderReview(request.getToken(), model, response);
    }

    @PostMapping("/accept-invite")
    String accept(
            @Valid @ModelAttribute("acceptRequest") AcceptInvitationRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        noStore(response);
        if (bindingResult.hasErrors()) {
            return renderReview(request.getToken(), model, response);
        }
        try {
            invitationService.accept(request);
        } catch (InvalidInvitationException exception) {
            return renderInvalid(model, response);
        } catch (InvitationEmailUnavailableException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            model.addAttribute("pageError", messages.of(exception));
            return renderReview(request.getToken(), model, response);
        }
        return "redirect:/login?invited";
    }

    private String renderReview(String token, Model model, HttpServletResponse response) {
        try {
            model.addAttribute("preview", invitationService.inspect(token));
        } catch (InvalidInvitationException exception) {
            return renderInvalid(model, response);
        }
        return "accept-invite";
    }

    private String renderInvalid(Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        model.addAttribute("invalid", messages.of(new InvalidInvitationException()));
        model.addAttribute("tokenRequest", new InvitationTokenRequest());
        return "accept-invite";
    }

    private static void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
    }
}
