package by.bsuir.saa.controller;

import by.bsuir.saa.service.AnalyticsService;
import by.bsuir.saa.service.DepartmentService;
import by.bsuir.saa.service.EmployeeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/analyst")
@PreAuthorize("hasRole('ANALYST')")
public class AnalystController {

    private final AnalyticsService analyticsService;
    private final DepartmentService departmentService;
    private final EmployeeService employeeService;

    public AnalystController(AnalyticsService analyticsService,
                             DepartmentService departmentService,
                             EmployeeService employeeService) {
        this.analyticsService = analyticsService;
        this.departmentService = departmentService;
        this.employeeService = employeeService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                            Model model) {

        model.addAttribute("title", "Аналитическая панель");
        model.addAttribute("icon", "bi-speedometer2");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departments", departmentService.getAllDepartments());

        Map<String, Object> companyStats = analyticsService.getCompanyStatistics(month, year);
        Map<String, Object> departmentComparison = analyticsService.getDepartmentSalaryComparison(month, year);
        List<Map<String, Object>> topEmployees = analyticsService.getTopEmployeesBySalary(month, year, 5);

        model.addAttribute("companyStats", companyStats);
        model.addAttribute("departmentComparison", departmentComparison);
        model.addAttribute("topEmployees", topEmployees);

        return "analyst/dashboard";
    }

    @GetMapping("/department-comparison")
    public String departmentComparison(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                                       @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                                       Model model) {

        model.addAttribute("title", "Сравнение отделов");
        model.addAttribute("icon", "bi-bar-chart");
        model.addAttribute("month", month);
        model.addAttribute("year", year);

        Map<String, Object> comparisonData = analyticsService.getDepartmentSalaryComparison(month, year);
        model.addAttribute("comparisonData", comparisonData);

        return "analyst/department-comparison";
    }

    @GetMapping("/salary-dynamics")
    public String salaryDynamics(@RequestParam(required = false) Integer departmentId,
                                 @RequestParam(defaultValue = "1") Integer startMonth,
                                 @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer startYear,
                                 @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer endMonth,
                                 @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer endYear,
                                 Model model) {

        model.addAttribute("title", "Динамика заработных плат");
        model.addAttribute("icon", "bi-graph-up");
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("startMonth", startMonth);
        model.addAttribute("startYear", startYear);
        model.addAttribute("endMonth", endMonth);
        model.addAttribute("endYear", endYear);
        model.addAttribute("departments", departmentService.getAllDepartments());

        if (departmentId != null) {
            Map<String, Object> dynamicsData = analyticsService.getSalaryDynamicsByDepartment(
                    departmentId, startMonth, startYear, endMonth, endYear);
            model.addAttribute("dynamicsData", dynamicsData);
        }

        return "analyst/salary-dynamics";
    }

    @GetMapping("/payment-structure")
    public String paymentStructure(@RequestParam(required = false) Integer employeeId,
                                   @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                                   @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                                   Model model) {

        model.addAttribute("title", "Структура выплат");
        model.addAttribute("icon", "bi-pie-chart");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("employeeId", employeeId);
        model.addAttribute("employees", employeeService.getActiveEmployees());

        if (employeeId != null) {
            Map<String, Object> structureData = analyticsService.getPaymentStructureAnalysis(employeeId, month, year);
            model.addAttribute("structureData", structureData);
        }

        return "analyst/payment-structure";
    }

    @GetMapping("/top-employees")
    public String topEmployees(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                               @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                               @RequestParam(defaultValue = "10") Integer limit,
                               Model model) {

        model.addAttribute("title", "Топ сотрудников по зарплате");
        model.addAttribute("icon", "bi-trophy");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("limit", limit);

        List<Map<String, Object>> topEmployees = analyticsService.getTopEmployeesBySalary(month, year, limit);
        model.addAttribute("topEmployees", topEmployees);

        return "analyst/top-employees";
    }

    @GetMapping("/trend-analysis")
    public String trendAnalysis(@RequestParam(required = false) Integer departmentId,
                                @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                                @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                                Model model) {

        model.addAttribute("title", "Анализ трендов");
        model.addAttribute("icon", "bi-arrow-trend-up");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("departments", departmentService.getAllDepartments());

        if (departmentId != null) {
            Map<String, Object> trendData = analyticsService.getSalaryTrendAnalysis(departmentId, month, year);
            model.addAttribute("trendData", trendData);
        }

        Map<String, Object> companyStats = analyticsService.getCompanyStatistics(month, year);
        model.addAttribute("companyStats", companyStats);

        return "analyst/trend-analysis";
    }
}