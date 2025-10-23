package by.bsuir.saa.controller;

import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.entity.User;
import by.bsuir.saa.service.EmployeeService;
import by.bsuir.saa.service.UserManagementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
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

        Map<String, Long> userStatsByRole = userManagementService.getUserStatisticsByRole();

        Map<String, String> roleIcons = new HashMap<>();
        roleIcons.put("ADMIN", "bi-shield-check");
        roleIcons.put("HR", "bi-people");
        roleIcons.put("RATESETTER", "bi-gear");
        roleIcons.put("ACCOUNTANT", "bi-calculator");
        roleIcons.put("ANALYST", "bi-graph-up");

        model.addAttribute("title", "Панель администратора");
        model.addAttribute("icon", "bi-shield-check");
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("activeUsers", activeUsers);
        model.addAttribute("inactiveUsers", totalUsers - activeUsers);
        model.addAttribute("totalEmployees", totalEmployees);
        model.addAttribute("activeEmployees", activeEmployees);

        model.addAttribute("userStatsByRole", userStatsByRole);
        model.addAttribute("roleIcons", roleIcons);

        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String usersPage(Model model) {
        List<User> users = userManagementService.getAllUsers();

        long activeCount = users.stream().filter(User::getIsActive).count();
        long inactiveCount = users.size() - activeCount;

        model.addAttribute("title", "Управление пользователями");
        model.addAttribute("icon", "bi-people");
        model.addAttribute("users", users);
        model.addAttribute("activeUsersCount", activeCount);
        model.addAttribute("inactiveUsersCount", inactiveCount);
        return "admin/users";
    }

    @GetMapping("/users/create")
    public String createUserForm(Model model) {
        List<Employee> employees = employeeService.getAllEmployeesWithDetails();

        Map<String, String> roleLabels = new HashMap<>();
        roleLabels.put("ADMIN", "Администратор");
        roleLabels.put("HR", "Специалист по кадрам");
        roleLabels.put("RATESETTER", "Нормировщик труда");
        roleLabels.put("ACCOUNTANT", "Бухгалтер");
        roleLabels.put("ANALYST", "Аналитик");

        model.addAttribute("title", "Создание пользователя");
        model.addAttribute("icon", "bi-person-plus");
        model.addAttribute("roles", List.of("ADMIN", "HR", "RATESETTER", "ACCOUNTANT", "ANALYST"));
        model.addAttribute("roleLabels", roleLabels);
        model.addAttribute("employees", employees);

        return "admin/create-user";
    }

    @PostMapping("/users")
    public String createUser(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam(required = false) Integer employeeId,
                             @RequestParam String role,
                             RedirectAttributes redirectAttributes) {
        try {
            userManagementService.createUser(username, password, employeeId, role);
            redirectAttributes.addFlashAttribute("success", "Пользователь успешно создан");
            return "redirect:/admin/users";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/users/create";
        }
    }

    @GetMapping("/users/{id}/edit")
    public String editUserForm(@PathVariable Integer id, Model model) {
        User user = userManagementService.getAllUsers().stream()
                .filter(u -> u.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        List<Employee> employees = employeeService.getAllEmployeesWithDetails();

        Map<String, String> roleLabels = new HashMap<>();
        roleLabels.put("ADMIN", "Администратор");
        roleLabels.put("HR", "Специалист по кадрам");
        roleLabels.put("RATESETTER", "Нормировщик труда");
        roleLabels.put("ACCOUNTANT", "Бухгалтер");
        roleLabels.put("ANALYST", "Аналитик");

        model.addAttribute("title", "Редактирование пользователя");
        model.addAttribute("icon", "bi-pencil");
        model.addAttribute("user", user);
        model.addAttribute("employees", employees);
        model.addAttribute("roles", List.of("ADMIN", "HR", "RATESETTER", "ACCOUNTANT", "ANALYST"));
        model.addAttribute("roleLabels", roleLabels);

        return "admin/edit-user";
    }

    @PostMapping("/users/{id}")
    public String updateUser(@PathVariable Integer id,
                             @RequestParam String username,
                             @RequestParam String role,
                             @RequestParam Boolean isActive,
                             @RequestParam(required = false) Integer employeeId,
                             RedirectAttributes redirectAttributes) {
        try {
            userManagementService.updateUser(id, username, role, isActive, employeeId);
            redirectAttributes.addFlashAttribute("success", "Пользователь успешно обновлен");
            return "redirect:/admin/users";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return String.format("redirect:/admin/users/%d/edit?error=%s", id, e.getMessage());
        }
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Integer id,
                             RedirectAttributes redirectAttributes) {
        try {
            userManagementService.deleteUser(id);
            redirectAttributes.addFlashAttribute("success", "Пользователь удален");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/activate")
    public String activateUser(@PathVariable Integer id,
                               RedirectAttributes redirectAttributes) {
        try {
            userManagementService.activateUser(id);
            redirectAttributes.addFlashAttribute("success", "Пользователь активирован");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/deactivate")
    public String deactivateUser(@PathVariable Integer id,
                                 RedirectAttributes redirectAttributes) {
        try {
            userManagementService.deactivateUser(id);
            redirectAttributes.addFlashAttribute("success", "Пользователь деактивирован");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/change-password")
    public String changePassword(@PathVariable Integer id,
                                 @RequestParam String newPassword,
                                 RedirectAttributes redirectAttributes) {
        try {
            userManagementService.changePassword(id, newPassword);
            redirectAttributes.addFlashAttribute("success", "Пароль успешно изменен");
            return "redirect:/admin/users";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return String.format("redirect:/admin/users/%d/edit?error=%s", id, e.getMessage());
        }
    }
}