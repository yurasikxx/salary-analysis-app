package by.bsuir.saa.controller;

import by.bsuir.saa.entity.Department;
import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.service.AnalyticsService;
import by.bsuir.saa.service.DepartmentService;
import by.bsuir.saa.service.EmployeeService;
import by.bsuir.saa.service.ReportService;
import by.bsuir.saa.util.MonthUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/analyst")
@PreAuthorize("hasRole('ANALYST')")
@RequiredArgsConstructor
public class AnalystController {

    private final EmployeeService employeeService;
    private final DepartmentService departmentService;
    private final AnalyticsService analyticsService;
    private final ReportService reportService;

    @Data
    public static class SalaryTrendData {
        private String period;
        private BigDecimal averageSalary;
        private BigDecimal totalFOT;
        private int employeeCount;
        private BigDecimal minSalary;
        private BigDecimal maxSalary;
    }

    @Data
    public static class DepartmentStats {
        private String departmentName;
        private BigDecimal averageSalary;
        private BigDecimal totalFOT;
        private long employeeCount;
        private BigDecimal minSalary;
        private BigDecimal maxSalary;
    }

    @Data
    public static class PositionStats {
        private String positionTitle;
        private BigDecimal averageSalary;
        private BigDecimal minSalary;
        private BigDecimal maxSalary;
        private long employeeCount;
        private BigDecimal totalFOT;
    }

    @Data
    public static class SalaryStructure {
        private String category;
        private BigDecimal amount;
        private BigDecimal percentage;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                            Model model) {

        model.addAttribute("title", "Дашборд аналитика");
        model.addAttribute("icon", "bi-bar-chart");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(month));

        AnalyticsService.DashboardStats stats = analyticsService.getDashboardStats(month, year);
        addAvailableYears(model);

        model.addAttribute("totalEmployees", stats.getTotalEmployees());
        model.addAttribute("employeesWithSalary", stats.getEmployeesWithSalary());
        model.addAttribute("totalFOT", stats.getTotalFOT());
        model.addAttribute("averageSalary", stats.getAverageSalary());
        model.addAttribute("departmentStats", stats.getDepartmentStats());
        model.addAttribute("highestDepartmentFOT", stats.getHighestDepartmentFOT());
        model.addAttribute("highestAverageSalary", stats.getHighestAverageSalary());
        model.addAttribute("topSalaries", stats.getTopSalaries());
        model.addAttribute("salaryCalculationRate", stats.getSalaryCalculationRate());

        return "analyst/dashboard";
    }

    @GetMapping("/salary-trends")
    public String salaryTrends(@RequestParam(defaultValue = "6") Integer monthsBack,
                               @RequestParam(required = false) Integer departmentId,
                               Model model) {

        model.addAttribute("title", "Анализ динамики заработной платы");
        model.addAttribute("icon", "bi-graph-up");
        model.addAttribute("monthsBack", monthsBack);
        model.addAttribute("departmentId", departmentId);

        List<SalaryTrendData> trendData = analyticsService.getSalaryTrends(monthsBack, departmentId);
        List<Department> departments = departmentService.getAllDepartments();

        model.addAttribute("trendData", trendData);
        model.addAttribute("departments", departments);

        return "analyst/salary-trends";
    }

    @GetMapping("/department-analysis")
    public String departmentAnalysis(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                                     @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                                     Model model) {

        model.addAttribute("title", "Анализ по подразделениям");
        model.addAttribute("icon", "bi-building");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(month));

        List<DepartmentStats> departmentStats = analyticsService.calculateDepartmentStats(month, year);
        BigDecimal totalCompanyFOT = analyticsService.getTotalCompanyFOT(month, year);

        model.addAttribute("departmentStats", departmentStats);
        model.addAttribute("totalCompanyFOT", totalCompanyFOT);

        return "analyst/department-analysis";
    }

    @GetMapping("/position-analysis")
    public String positionAnalysis(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                                   @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                                   Model model) {

        model.addAttribute("title", "Анализ по должностям");
        model.addAttribute("icon", "bi-person-badge");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(month));

        List<PositionStats> positionStats = analyticsService.getPositionStats(month, year);
        model.addAttribute("positionStats", positionStats);

        return "analyst/position-analysis";
    }

    @GetMapping("/salary-structure")
    public String salaryStructure(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                                  @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                                  @RequestParam(required = false) Integer employeeId,
                                  Model model) {

        model.addAttribute("title", "Анализ структуры заработной платы");
        model.addAttribute("icon", "bi-pie-chart");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("employeeId", employeeId);
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(month));

        List<Employee> employees = employeeService.getActiveEmployees();
        List<SalaryStructure> structureData = analyticsService.getSalaryStructure(month, year, employeeId);

        if (employeeId != null) {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));
            model.addAttribute("selectedEmployee", employee);
        }

        model.addAttribute("employees", employees);
        model.addAttribute("structureData", structureData);

        return "analyst/salary-structure";
    }

    @GetMapping("/reports/export-trends")
    public ResponseEntity<byte[]> exportTrendsReport(@RequestParam(defaultValue = "6") Integer monthsBack,
                                                     @RequestParam(required = false) Integer departmentId) {
        try {
            byte[] excelBytes = reportService.generateSalaryTrendsReport(monthsBack, departmentId);
            String filename = "salary_trends_report_" + monthsBack + "months" +
                    (departmentId != null ? "_department_" + departmentId : "") + ".xlsx";

            return ResponseEntity.ok()
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body(excelBytes);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/reports/export-trends-pdf")
    public ResponseEntity<byte[]> exportTrendsPdfReport(@RequestParam(defaultValue = "6") Integer monthsBack,
                                                        @RequestParam(required = false) Integer departmentId) {
        try {
            byte[] pdfBytes = reportService.generateSalaryTrendsPdf(monthsBack, departmentId);
            String filename = "salary_trends_report_" + monthsBack + "months" +
                    (departmentId != null ? "_department_" + departmentId : "") + ".pdf";

            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body(pdfBytes);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/reports/export-department")
    public ResponseEntity<byte[]> exportDepartmentReport(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year) {
        try {
            byte[] pdfBytes = reportService.generateDepartmentAnalysisReport(month, year);
            String filename = "department_analysis_" + month + "_" + year + ".pdf";

            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body(pdfBytes);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/reports/export-position")
    public ResponseEntity<byte[]> exportPositionReport(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year) {
        try {
            byte[] excelBytes = reportService.generatePositionAnalysisReport(month, year);
            String filename = "position_analysis_" + month + "_" + year + ".xlsx";

            return ResponseEntity.ok()
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body(excelBytes);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/reports/export-position-pdf")
    public ResponseEntity<byte[]> exportPositionPdfReport(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year) {
        try {
            byte[] pdfBytes = reportService.generatePositionAnalysisPdf(month, year);
            String filename = "position_analysis_" + month + "_" + year + ".pdf";

            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body(pdfBytes);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private void addAvailableYears(Model model) {
        int currentYear = LocalDate.now().getYear();
        List<Integer> availableYears = List.of(
                currentYear - 2,
                currentYear - 1,
                currentYear,
                currentYear + 1
        );
        model.addAttribute("availableYears", availableYears);
    }
}