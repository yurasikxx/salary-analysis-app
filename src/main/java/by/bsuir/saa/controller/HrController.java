package by.bsuir.saa.controller;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.service.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/hr")
@PreAuthorize("hasRole('HR')")
public class HrController {

    private final EmployeeService employeeService;
    private final TimesheetService timesheetService;
    private final PositionService positionService;
    private final DepartmentService departmentService;
    private final UserManagementService userManagementService;
    private final MarkTypeService markTypeService;

    public HrController(EmployeeService employeeService,
                        TimesheetService timesheetService,
                        PositionService positionService,
                        DepartmentService departmentService,
                        UserManagementService userManagementService,
                        MarkTypeService markTypeService) {
        this.employeeService = employeeService;
        this.timesheetService = timesheetService;
        this.positionService = positionService;
        this.departmentService = departmentService;
        this.userManagementService = userManagementService;
        this.markTypeService = markTypeService;
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
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));
        model.addAttribute("employee", employee);
        return "hr/employee-profile";
    }

    @GetMapping("/employees/create")
    public String createEmployeeForm(Model model) {
        List<Position> positions = positionService.getAllPositions();
        List<Department> departments = departmentService.getAllDepartments();

        model.addAttribute("positions", positions);
        model.addAttribute("departments", departments);
        return "hr/create-employee";
    }

    @PostMapping("/employees")
    public String createEmployee(@RequestParam String fullName,
                                 @RequestParam String hireDate,
                                 @RequestParam Integer positionId,
                                 @RequestParam Integer departmentId) {

        try {
            Position position = positionService.getPositionById(positionId)
                    .orElseThrow(() -> new RuntimeException("Должность не найдена"));

            Department department = departmentService.getDepartmentById(departmentId)
                    .orElseThrow(() -> new RuntimeException("Отдел не найден"));

            employeeService.createEmployee(fullName, LocalDate.parse(hireDate), position, department);

            return "redirect:/hr/employees?success=Сотрудник " + fullName + " успешно создан";

        } catch (Exception e) {
            return "redirect:/hr/employees/create?error=" + e.getMessage();
        }
    }

    @PostMapping("/employees/{id}/terminate")
    public String terminateEmployee(@PathVariable Integer id,
                                    @RequestParam String terminationDate) {
        try {
            employeeService.terminateEmployee(id, LocalDate.parse(terminationDate));
            return "redirect:/hr/employees?success=Сотрудник успешно уволен";
        } catch (Exception e) {
            return "redirect:/hr/employees?error=" + e.getMessage();
        }
    }

    @GetMapping("/timesheets")
    public String timesheetsPage(@RequestParam(defaultValue = "9") Integer month,
                                 @RequestParam(defaultValue = "2025") Integer year,
                                 @RequestParam(required = false) Integer departmentId,
                                 Model model) {

        List<Timesheet> timesheets = timesheetService.getTimesheetsByPeriod(month, year);

        if (departmentId != null) {
            timesheets = timesheets.stream()
                    .filter(t -> t.getEmployee().getDepartment().getId().equals(departmentId))
                    .collect(Collectors.toList());
        }

        long confirmedCount = timesheets.stream()
                .filter(t -> t.getStatus() == Timesheet.TimesheetStatus.CONFIRMED)
                .count();
        long draftCount = timesheets.size() - confirmedCount;

        double averageHours = timesheets.stream()
                .mapToDouble(t -> t.getTotalHours().doubleValue())
                .average()
                .orElse(0.0);

        List<Department> departments = departmentService.getAllDepartments();

        model.addAttribute("timesheets", timesheets);
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("departments", departments);
        model.addAttribute("confirmedCount", confirmedCount);
        model.addAttribute("draftCount", draftCount);
        model.addAttribute("averageHours", String.format("%.1f", averageHours));

        return "hr/timesheets";
    }

    @GetMapping("/timesheets/create")
    public String createTimesheetForm(@RequestParam(defaultValue = "9") Integer month,
                                      @RequestParam(defaultValue = "2025") Integer year,
                                      @RequestParam(required = false) Integer employeeId,
                                      Model model) {

        List<Employee> activeEmployees = employeeService.getAllEmployees().stream()
                .filter(e -> e.getTerminationDate() == null)
                .collect(Collectors.toList());

        List<MarkType> markTypes = markTypeService.getAllMarkTypes();

        model.addAttribute("employees", activeEmployees);
        model.addAttribute("markTypes", markTypes);
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("selectedEmployeeId", employeeId);

        return "hr/create-timesheet";
    }

    @PostMapping("/timesheets")
    public String createTimesheet(@RequestParam Integer employeeId,
                                  @RequestParam Integer month,
                                  @RequestParam Integer year) {
        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            if (timesheetService.getTimesheet(employee, month, year).isPresent()) {
                return "redirect:/hr/timesheets/create?month=" + month + "&year=" + year +
                        "&error=Табель для сотрудника уже существует за указанный период";
            }

            Timesheet timesheet = timesheetService.getOrCreateTimesheet(employee, month, year);

            return "redirect:/hr/timesheets/" + timesheet.getId() + "/edit?success=Табель создан";

        } catch (Exception e) {
            return "redirect:/hr/timesheets/create?month=" + month + "&year=" + year +
                    "&error=" + e.getMessage();
        }
    }

    @GetMapping("/timesheets/{id}/edit")
    public String editTimesheetForm(@PathVariable Integer id, Model model) {
        Timesheet timesheet = timesheetService.getTimesheetById(id)
                .orElseThrow(() -> new RuntimeException("Табель не найден"));

        List<TimesheetEntry> entries = timesheetService.getTimesheetEntries(timesheet);
        List<MarkType> markTypes = markTypeService.getAllMarkTypes();

        model.addAttribute("timesheet", timesheet);
        model.addAttribute("entries", entries);
        model.addAttribute("markTypes", markTypes);

        return "hr/edit-timesheet";
    }

    @PostMapping("/timesheets/{id}/entries")
    public String addTimesheetEntry(@PathVariable Integer id,
                                    @RequestParam String date,
                                    @RequestParam Integer markTypeId,
                                    @RequestParam BigDecimal hours) {
        try {
            Timesheet timesheet = timesheetService.getTimesheetById(id)
                    .orElseThrow(() -> new RuntimeException("Табель не найден"));

            MarkType markType = markTypeService.getMarkTypeById(markTypeId)
                    .orElseThrow(() -> new RuntimeException("Тип отметки не найден"));

            timesheetService.addTimesheetEntry(timesheet, LocalDate.parse(date), markType, hours);

            return "redirect:/hr/timesheets/" + id + "/edit?success=Запись добавлена";

        } catch (Exception e) {
            return "redirect:/hr/timesheets/" + id + "/edit?error=" + e.getMessage();
        }
    }

    @PostMapping("/timesheets/{id}/confirm")
    public String confirmTimesheet(@PathVariable Integer id, Authentication authentication) {
        try {
            User currentUser = userManagementService.findUserByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

            timesheetService.confirmTimesheet(id, currentUser);

            return "redirect:/hr/timesheets?success=Табель подтвержден";

        } catch (Exception e) {
            return "redirect:/hr/timesheets?error=" + e.getMessage();
        }
    }

    @PostMapping("/timesheets/{id}/delete")
    public String deleteTimesheet(@PathVariable Integer id) {
        try {
            Timesheet timesheet = timesheetService.getTimesheetById(id)
                    .orElseThrow(() -> new RuntimeException("Табель не найден"));

            timesheetService.deleteTimesheetEntries(timesheet);
            timesheetService.deleteTimesheet(id);

            return "redirect:/hr/timesheets?success=Табель удален";

        } catch (Exception e) {
            return "redirect:/hr/timesheets?error=" + e.getMessage();
        }
    }

    @GetMapping("/employees/{id}/timesheets")
    public String employeeTimesheets(@PathVariable Integer id,
                                     @RequestParam(defaultValue = "9") Integer month,
                                     @RequestParam(defaultValue = "2025") Integer year,
                                     Model model) {
        Employee employee = employeeService.getEmployeeById(id)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        List<Timesheet> timesheets = timesheetService.getTimesheetsByEmployee(employee);

        model.addAttribute("employee", employee);
        model.addAttribute("timesheets", timesheets);
        model.addAttribute("month", month);
        model.addAttribute("year", year);

        return "hr/employee-timesheets";
    }

    @GetMapping("/statistics")
    public String statisticsPage(Model model) {
        List<Employee> employees = employeeService.getAllEmployees();
        List<Timesheet> timesheets = timesheetService.getAllTimesheets();

        long totalEmployees = employees.size();
        long activeEmployees = employees.stream()
                .filter(e -> e.getTerminationDate() == null)
                .count();

        long totalTimesheets = timesheets.size();
        long confirmedTimesheets = timesheets.stream()
                .filter(t -> t.getStatus() == Timesheet.TimesheetStatus.CONFIRMED)
                .count();

        double avgHours = timesheets.stream()
                .mapToDouble(t -> t.getTotalHours().doubleValue())
                .average()
                .orElse(0.0);

        model.addAttribute("totalEmployees", totalEmployees);
        model.addAttribute("activeEmployees", activeEmployees);
        model.addAttribute("totalTimesheets", totalTimesheets);
        model.addAttribute("confirmedTimesheets", confirmedTimesheets);
        model.addAttribute("avgHours", String.format("%.1f", avgHours));

        return "hr/statistics";
    }
}