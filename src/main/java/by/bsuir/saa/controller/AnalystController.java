package by.bsuir.saa.controller;

import by.bsuir.saa.service.AnalyticsService;
import by.bsuir.saa.service.DepartmentService;
import by.bsuir.saa.service.EmployeeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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

    // === ВЕБ-СТРАНИЦЫ ===

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "9") Integer month,
                            @RequestParam(defaultValue = "2025") Integer year,
                            Model model) {

        model.addAttribute("title", "Аналитическая панель");
        model.addAttribute("icon", "bi-speedometer2");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departments", departmentService.getAllDepartments());

        var companyStats = analyticsService.getCompanyStatistics(month, year);
        model.addAttribute("companyStats", companyStats);

        var topEmployees = analyticsService.getTopEmployeesBySalary(month, year, 5);
        model.addAttribute("topEmployees", topEmployees);

        var departmentComparison = analyticsService.getDepartmentSalaryComparison(month, year);
        model.addAttribute("departmentComparison", departmentComparison);

        return "analyst/dashboard";
    }

    @GetMapping("/department-comparison")
    public String departmentComparison(@RequestParam(defaultValue = "9") Integer month,
                                       @RequestParam(defaultValue = "2025") Integer year,
                                       Model model) {

        model.addAttribute("title", "Сравнение отделов");
        model.addAttribute("icon", "bi-bar-chart");
        model.addAttribute("month", month);
        model.addAttribute("year", year);

        var comparisonData = analyticsService.getDepartmentSalaryComparison(month, year);
        model.addAttribute("comparisonData", comparisonData);

        return "analyst/department-comparison.html";
    }

    @GetMapping("/salary-dynamics")
    public String salaryDynamics(@RequestParam(required = false) Integer departmentId,
                                 @RequestParam(defaultValue = "1") Integer startMonth,
                                 @RequestParam(defaultValue = "2025") Integer startYear,
                                 @RequestParam(defaultValue = "9") Integer endMonth,
                                 @RequestParam(defaultValue = "2025") Integer endYear,
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
            var dynamicsData = analyticsService.getSalaryDynamicsByDepartment(
                    departmentId, startMonth, startYear, endMonth, endYear);
            model.addAttribute("dynamicsData", dynamicsData);
        }

        return "analyst/salary-dynamics";
    }

    @GetMapping("/payment-structure")
    public String paymentStructure(@RequestParam(required = false) Integer employeeId,
                                   @RequestParam(defaultValue = "9") Integer month,
                                   @RequestParam(defaultValue = "2025") Integer year,
                                   Model model) {

        model.addAttribute("title", "Структура выплат");
        model.addAttribute("icon", "bi-pie-chart");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("employeeId", employeeId);
        model.addAttribute("employees", employeeService.getActiveEmployees());

        if (employeeId != null) {
            var structureData = analyticsService.getPaymentStructureAnalysis(employeeId, month, year);
            model.addAttribute("structureData", structureData);
        }

        return "analyst/payment-structure";
    }

    @GetMapping("/top-employees")
    public String topEmployees(@RequestParam(defaultValue = "9") Integer month,
                               @RequestParam(defaultValue = "2025") Integer year,
                               @RequestParam(defaultValue = "10") Integer limit,
                               Model model) {

        model.addAttribute("title", "Топ сотрудников по зарплате");
        model.addAttribute("icon", "bi-trophy");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("limit", limit);

        var topEmployees = analyticsService.getTopEmployeesBySalary(month, year, limit);
        model.addAttribute("topEmployees", topEmployees);

        return "analyst/top-employees";
    }

    @GetMapping("/trend-analysis")
    public String trendAnalysis(@RequestParam(required = false) Integer departmentId,
                                @RequestParam(defaultValue = "9") Integer month,
                                @RequestParam(defaultValue = "2025") Integer year,
                                Model model) {

        model.addAttribute("title", "Анализ трендов");
        model.addAttribute("icon", "bi-arrow-trend-up");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("departments", departmentService.getAllDepartments());

        if (departmentId != null) {
            var trendData = analyticsService.getSalaryTrendAnalysis(departmentId, month, year);
            model.addAttribute("trendData", trendData);
        }

        var companyStats = analyticsService.getCompanyStatistics(month, year);
        model.addAttribute("companyStats", companyStats);

        return "analyst/trend-analysis";
    }

    // === API ENDPOINTS (для AJAX запросов) ===

    @GetMapping("/api/department-comparison")
    @ResponseBody
    public Map<String, Object> getDepartmentComparisonApi(@RequestParam Integer month,
                                                          @RequestParam Integer year) {
        return analyticsService.getDepartmentSalaryComparison(month, year);
    }

    @GetMapping("/api/salary-dynamics")
    @ResponseBody
    public Map<String, Object> getSalaryDynamicsApi(@RequestParam Integer departmentId,
                                                    @RequestParam Integer startMonth,
                                                    @RequestParam Integer startYear,
                                                    @RequestParam Integer endMonth,
                                                    @RequestParam Integer endYear) {
        return analyticsService.getSalaryDynamicsByDepartment(departmentId, startMonth, startYear, endMonth, endYear);
    }

    @GetMapping("/api/payment-structure")
    @ResponseBody
    public Map<String, Object> getPaymentStructureApi(@RequestParam Integer employeeId,
                                                      @RequestParam Integer month,
                                                      @RequestParam Integer year) {
        return analyticsService.getPaymentStructureAnalysis(employeeId, month, year);
    }

    @GetMapping("/api/top-employees")
    @ResponseBody
    public Object getTopEmployeesApi(@RequestParam Integer month,
                                     @RequestParam Integer year,
                                     @RequestParam(defaultValue = "5") Integer limit) {
        return analyticsService.getTopEmployeesBySalary(month, year, limit);
    }

    @GetMapping("/api/trend")
    @ResponseBody
    public Map<String, Object> getSalaryTrendApi(@RequestParam Integer departmentId,
                                                 @RequestParam Integer month,
                                                 @RequestParam Integer year) {
        return analyticsService.getSalaryTrendAnalysis(departmentId, month, year);
    }

    @GetMapping("/api/company-stats")
    @ResponseBody
    public Map<String, Object> getCompanyStatisticsApi(@RequestParam Integer month,
                                                       @RequestParam Integer year) {
        return analyticsService.getCompanyStatistics(month, year);
    }
}