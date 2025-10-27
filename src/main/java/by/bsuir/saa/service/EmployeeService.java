package by.bsuir.saa.service;

import by.bsuir.saa.entity.Department;
import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.entity.Position;
import by.bsuir.saa.entity.Timesheet;
import by.bsuir.saa.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final TimesheetService timesheetService;

    public EmployeeService(EmployeeRepository employeeRepository,
                           TimesheetService timesheetService) {
        this.employeeRepository = employeeRepository;
        this.timesheetService = timesheetService;
    }

    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    public Optional<Employee> getEmployeeById(Integer id) {
        return employeeRepository.findById(id);
    }

    public List<Employee> getActiveEmployees() {
        return employeeRepository.findByTerminationDateIsNull();
    }

    public List<Employee> getActiveEmployeesByDepartment(Integer departmentId) {
        return employeeRepository.findByDepartmentIdAndTerminationDateIsNull(departmentId);
    }

    public List<Employee> getEmployeesByDepartment(Department department) {
        return employeeRepository.findByDepartment(department);
    }

    public List<Employee> searchEmployeesByName(String name) {
        return employeeRepository.findByFullNameContainingIgnoreCase(name);
    }

    public List<Employee> findByPosition(Position position) {
        return employeeRepository.findByPosition(position);
    }

    public List<Employee> getAllEmployeesWithDetails() {
        return employeeRepository.findAllWithDetails();
    }

    public List<Employee> getActiveEmployeesWithDetails() {
        return employeeRepository.findActiveEmployeesWithDetails();
    }

    public long getActiveEmployeeCount() {
        return employeeRepository.countByTerminationDateIsNull();
    }

    public long getEmployeeCountByDepartment(Department department) {
        return employeeRepository.countByDepartment(department);
    }

    public Long getNewEmployeesCount(LocalDate sinceDate) {
        return employeeRepository.countByHireDateAfter(sinceDate);
    }

    public Long getNewEmployeesCount(LocalDate startDate, LocalDate endDate) {
        return employeeRepository.countByHireDateBetweenAndTerminationDateIsNull(startDate, endDate);
    }

    @Transactional
    public void createEmployee(String fullName, LocalDate hireDate,
                               Position position, Department department) {
        validateFullName(fullName);

        String normalizedFullName = fullName.trim().toLowerCase().replaceAll("\\s+", " ");

        List<Employee> allEmployees = employeeRepository.findAll();

        boolean exists = allEmployees.stream()
                .anyMatch(e -> {
                    String existingName = e.getFullName().trim().toLowerCase().replaceAll("\\s+", " ");
                    return existingName.equals(normalizedFullName);
                });

        if (exists) {
            throw new RuntimeException("Сотрудник с ФИО '" + fullName + "' уже существует в системе");
        }

        Employee employee = new Employee();
        employee.setFullName(fullName.trim());
        employee.setHireDate(hireDate);
        employee.setPosition(position);
        employee.setDepartment(department);

        employeeRepository.save(employee);
    }

    @Transactional
    public Employee updateEmployee(Integer id, String fullName, Position position, Department department) {
        validateFullName(fullName);

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        if (employee.getTerminationDate() != null) {
            throw new RuntimeException("Невозможно редактировать уволенного сотрудника");
        }

        String normalizedFullName = fullName.trim().toLowerCase().replaceAll("\\s+", " ");
        String currentNormalizedName = employee.getFullName().trim().toLowerCase().replaceAll("\\s+", " ");

        if (!currentNormalizedName.equals(normalizedFullName)) {
            List<Employee> allEmployees = employeeRepository.findAll();

            boolean duplicateExists = allEmployees.stream()
                    .filter(e -> !e.getId().equals(id))
                    .anyMatch(e -> {
                        String existingName = e.getFullName().trim().toLowerCase().replaceAll("\\s+", " ");
                        return existingName.equals(normalizedFullName);
                    });

            if (duplicateExists) {
                throw new RuntimeException("Сотрудник с ФИО '" + fullName + "' уже существует в системе");
            }
        }

        employee.setFullName(fullName.trim());
        employee.setPosition(position);
        employee.setDepartment(department);

        return employeeRepository.save(employee);
    }

    @Transactional
    public void terminateEmployee(Integer id, LocalDate terminationDate) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        if (employee.getTerminationDate() != null) {
            throw new RuntimeException("Сотрудник уже уволен");
        }

        if (terminationDate.isBefore(employee.getHireDate())) {
            throw new RuntimeException("Дата увольнения не может быть раньше даты приема");
        }

        if (terminationDate.isAfter(LocalDate.now())) {
            throw new RuntimeException("Дата увольнения не может быть в будущем");
        }

        employee.setTerminationDate(terminationDate);
        employeeRepository.save(employee);
    }

    @Transactional
    public void restoreEmployee(Integer id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        if (employee.getTerminationDate() == null) {
            throw new RuntimeException("Сотрудник уже активен");
        }

        String normalizedFullName = employee.getFullName().trim().toLowerCase().replaceAll("\\s+", " ");

        List<Employee> allEmployees = employeeRepository.findAll();

        boolean duplicateExists = allEmployees.stream()
                .filter(e -> !e.getId().equals(id))
                .anyMatch(e -> {
                    String existingName = e.getFullName().trim().toLowerCase().replaceAll("\\s+", " ");
                    return existingName.equals(normalizedFullName);
                });

        if (duplicateExists) {
            throw new RuntimeException("Нельзя восстановить сотрудника. Сотрудник с ФИО '" +
                    employee.getFullName() + "' уже существует в системе");
        }

        employee.setTerminationDate(null);
        employeeRepository.save(employee);
    }

    @Transactional
    public void deleteEmployee(Integer id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        if (employee.getTerminationDate() == null) {
            throw new RuntimeException("Нельзя удалить активного сотрудника. Сначала увольте сотрудника.");
        }

        List<Timesheet> employeeTimesheets = timesheetService.getTimesheetsByEmployee(employee);
        if (!employeeTimesheets.isEmpty()) {
            throw new RuntimeException("Нельзя удалить сотрудника. Существуют связанные табели учета времени.");
        }

        employeeRepository.delete(employee);
    }

    private void validateFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new RuntimeException("ФИО не может быть пустым");
        }

        String normalizedName = fullName.trim().replaceAll("\\s+", " ");
        String[] words = normalizedName.split(" ");

        if (words.length != 3) {
            throw new RuntimeException("ФИО должно содержать ровно три слова: Фамилию, Имя и Отчество. Указано: " + words.length);
        }

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) {
                throw new RuntimeException("ФИО содержит пустые слова");
            }

            if (!Character.isUpperCase(word.charAt(0))) {
                String wordType = i == 0 ? "Фамилия" : (i == 1 ? "Имя" : "Отчество");
                throw new RuntimeException(wordType + " должно начинаться с заглавной буквы: " + word);
            }

            for (int j = 1; j < word.length(); j++) {
                char c = word.charAt(j);
                if (c != '-' && Character.isUpperCase(c)) {
                    throw new RuntimeException("В ФИО не должно быть заглавных букв в середине слова: " + word);
                }
            }
        }

        String nameRegex = "^[А-ЯЁ][а-яё-]*\\s[А-ЯЁ][а-яё-]*\\s[А-ЯЁ][а-яё-]*$";
        if (!normalizedName.matches(nameRegex)) {
            throw new RuntimeException("ФИО должно содержать только кириллические символы и соответствовать формату: Фамилия Имя Отчество");
        }

        if (words[0].length() < 2) throw new RuntimeException("Фамилия слишком короткая");
        if (words[1].length() < 2) throw new RuntimeException("Имя слишком короткое");
        if (words[2].length() < 4) throw new RuntimeException("Отчество слишком короткое");
    }
}