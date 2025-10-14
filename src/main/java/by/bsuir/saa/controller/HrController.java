package by.bsuir.saa.controller;

import by.bsuir.saa.entity.Department;
import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.entity.Position;
import by.bsuir.saa.entity.Timesheet;
import by.bsuir.saa.service.DepartmentService;
import by.bsuir.saa.service.EmployeeService;
import by.bsuir.saa.service.PositionService;
import by.bsuir.saa.service.TimesheetService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/hr")
@PreAuthorize("hasRole('HR')")
public class HrController {

    private final EmployeeService employeeService;
    private final TimesheetService timesheetService;
    private final PositionService positionService;
    private final DepartmentService departmentService;

    public HrController(EmployeeService employeeService, TimesheetService timesheetService,
                        PositionService positionService, DepartmentService departmentService) {
        this.employeeService = employeeService;
        this.timesheetService = timesheetService;
        this.positionService = positionService;
        this.departmentService = departmentService;
    }

    @GetMapping("/employees")
    public String employeesPage(Model model) {
        List<Employee> employees = employeeService.getAllEmployees();

        long activeCount = employees.stream()
                .filter(e -> e.getTerminationDate() == null)
                .count();
        long terminatedCount = employees.size() - activeCount;
        long departmentCount = employees.stream()
                .map(Employee::getDepartment)
                .distinct()
                .count();

        model.addAttribute("employees", employees);
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("terminatedCount", terminatedCount);
        model.addAttribute("departmentCount", departmentCount);

        return "hr/employees";
    }

    @GetMapping("/employees/{id}")
    public String employeeProfile(@PathVariable Integer id, Model model) {
        Employee employee = employeeService.getEmployeeById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        model.addAttribute("employee", employee);
        return "hr/employee-profile";
    }

    @GetMapping("/employees/create")
    public String createEmployeeForm(Model model) {
        model.addAttribute("positions", positionService.getAllPositions());
        model.addAttribute("departments", departmentService.getAllDepartments());
        return "hr/create-employee";
    }

    @PostMapping("/employees")
    public String createEmployee(@RequestParam String fullName,
                                 @RequestParam String hireDate,
                                 @RequestParam Integer positionId,
                                 @RequestParam Integer departmentId) {

        Position position = positionService.getPositionById(positionId)
                .orElseThrow(() -> new RuntimeException("Должность не найдена: " + positionId));

        Department department = departmentService.getDepartmentById(departmentId)
                .orElseThrow(() -> new RuntimeException("Отдел не найден: " + departmentId));

        employeeService.createEmployee(fullName, LocalDate.parse(hireDate), position, department);
        return "redirect:/hr/employees?success=Employee created";
    }

    @GetMapping("/timesheets")
    public String timesheetsPage(@RequestParam(defaultValue = "9") Integer month,
                                 @RequestParam(defaultValue = "2025") Integer year,
                                 Model model) {
        model.addAttribute("month", month);
        model.addAttribute("year", year);

        List<Timesheet> timesheets = timesheetService.getTimesheetsByPeriod(month, year);
        model.addAttribute("timesheets", timesheets);

        return "hr/timesheets";
    }

    @PostMapping("/employees/{id}/terminate")
    public String terminateEmployee(@PathVariable Integer id,
                                    @RequestParam String terminationDate) {
        employeeService.terminateEmployee(id, LocalDate.parse(terminationDate));
        return "redirect:/hr/employees?success=Employee terminated successfully";
    }
}