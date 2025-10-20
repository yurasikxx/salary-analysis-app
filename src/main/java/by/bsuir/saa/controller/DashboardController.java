package by.bsuir.saa.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority.getAuthority().equals("ROLE_ANALYST")) {
                return "redirect:/analyst/dashboard";
            } else if (authority.getAuthority().equals("ROLE_ACCOUNTANT")) {
                return "redirect:/accountant/dashboard";
            } else if (authority.getAuthority().equals("ROLE_HR")) {
                return "redirect:/hr/dashboard"; // нужно создать
            } else if (authority.getAuthority().equals("ROLE_RATESETTER")) {
                return "redirect:/ratesetter/dashboard"; // нужно создать
            } else if (authority.getAuthority().equals("ROLE_ADMIN")) {
                return "redirect:/admin/dashboard"; // нужно создать
            }
        }

        return "redirect:/login";
    }
}