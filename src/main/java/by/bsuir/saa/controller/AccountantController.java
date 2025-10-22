package by.bsuir.saa.controller;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.service.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/accountant")
@PreAuthorize("hasRole('ACCOUNTANT')")
public class AccountantController {

    private final EmployeeService employeeService;
    private final PaymentService paymentService;
    private final PaymentTypeService paymentTypeService;
    private final DepartmentService departmentService;
    private final ReportService reportService;

    public AccountantController(EmployeeService employeeService,
                                PaymentService paymentService,
                                PaymentTypeService paymentTypeService,
                                DepartmentService departmentService,
                                ReportService reportService) {
        this.employeeService = employeeService;
        this.paymentService = paymentService;
        this.paymentTypeService = paymentTypeService;
        this.departmentService = departmentService;
        this.reportService = reportService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                            Model model) {

        model.addAttribute("title", "Главная панель бухгалтера");
        model.addAttribute("icon", "bi-speedometer2");
        model.addAttribute("month", month);
        model.addAttribute("year", year);

        List<Payment> payments = paymentService.getPaymentsByPeriod(month, year);
        List<Employee> employees = employeeService.getActiveEmployees();

        BigDecimal totalAccruals = paymentService.getTotalAccruals(month, year);
        BigDecimal totalDeductions = paymentService.getTotalDeductions(month, year);
        BigDecimal netTotal = totalAccruals.subtract(totalDeductions);

        long employeesWithCalculations = payments.stream()
                .map(Payment::getEmployee)
                .distinct()
                .count();

        model.addAttribute("totalAccruals", totalAccruals);
        model.addAttribute("totalDeductions", totalDeductions);
        model.addAttribute("netTotal", netTotal);
        model.addAttribute("totalEmployees", employees.size());
        model.addAttribute("employeesWithCalculations", employeesWithCalculations);

        return "accountant/dashboard";
    }

    @GetMapping("/deductions")
    public String deductionsPage(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                                 @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                                 @RequestParam(required = false) Integer departmentId,
                                 Model model) {

        model.addAttribute("title", "Управление удержаниями");
        model.addAttribute("icon", "bi-calculator");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departmentId", departmentId);

        List<Employee> employees = employeeService.getActiveEmployees();
        if (departmentId != null) {
            employees = employees.stream()
                    .filter(e -> e.getDepartment().getId().equals(departmentId))
                    .collect(Collectors.toList());
        }

        List<Department> departments = departmentService.getAllDepartments();
        List<PaymentType> deductionTypes = paymentTypeService.getDeductionTypes();

        model.addAttribute("employees", employees);
        model.addAttribute("departments", departments);
        model.addAttribute("deductionTypes", deductionTypes);

        return "accountant/deductions";
    }

    @GetMapping("/payments")
    public String paymentsPage(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                               @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                               @RequestParam(required = false) Integer departmentId,
                               Model model) {

        model.addAttribute("title", "Все платежи");
        model.addAttribute("icon", "bi-cash-stack");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departmentId", departmentId);

        List<Payment> payments = paymentService.getPaymentsByPeriod(month, year);
        if (departmentId != null) {
            payments = payments.stream()
                    .filter(p -> p.getEmployee().getDepartment().getId().equals(departmentId))
                    .collect(Collectors.toList());
        }

        BigDecimal totalAccruals = payments.stream()
                .filter(p -> p.getPaymentType().getCategory().equals("accrual"))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDeductions = payments.stream()
                .filter(p -> p.getPaymentType().getCategory().equals("deduction"))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .abs();

        List<Department> departments = departmentService.getAllDepartments();

        model.addAttribute("payments", payments);
        model.addAttribute("departments", departments);
        model.addAttribute("totalAccruals", totalAccruals);
        model.addAttribute("totalDeductions", totalDeductions);
        model.addAttribute("netTotal", totalAccruals.subtract(totalDeductions));

        return "accountant/payments";
    }

    @GetMapping("/reports")
    public String reportsPage(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                              @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                              Model model) {

        model.addAttribute("title", "Генерация отчетов");
        model.addAttribute("icon", "bi-file-text");
        model.addAttribute("month", month);
        model.addAttribute("year", year);

        List<Employee> employees = employeeService.getActiveEmployees();
        List<Department> departments = departmentService.getAllDepartments();

        model.addAttribute("employees", employees);
        model.addAttribute("departments", departments);

        return "accountant/reports";
    }

    @PostMapping("/deductions/tax")
    public String calculateTax(@RequestParam Integer employeeId,
                               @RequestParam Integer month,
                               @RequestParam Integer year) {
        try {
            return "redirect:/accountant/deductions?month=" + month + "&year=" + year + "&success=Налог рассчитан";
        } catch (Exception e) {
            return "redirect:/accountant/deductions?month=" + month + "&year=" + year + "&error=" + e.getMessage();
        }
    }

    @PostMapping("/deductions/custom")
    public String addCustomDeduction(@RequestParam Integer employeeId,
                                     @RequestParam Integer month,
                                     @RequestParam Integer year,
                                     @RequestParam Integer paymentTypeId,
                                     @RequestParam BigDecimal amount,
                                     @RequestParam String description) {
        try {
            return "redirect:/accountant/deductions?month=" + month + "&year=" + year + "&success=Удержание добавлено";
        } catch (Exception e) {
            return "redirect:/accountant/deductions?month=" + month + "&year=" + year + "&error=" + e.getMessage();
        }
    }

    @PostMapping("/payments/{id}/delete")
    public String deletePayment(@PathVariable Integer id,
                                @RequestParam Integer month,
                                @RequestParam Integer year) {
        try {
            paymentService.deletePayment(id);
            return "redirect:/accountant/payments?month=" + month + "&year=" + year + "&success=Платеж удален";
        } catch (Exception e) {
            return "redirect:/accountant/payments?month=" + month + "&year=" + year + "&error=" + e.getMessage();
        }
    }

    @GetMapping("/reports/salary/pdf")
    public ResponseEntity<byte[]> downloadSalaryReportPdf(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        try {
            byte[] pdfContent = reportService.generateSalaryReportPdf(month, year);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"salary-report-" + month + "-" + year + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfContent);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Ошибка генерации PDF: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/reports/salary/excel")
    public ResponseEntity<byte[]> downloadSalaryReportExcel(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        try {
            byte[] excelContent = reportService.generateSalaryReportExcel(month, year);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"salary-report-" + month + "-" + year + ".xlsx\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelContent);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Ошибка генерации Excel: " + e.getMessage()).getBytes());
        }
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

                case "deductions":
                    reportContent = reportService.generateDeductionsReport(month, year);
                    break;

                default:
                    throw new RuntimeException("Неизвестный тип отчета: " + reportType);
            }

            return "redirect:/accountant/reports?month=" + month + "&year=" + year + "&success=Отчет сгенерирован";

        } catch (Exception e) {
            return "redirect:/accountant/reports?month=" + month + "&year=" + year + "&error=" + e.getMessage();
        }
    }

    @GetMapping("/reports/deductions/pdf")
    public ResponseEntity<byte[]> downloadDeductionsReportPdf(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        try {
            byte[] pdfContent = reportService.generateDeductionsReportPdf(month, year);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"deductions-report-" + month + "-" + year + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfContent);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Ошибка генерации PDF: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/reports/deductions/excel")
    public ResponseEntity<byte[]> downloadDeductionsReportExcel(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        try {
            byte[] excelContent = reportService.generateDeductionsReportExcel(month, year);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"deductions-report-" + month + "-" + year + ".xlsx\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelContent);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Ошибка генерации Excel: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/reports/tax/pdf")
    public ResponseEntity<byte[]> downloadTaxReportPdf(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        try {
            byte[] pdfContent = reportService.generateTaxReportPdf(month, year);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"tax-report-" + month + "-" + year + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfContent);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Ошибка генерации PDF: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/reports/tax/excel")
    public ResponseEntity<byte[]> downloadTaxReportExcel(
            @RequestParam Integer month,
            @RequestParam Integer year) {
        try {
            byte[] excelContent = reportService.generateTaxReportExcel(month, year);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"tax-report-" + month + "-" + year + ".xlsx\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(excelContent);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Ошибка генерации Excel: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/reports/payslip/pdf")
    public ResponseEntity<byte[]> downloadPayslipPdf(
            @RequestParam Integer employeeId,
            @RequestParam Integer month,
            @RequestParam Integer year) {
        try {
            byte[] pdfContent = reportService.generatePayslipPdf(employeeId, month, year);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"payslip-" + employeeId + "-" + month + "-" + year + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfContent);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Ошибка генерации PDF: " + e.getMessage()).getBytes());
        }
    }
}