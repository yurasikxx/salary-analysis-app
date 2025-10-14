package by.bsuir.saa.controller;

import by.bsuir.saa.service.AnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasRole('ANALYST')")
public class AnalystController {

    private final AnalyticsService analyticsService;

    public AnalystController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/department-comparison")
    public Map<String, Object> getDepartmentComparison(@RequestParam Integer month,
                                                       @RequestParam Integer year) {
        return analyticsService.getDepartmentSalaryComparison(month, year);
    }

    @GetMapping("/salary-dynamics")
    public Map<String, Object> getSalaryDynamics(@RequestParam Integer departmentId,
                                                 @RequestParam Integer startMonth,
                                                 @RequestParam Integer startYear,
                                                 @RequestParam Integer endMonth,
                                                 @RequestParam Integer endYear) {
        return analyticsService.getSalaryDynamicsByDepartment(departmentId, startMonth, startYear, endMonth, endYear);
    }

    @GetMapping("/payment-structure")
    public Map<String, Object> getPaymentStructure(@RequestParam Integer employeeId,
                                                   @RequestParam Integer month,
                                                   @RequestParam Integer year) {
        return analyticsService.getPaymentStructureAnalysis(employeeId, month, year);
    }

    @GetMapping("/top-employees")
    public Object getTopEmployees(@RequestParam Integer month,
                                  @RequestParam Integer year,
                                  @RequestParam(defaultValue = "5") Integer limit) {
        return analyticsService.getTopEmployeesBySalary(month, year, limit);
    }

    @GetMapping("/trend")
    public Map<String, Object> getSalaryTrend(@RequestParam Integer departmentId,
                                              @RequestParam Integer month,
                                              @RequestParam Integer year) {
        return analyticsService.getSalaryTrendAnalysis(departmentId, month, year);
    }

    @GetMapping("/company-stats")
    public Map<String, Object> getCompanyStatistics(@RequestParam Integer month,
                                                    @RequestParam Integer year) {
        return analyticsService.getCompanyStatistics(month, year);
    }
}