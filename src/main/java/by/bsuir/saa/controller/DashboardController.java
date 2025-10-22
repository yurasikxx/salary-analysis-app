package by.bsuir.saa.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            switch (role) {
                case "ROLE_ANALYST":
                    return "redirect:/analyst/dashboard";
                case "ROLE_ACCOUNTANT":
                    return "redirect:/accountant/dashboard";
                case "ROLE_HR":
                    return "redirect:/hr/dashboard";
                case "ROLE_RATESETTER":
                    return "redirect:/ratesetter/dashboard";
                case "ROLE_ADMIN":
                    return "redirect:/admin/dashboard";
            }
        }

        return "redirect:/login";
    }
}