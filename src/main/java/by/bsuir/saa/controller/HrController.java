package by.bsuir.saa.controller;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.service.*;
import by.bsuir.saa.util.MonthUtil;
import org.hibernate.Hibernate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                            Model model) {

        model.addAttribute("title", "Дашборд кадровой службы");
        model.addAttribute("icon", "bi-person-badge");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(month));

        long totalEmployees = employeeService.getActiveEmployees().size();
        long totalDepartments = departmentService.getAllDepartments().size();
        long timesheetsConfirmed = timesheetService.getConfirmedTimesheetsCount(month, year);
        long timesheetsPending = timesheetService.getPendingTimesheetsCount(month, year);
        long newEmployees = employeeService.getNewEmployeesCount(LocalDate.now().minusDays(30));

        model.addAttribute("totalEmployees", totalEmployees);
        model.addAttribute("totalDepartments", totalDepartments);
        model.addAttribute("timesheetsConfirmed", timesheetsConfirmed);
        model.addAttribute("timesheetsPending", timesheetsPending);
        model.addAttribute("newEmployees", newEmployees);
        model.addAttribute("timesheetCompletionRate", totalEmployees > 0 ?
                (timesheetsConfirmed * 100 / totalEmployees) : 0);

        return "hr/dashboard";
    }

    @GetMapping("/employees")
    public String employeesPage(Model model) {
        model.addAttribute("title", "Управление сотрудниками");
        model.addAttribute("icon", "bi-people");

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
        model.addAttribute("title", "Профиль сотрудника");
        model.addAttribute("icon", "bi-person-badge");

        Employee employee = employeeService.getEmployeeById(id)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));
        model.addAttribute("employee", employee);

        LocalDate endDate = employee.getTerminationDate() != null ?
                employee.getTerminationDate() : LocalDate.now();

        long years = java.time.temporal.ChronoUnit.YEARS.between(employee.getHireDate(), endDate);
        long months = java.time.temporal.ChronoUnit.MONTHS.between(
                employee.getHireDate().plusYears(years), endDate);
        long days = java.time.temporal.ChronoUnit.DAYS.between(
                employee.getHireDate().plusYears(years).plusMonths(months), endDate);

        String workExperience = years + " лет " + months + " месяцев " + days + " дней";
        model.addAttribute("workExperience", workExperience);

        return "hr/employee-profile";
    }

    @GetMapping("/employees/create")
    public String createEmployeeForm(Model model) {
        model.addAttribute("title", "Добавить сотрудника");
        model.addAttribute("icon", "bi-person-plus");

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
                                 @RequestParam Integer departmentId,
                                 RedirectAttributes redirectAttributes) {

        try {
            Position position = positionService.getPositionById(positionId)
                    .orElseThrow(() -> new RuntimeException("Должность не найдена"));

            Department department = departmentService.getDepartmentById(departmentId)
                    .orElseThrow(() -> new RuntimeException("Отдел не найден"));

            employeeService.createEmployee(fullName, LocalDate.parse(hireDate), position, department);

            redirectAttributes.addFlashAttribute("success", "Сотрудник " + fullName + " успешно создан");
            return "redirect:/hr/employees";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/hr/employees/create";
        }
    }

    @PostMapping("/employees/{id}/terminate")
    public String terminateEmployee(@PathVariable Integer id,
                                    @RequestParam String terminationDate,
                                    RedirectAttributes redirectAttributes) {
        try {
            employeeService.terminateEmployee(id, LocalDate.parse(terminationDate));
            redirectAttributes.addFlashAttribute("success", "Сотрудник успешно уволен");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/hr/employees";
    }

    @GetMapping("/timesheets")
    public String timesheetsPage(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                                 @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                                 @RequestParam(required = false) Integer departmentId,
                                 Model model) {

        model.addAttribute("title", "Табели учета рабочего времени");
        model.addAttribute("icon", "bi-calendar-check");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departmentId", departmentId);

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
        model.addAttribute("departments", departments);
        model.addAttribute("confirmedCount", confirmedCount);
        model.addAttribute("draftCount", draftCount);
        model.addAttribute("averageHours", String.format("%.1f", averageHours));
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(month));

        return "hr/timesheets";
    }

    @GetMapping("/timesheets/create")
    public String createTimesheetForm(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                                      @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                                      @RequestParam(required = false) Integer employeeId,
                                      Model model) {

        model.addAttribute("title", "Создать табель");
        model.addAttribute("icon", "bi-calendar-plus");
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(month));

        List<Employee> activeEmployees = employeeService.getActiveEmployees();
        List<Department> departments = departmentService.getAllDepartments();

        model.addAttribute("employees", activeEmployees);
        model.addAttribute("departments", departments);
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("selectedEmployeeId", employeeId);

        return "hr/create-timesheet";
    }

    @PostMapping("/timesheets")
    public String createTimesheet(@RequestParam Integer employeeId,
                                  @RequestParam Integer month,
                                  @RequestParam Integer year,
                                  RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            if (timesheetService.getTimesheet(employee, month, year).isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Табель для сотрудника уже существует за указанный период");
                return "redirect:/hr/timesheets/create?month=%d&year=%d".formatted(month, year);
            }

            Timesheet timesheet = timesheetService.getOrCreateTimesheet(employee, month, year);

            redirectAttributes.addFlashAttribute("success", "Табель успешно создан");
            return "redirect:/hr/timesheets/%d/edit".formatted(timesheet.getId());

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/hr/timesheets/create?month=%d&year=%d".formatted(month, year);
        }
    }

    @GetMapping("/timesheets/{id}/edit")
    public String editTimesheetForm(@PathVariable Integer id, Model model) {
        Timesheet timesheet = timesheetService.getTimesheetById(id)
                .orElseThrow(() -> new RuntimeException("Табель не найден"));

        Hibernate.initialize(timesheet.getEmployee());
        Hibernate.initialize(timesheet.getEmployee().getDepartment());
        Hibernate.initialize(timesheet.getEmployee().getPosition());

        model.addAttribute("title", "Редактирование табеля - " + timesheet.getEmployee().getFullName());
        model.addAttribute("icon", "bi-pencil");
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(timesheet.getMonth()));
        model.addAttribute("timesheet", timesheet);

        List<LocalDate> monthDays = getDaysInMonth(timesheet.getYear(), timesheet.getMonth());
        model.addAttribute("monthDays", monthDays);

        Map<LocalDate, TimesheetEntry> entriesMap = timesheetService.getTimesheetEntriesMap(timesheet);
        model.addAttribute("entriesMap", entriesMap);

        System.out.println("=== ДЕБАГ ИНФОРМАЦИЯ ===");
        System.out.println("Табель ID: " + timesheet.getId());
        System.out.println("Сотрудник: " + timesheet.getEmployee().getFullName());
        System.out.println("Записей в entriesMap: " + entriesMap.size());

        for (LocalDate day : monthDays) {
            TimesheetEntry entry = entriesMap.get(day);
            if (entry != null) {
                System.out.println("День " + day + ": " +
                        entry.getMarkType().getCode() + " - " +
                        entry.getHoursWorked() + "ч.");
            }
        }

        // Типы отметок
        List<MarkType> markTypes = markTypeService.getAllMarkTypes();
        model.addAttribute("markTypes", markTypes);

        return "hr/edit-timesheet";
    }

    @PostMapping("/timesheets/{id}/entries")
    public String saveTimesheetEntries(@PathVariable Integer id,
                                       @RequestParam Map<String, String> allParams,
                                       RedirectAttributes redirectAttributes) {
        try {
            Timesheet timesheet = timesheetService.getTimesheetById(id)
                    .orElseThrow(() -> new RuntimeException("Табель не найден"));

            Map<String, String> dayEntries = new HashMap<>();

            for (String key : allParams.keySet()) {
                if (key.startsWith("markType_")) {
                    String dateStr = key.substring(9);
                    String markTypeCode = allParams.get(key);
                    String hoursKey = "hours_" + dateStr;
                    String hoursValue = allParams.get(hoursKey);

                    if (markTypeCode != null && !markTypeCode.isEmpty() &&
                            hoursValue != null && !hoursValue.isEmpty()) {
                        dayEntries.put("day_" + dateStr, markTypeCode + "_" + hoursValue);
                    }
                }
            }

            System.out.println("Сохранение " + dayEntries.size() + " записей");
            timesheetService.saveTimesheetEntries(id, dayEntries);

            redirectAttributes.addFlashAttribute("success", "Табель успешно сохранен");
            return "redirect:/hr/timesheets/" + id + "/edit";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/hr/timesheets/" + id + "/edit";
        }
    }

    @PostMapping("/timesheets/{id}/confirm")
    public String confirmTimesheet(@PathVariable Integer id,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            User currentUser = userManagementService.findUserByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

            timesheetService.confirmTimesheet(id, currentUser);

            redirectAttributes.addFlashAttribute("success", "Табель подтвержден");
            return "redirect:/hr/timesheets/%d/edit".formatted(id);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/hr/timesheets/%d/edit".formatted(id);
        }
    }

    private List<LocalDate> getDaysInMonth(int year, int month) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        LocalDate currentDay = firstDay;
        while (!currentDay.isAfter(lastDay)) {
            days.add(currentDay);
            currentDay = currentDay.plusDays(1);
        }

        return days;
    }

    @PostMapping("/timesheets/batch-create")
    public String batchCreateTimesheets(@RequestParam Integer month,
                                        @RequestParam Integer year,
                                        RedirectAttributes redirectAttributes) {
        try {
            List<Employee> activeEmployees = employeeService.getActiveEmployees();
            int createdCount = 0;

            for (Employee employee : activeEmployees) {
                if (timesheetService.getTimesheet(employee, month, year).isEmpty()) {
                    timesheetService.getOrCreateTimesheet(employee, month, year);
                    createdCount++;
                }
            }

            redirectAttributes.addFlashAttribute("success",
                    "Создано " + createdCount + " табелей за " + MonthUtil.getRussianMonthName(month) + " " + year);
            return "redirect:/hr/timesheets?month=%d&year=%d".formatted(month, year);

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/hr/timesheets?month=%d&year=%d".formatted(month, year);
        }
    }
}