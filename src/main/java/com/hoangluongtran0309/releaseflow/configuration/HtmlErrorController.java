package com.hoangluongtran0309.releaseflow.configuration;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * The page somebody lands on when a request fails outside a controller that has its own
 * answer: a wrong address, a page their role does not reach, or a fault. REST clients
 * still get {@code application/problem+json} from {@link ApiExceptionHandler} and the
 * security chain, which both run first.
 *
 * <p>Only three statuses have wording of their own; anything else is shown as a fault,
 * because a page that guessed would be telling somebody something it does not know.
 */
@Controller
class HtmlErrorController implements ErrorController {

    private final ErrorAttributes errorAttributes;

    HtmlErrorController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @RequestMapping("/error")
    String error(HttpServletRequest request, Model model) {
        HttpStatus status = status(request);
        model.addAttribute("status", status.value());
        model.addAttribute("statusKey", switch (status) {
            case NOT_FOUND -> "notFound";
            case FORBIDDEN -> "forbidden";
            default -> "fault";
        });
        // The path is what the person typed or followed; never the exception behind it.
        model.addAttribute("path", errorAttributes
                .getErrorAttributes(new ServletWebRequest(request), ErrorAttributeOptions.defaults())
                .get("path"));
        return "error";
    }

    private static HttpStatus status(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code instanceof Integer value) {
            HttpStatus resolved = HttpStatus.resolve(value);
            if (resolved != null) {
                return resolved;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
