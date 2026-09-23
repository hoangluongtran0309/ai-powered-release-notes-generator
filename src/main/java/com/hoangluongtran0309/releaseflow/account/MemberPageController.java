package com.hoangluongtran0309.releaseflow.account;

import com.hoangluongtran0309.releaseflow.configuration.LocalizedException;
import com.hoangluongtran0309.releaseflow.configuration.UiMessages;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@Controller
public class MemberPageController {

    private final InvitationService invitationService;
    private final UiMessages messages;

    MemberPageController(InvitationService invitationService, UiMessages messages) {
        this.invitationService = invitationService;
        this.messages = messages;
    }

    @GetMapping("/members")
    String members(@AuthenticationPrincipal ReleaseFlowPrincipal principal, Model model) {
        return renderMembers(principal, model);
    }

    @PostMapping("/members/invitations")
    String invite(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @Valid @ModelAttribute("invitationRequest") InvitationRequest request,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        if (bindingResult.hasErrors()) {
            return renderMembers(principal, model);
        }
        try {
            return renderIssued(invitationService.invite(principal, request), model, response);
        } catch (InvitationEmailUnavailableException | InvitationAlreadyPendingException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            bindingResult.rejectValue("email", "invitation.conflict", messages.of(exception));
            return renderMembers(principal, model);
        }
    }

    @PostMapping("/members/invitations/{invitationId}/reissue")
    String reissue(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID invitationId,
            Model model,
            HttpServletResponse response
    ) {
        try {
            return renderIssued(invitationService.reissue(principal, invitationId), model, response);
        } catch (InvitationNotFoundException exception) {
            return renderMembersWithError(principal, HttpStatus.NOT_FOUND, exception, model, response);
        } catch (InvitationEmailUnavailableException exception) {
            return renderMembersWithError(principal, HttpStatus.CONFLICT, exception, model, response);
        }
    }

    @PostMapping("/members/invitations/{invitationId}/revoke")
    String revoke(
            @AuthenticationPrincipal ReleaseFlowPrincipal principal,
            @PathVariable UUID invitationId,
            Model model,
            HttpServletResponse response
    ) {
        try {
            invitationService.revoke(principal, invitationId);
        } catch (InvitationNotFoundException exception) {
            return renderMembersWithError(principal, HttpStatus.NOT_FOUND, exception, model, response);
        }
        return "redirect:/members";
    }

    // The link is rendered once, never redirected or stored, so it cannot be recovered later.
    private String renderIssued(IssuedInvitation invitation, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", CacheControl.noStore().getHeaderValue());
        model.addAttribute("invitation", invitation);
        model.addAttribute(
                "acceptanceUrl",
                ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString() + invitation.acceptancePath()
        );
        return "invitation-issued";
    }

    private String renderMembers(ReleaseFlowPrincipal principal, Model model) {
        if (!model.containsAttribute("invitationRequest")) {
            model.addAttribute("invitationRequest", new InvitationRequest());
        }
        model.addAttribute("members", invitationService.members(principal));
        model.addAttribute("invitations", invitationService.invitations(principal));
        return "members";
    }

    private String renderMembersWithError(
            ReleaseFlowPrincipal principal,
            HttpStatus status,
            LocalizedException exception,
            Model model,
            HttpServletResponse response
    ) {
        response.setStatus(status.value());
        model.addAttribute("pageError", messages.of(exception));
        return renderMembers(principal, model);
    }
}
