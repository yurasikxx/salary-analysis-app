package by.bsuir.saa.controller;

import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error,
                        @RequestParam(value = "logout", required = false) String logout,
                        Model model,
                        HttpServletRequest request) {

        if (error != null) {
            model.addAttribute("error", getErrorMessage(request));
        }

        if (logout != null) {
            model.addAttribute("success", "Вы успешно вышли из системы");
        }

        return "auth/login";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "auth/access-denied";
    }

    private String getErrorMessage(HttpServletRequest request) {
        Exception exception = (Exception)
                request.getSession().getAttribute("SPRING_SECURITY_LAST_EXCEPTION");

        if (exception != null) {
            Throwable rootCause = getRootCause(exception);

            if (rootCause instanceof DisabledException) {
                return "Учетная запись деактивирована. Обратитесь к администратору.";
            } else {
                return "Неверное имя пользователя или пароль";
            }
        }

        return "Неверное имя пользователя или пароль";
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }
}