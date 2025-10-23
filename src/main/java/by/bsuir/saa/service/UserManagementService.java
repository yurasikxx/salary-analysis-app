package by.bsuir.saa.service;

import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.entity.User;
import by.bsuir.saa.entity.UserRole;
import by.bsuir.saa.repository.EmployeeRepository;
import by.bsuir.saa.repository.UserRepository;
import by.bsuir.saa.repository.UserRoleRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserManagementService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeService employeeService;

    public UserManagementService(UserRepository userRepository,
                                 UserRoleRepository userRoleRepository,
                                 PasswordEncoder passwordEncoder, EmployeeService employeeService, EmployeeRepository employeeRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.employeeService = employeeService;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> getActiveUsers() {
        return userRepository.findByIsActiveTrue();
    }

    public User createUser(String username, String password, Integer employeeId, String roleName) {
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Пользователь с таким логином уже существует");
        }

        Employee employee = null;
        if (employeeId != null) {
            employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден: " + employeeId));
        }

        UserRole role = userRoleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Роль не найдена: " + roleName));

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmployee(employee);
        user.setRole(role);
        user.setIsActive(true);

        return userRepository.save(user);
    }

    public User updateUser(Integer userId, String username, String roleName, Boolean isActive, Integer employeeId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        if (username != null) {
            if (!user.getUsername().equals(username) && userRepository.existsByUsername(username)) {
                throw new RuntimeException("Пользователь с таким логином уже существует");
            }
            user.setUsername(username);
        }

        if (roleName != null) {
            UserRole role = userRoleRepository.findByName(roleName)
                    .orElseThrow(() -> new RuntimeException("Роль не найдена: " + roleName));
            user.setRole(role);
        }

        if (isActive != null) {
            user.setIsActive(isActive);
        }

        if (employeeId != null) {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден: " + employeeId));
            user.setEmployee(employee);
        } else {
            user.setEmployee(null);
        }

        return userRepository.save(user);
    }

    public void deleteUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName().equals(user.getUsername())) {
            throw new RuntimeException("Нельзя удалить собственный аккаунт");
        }

        userRepository.delete(user);
    }

    public void changePassword(Integer userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void activateUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        user.setIsActive(true);
        userRepository.save(user);
    }

    public void deactivateUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName().equals(user.getUsername())) {
            throw new RuntimeException("Нельзя деактивировать собственный аккаунт");
        }

        user.setIsActive(false);
        userRepository.save(user);
    }

    public Optional<User> findUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Map<String, Long> getUserStatisticsByRole() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .collect(Collectors.groupingBy(
                        user -> user.getRole().getName(),
                        Collectors.counting()
                ));
    }
}