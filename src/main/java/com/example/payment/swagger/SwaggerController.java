package com.example.payment.swagger;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Redirects the root URL to Swagger UI.
 * This mirrors the pattern used in the coordinator project.
 */
@Controller
public class SwaggerController {

    @RequestMapping("/")
    public String getRedirectUrl() {
        return "redirect:swagger-ui.html";
    }
}