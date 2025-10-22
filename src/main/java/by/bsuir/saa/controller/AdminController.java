package by.bsuir.saa.controller;

import by.bsuir.saa.entity.User;
import by.bsuir.saa.service.EmployeeService;
import by.bsuir.saa.service.UserManagementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserManagementService userManagementService;
    private final EmployeeService employeeService;

    public AdminController(UserManagementService userManagementService, EmployeeService employeeService) {
        this.userManagementService = userManagementService;
        this.employeeService = employeeService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        long totalUsers = userManagementService.getAllUsers().size();
        long activeUsers = userManagementService.getActiveUsers().size();
        long totalEmployees = employeeService.getAllEmployees().size();
        long activeEmployees = employeeService.getActiveEmployees().size();

        model.addAttribute("title", "Панель администратора");
        model.addAttribute("icon", "bi-shield-check");
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("activeUsers", activeUsers);
        model.addAttribute("inactiveUsers", totalUsers - activeUsers);
        model.addAttribute("totalEmployees", totalEmployees);
        model.addAttribute("activeEmployees", activeEmployees);

        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String usersPage(Model model) {
        List<User> users = userManagementService.getActiveUsers();
        model.addAttribute("title", "Управление пользователями");
        model.addAttribute("icon", "bi-people");
        model.addAttribute("users", users);
        return "admin/users";
    }

    @GetMapping("/users/create")
    public String createUserForm(Model model) {
        model.addAttribute("title", "Создание пользователя");
        model.addAttribute("icon", "bi-person-plus");
        model.addAttribute("roles", List.of("ADMIN", "HR", "RATESETTER", "ACCOUNTANT", "ANALYST"));
        model.addAttribute("employees", employeeService.getAllEmployees());
        return "admin/create-user";
    }

    @PostMapping("/users")
    public String createUser(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam(required = false) Integer employeeId,
                             @RequestParam String role) {
        try {
            userManagementService.createUser(username, password, employeeId, role);
            return "redirect:/admin/users?success=Пользователь успешно создан";
        } catch (Exception e) {
            return "redirect:/admin/users/create?error=" + e.getMessage();
        }
    }

    @GetMapping("/users/{id}/edit")
    public String editUserForm(@PathVariable Integer id, Model model) {
        User user = userManagementService.getAllUsers().stream()
                .filter(u -> u.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        model.addAttribute("title", "Редактирование пользователя");
        model.addAttribute("icon", "bi-pencil");
        model.addAttribute("user", user);
        model.addAttribute("roles", List.of("ADMIN", "HR", "RATESETTER", "ACCOUNTANT", "ANALYST"));
        return "admin/edit-user";
    }

    @PostMapping("/users/{id}")
    public String updateUser(@PathVariable Integer id,
                             @RequestParam String username,
                             @RequestParam String role,
                             @RequestParam Boolean isActive) {
        try {
            userManagementService.updateUser(id, username, role, isActive);
            return "redirect:/admin/users?success=Пользователь успешно обновлен";
        } catch (Exception e) {
            return "redirect:/admin/users/" + id + "/edit?error=" + e.getMessage();
        }
    }

    @PostMapping("/users/{id}/deactivate")
    public String deactivateUser(@PathVariable Integer id) {
        try {
            userManagementService.deactivateUser(id);
            return "redirect:/admin/users?success=Пользователь деактивирован";
        } catch (Exception e) {
            return "redirect:/admin/users?error=" + e.getMessage();
        }
    }

    @PostMapping("/users/{id}/activate")
    public String activateUser(@PathVariable Integer id) {
        try {
            userManagementService.updateUser(id, null, null, true);
            return "redirect:/admin/users?success=Пользователь активирован";
        } catch (Exception e) {
            return "redirect:/admin/users?error=" + e.getMessage();
        }
    }

    @PostMapping("/users/{id}/change-password")
    public String changePassword(@PathVariable Integer id,
                                 @RequestParam String newPassword) {
        try {
            userManagementService.changePassword(id, newPassword);
            return "redirect:/admin/users?success=Пароль успешно изменен";
        } catch (Exception e) {
            return "redirect:/admin/users/" + id + "/edit?error=" + e.getMessage();
        }
    }

    @GetMapping("/system")
    public String systemSettings(Model model) {
        model.addAttribute("title", "Настройки системы");
        model.addAttribute("icon", "bi-gear");
        return "admin/system-settings";
    }

    @GetMapping("/audit")
    public String auditLog(Model model) {
        model.addAttribute("title", "Журнал событий");
        model.addAttribute("icon", "bi-clock-history");
        return "admin/audit-log";
    }

/*    @GetMapping("/api/users/stats")
    @ResponseBody
    public Map<String, Long> getUserStats() {
        return userManagementService.getUserStatisticsByRole();
    }*/
}