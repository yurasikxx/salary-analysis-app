package by.bsuir.saa.controller;

import by.bsuir.saa.entity.User;
import by.bsuir.saa.service.EmployeeService;
import by.bsuir.saa.service.UserManagementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        model.addAttribute("users", users);
        return "admin/users";
    }

    @GetMapping("/users/create")
    public String createUserForm(Model model) {
        model.addAttribute("roles", List.of("ADMIN", "HR", "RATESETTER", "ACCOUNTANT", "ANALYST"));
        model.addAttribute("employees", employeeService.getAllEmployees());
        return "admin/create-user";
    }

    @PostMapping("/users")
    public String createUser(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam(required = false) Integer employeeId,
                             @RequestParam String role) {
        userManagementService.createUser(username, password, employeeId, role);
        return "redirect:/admin/users?success=User created";
    }

    @GetMapping("/users/{id}/edit")
    public String editUserForm(@PathVariable Integer id, Model model) {
        User user = userManagementService.getAllUsers().stream()
                .filter(u -> u.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        model.addAttribute("roles", List.of("ADMIN", "HR", "RATESETTER", "ACCOUNTANT", "ANALYST"));
        return "admin/edit-user";
    }

    @PostMapping("/users/{id}")
    public String updateUser(@PathVariable Integer id,
                             @RequestParam String username,
                             @RequestParam String role,
                             @RequestParam Boolean isActive) {
        userManagementService.updateUser(id, username, role, isActive);
        return "redirect:/admin/users?success=User updated";
    }

    @PostMapping("/users/{id}/deactivate")
    public String deactivateUser(@PathVariable Integer id) {
        userManagementService.deactivateUser(id);
        return "redirect:/admin/users?success=User deactivated";
    }

    @GetMapping("/system")
    public String systemSettings(Model model) {
        // Здесь можно добавить настройки системы
        return "admin/system-settings";
    }

    @GetMapping("/audit")
    public String auditLog(Model model) {
        // Здесь можно добавить журнал событий
        return "admin/audit-log";
    }

    @PostMapping("/users/{id}/activate")
    public String activateUser(@PathVariable Integer id) {
        userManagementService.updateUser(id, null, null, true);
        return "redirect:/admin/users?success=User activated";
    }

    @PostMapping("/users/{id}/change-password")
    public String changePassword(@PathVariable Integer id,
                                 @RequestParam String newPassword) {
        userManagementService.changePassword(id, newPassword);
        return "redirect:/admin/users?success=Password changed";
    }
}