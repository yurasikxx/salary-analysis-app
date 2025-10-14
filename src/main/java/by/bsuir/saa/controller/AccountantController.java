package by.bsuir.saa.controller;

import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.service.EmployeeService;
import by.bsuir.saa.service.ReportService;
import by.bsuir.saa.service.SalaryCalculationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/accountant")
@PreAuthorize("hasRole('ACCOUNTANT')")
public class AccountantController {

    private final EmployeeService employeeService;
    private final SalaryCalculationService salaryCalculationService;
    private final ReportService reportService;

    public AccountantController(EmployeeService employeeService,
                                SalaryCalculationService salaryCalculationService,
                                ReportService reportService) {
        this.employeeService = employeeService;
        this.salaryCalculationService = salaryCalculationService;
        this.reportService = reportService;
    }

    @GetMapping("/deductions")
    public String deductionsPage(@RequestParam(defaultValue = "09") Integer month,
                                 @RequestParam(defaultValue = "2025") Integer year,
                                 Model model) {
        List<Employee> employees = employeeService.getAllEmployees();
        model.addAttribute("employees", employees);
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        return "accountant/deductions";
    }

    @PostMapping("/deductions/tax")
    public String calculateTax(@RequestParam Integer employeeId,
                               @RequestParam Integer month,
                               @RequestParam Integer year) {
        Employee employee = employeeService.getEmployeeById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        salaryCalculationService.calculateIncomeTax(employee, month, year);
        return "redirect:/accountant/deductions?month=" + month + "&year=" + year + "&success=Tax calculated";
    }

    @PostMapping("/calculations/final")
    public String calculateFinalSalary(@RequestParam Integer employeeId,
                                       @RequestParam Integer month,
                                       @RequestParam Integer year) {
        Employee employee = employeeService.getEmployeeById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        salaryCalculationService.calculateFinalSalary(employee, month, year);
        return "redirect:/accountant/deductions?month=" + month + "&year=" + year + "&success=Final salary calculated";
    }

    @GetMapping("/reports")
    public String reportsPage(Model model) {
        return "accountant/reports";
    }

    @PostMapping("/reports/generate")
    public String generateReport(@RequestParam String reportType,
                                 @RequestParam Integer month,
                                 @RequestParam Integer year,
                                 @RequestParam(required = false) Integer employeeId,
                                 @RequestParam(required = false) Integer departmentId) {
        try {
            String reportContent;

            switch (reportType) {
                case "salary":
                    if (departmentId == null) {
                        reportContent = reportService.generateSalaryReport(month, year);
                    } else {
                        reportContent = reportService.generateSalaryStatement(departmentId, month, year);
                    }
                    break;

                case "tax":
                    reportContent = reportService.generateTaxReport(month, year);
                    break;

                case "payslip":
                    if (employeeId == null) {
                        throw new RuntimeException("Для расчетного листка необходимо указать сотрудника");
                    }
                    reportContent = reportService.generatePayslip(employeeId, month, year);
                    break;

                default:
                    throw new RuntimeException("Неизвестный тип отчета: " + reportType);
            }

            System.out.println("Сгенерирован отчет:\n" + reportContent);

        } catch (Exception e) {
            return "redirect:/accountant/reports?error=" + e.getMessage();
        }

        return "redirect:/accountant/reports?success=Report generated";
    }
}