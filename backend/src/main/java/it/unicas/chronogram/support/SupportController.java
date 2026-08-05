package it.unicas.chronogram.support;

import it.unicas.chronogram.common.ApiResponse;
import it.unicas.chronogram.security.AuthPrincipal;
import it.unicas.chronogram.support.dto.SupportMessageRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JWT-protected support form. Authentication is what makes the message
 * attributable - and what keeps the endpoint from becoming an open relay for
 * anonymous mail through our SMTP credentials.
 */
@RestController
@RequestMapping("/api/support")
public class SupportController {

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @PostMapping("/messages")
    public ApiResponse<Void> send(@AuthenticationPrincipal AuthPrincipal principal,
                                  @Valid @RequestBody SupportMessageRequest request) {
        supportService.submit(principal.userId(), request);
        return ApiResponse.ok("Your message has been sent to our support team.");
    }
}
