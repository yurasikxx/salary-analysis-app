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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/accountant")
@PreAuthorize("hasRole('ACCOUNTANT')")
public class AccountantController {

    private final EmployeeService employeeService;
    private final SalaryCalculationService salaryCalculationService;
    private final ReportService reportService;
    private final PaymentService paymentService;
    private final PaymentTypeService paymentTypeService;
    private final DepartmentService departmentService;

    public AccountantController(EmployeeService employeeService,
                                SalaryCalculationService salaryCalculationService,
                                ReportService reportService,
                                PaymentService paymentService,
                                PaymentTypeService paymentTypeService,
                                DepartmentService departmentService) {
        this.employeeService = employeeService;
        this.salaryCalculationService = salaryCalculationService;
        this.reportService = reportService;
        this.paymentService = paymentService;
        this.paymentTypeService = paymentTypeService;
        this.departmentService = departmentService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "9") Integer month,
                            @RequestParam(defaultValue = "2025") Integer year,
                            Model model) {

        List<Payment> payments = paymentService.getPaymentsByPeriod(month, year);
        List<Employee> employees = employeeService.getActiveEmployees();

        // Статистика
        BigDecimal totalAccruals = paymentService.getTotalAccruals(month, year);
        BigDecimal totalDeductions = paymentService.getTotalDeductions(month, year);
        BigDecimal netTotal = totalAccruals.subtract(totalDeductions);

        long employeesWithCalculations = payments.stream()
                .map(Payment::getEmployee)
                .distinct()
                .count();

        long employeesWithDeductions = payments.stream()
                .filter(p -> p.getPaymentType().getCategory().equals("deduction"))
                .map(Payment::getEmployee)
                .distinct()
                .count();

        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("totalAccruals", totalAccruals);
        model.addAttribute("totalDeductions", totalDeductions);
        model.addAttribute("netTotal", netTotal);
        model.addAttribute("totalEmployees", employees.size());
        model.addAttribute("employeesWithCalculations", employeesWithCalculations);
        model.addAttribute("employeesWithDeductions", employeesWithDeductions);

        return "accountant/dashboard";
    }

    @GetMapping("/deductions")
    public String deductionsPage(@RequestParam(defaultValue = "9") Integer month,
                                 @RequestParam(defaultValue = "2025") Integer year,
                                 @RequestParam(required = false) Integer departmentId,
                                 Model model) {

        List<Employee> employees = employeeService.getActiveEmployees();

        if (departmentId != null) {
            employees = employees.stream()
                    .filter(e -> e.getDepartment().getId().equals(departmentId))
                    .collect(Collectors.toList());
        }

        // Получаем платежи за период для отображения статуса
        Map<Integer, List<Payment>> employeePayments = paymentService.getPaymentsGroupedByEmployee(month, year);

        // Создаем дополнительные структуры для упрощения шаблона
        Map<Integer, Boolean> employeeHasTax = new HashMap<>();
        Map<Integer, BigDecimal> employeeAccruals = new HashMap<>();
        int employeesWithTaxes = 0;

        for (Employee employee : employees) {
            List<Payment> payments = employeePayments.get(employee.getId());
            if (payments != null) {
                // Проверяем наличие налога
                boolean hasTax = payments.stream()
                        .anyMatch(p -> "INCOME_TAX".equals(p.getPaymentType().getCode()));
                employeeHasTax.put(employee.getId(), hasTax);

                if (hasTax) {
                    employeesWithTaxes++;
                }

                // Считаем начисления
                BigDecimal accruals = payments.stream()
                        .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                        .map(Payment::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                employeeAccruals.put(employee.getId(), accruals);
            } else {
                employeeHasTax.put(employee.getId(), false);
                employeeAccruals.put(employee.getId(), BigDecimal.ZERO);
            }
        }

        List<Department> departments = departmentService.getAllDepartments();
        List<PaymentType> deductionTypes = paymentTypeService.getDeductionTypes();

        model.addAttribute("employees", employees);
        model.addAttribute("employeePayments", employeePayments);
        model.addAttribute("employeeHasTax", employeeHasTax);
        model.addAttribute("employeeAccruals", employeeAccruals);
        model.addAttribute("employeesWithTaxes", employeesWithTaxes);
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("departments", departments);
        model.addAttribute("deductionTypes", deductionTypes);

        return "accountant/deductions";
    }

    @PostMapping("/deductions/tax")
    public String calculateTax(@RequestParam Integer employeeId,
                               @RequestParam Integer month,
                               @RequestParam Integer year) {
        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            salaryCalculationService.calculateIncomeTax(employee, month, year);
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
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            PaymentType paymentType = paymentTypeService.getPaymentTypeById(paymentTypeId)
                    .orElseThrow(() -> new RuntimeException("Тип удержания не найден"));

            if (!"deduction".equals(paymentType.getCategory())) {
                throw new RuntimeException("Можно добавлять только удержания");
            }

            paymentService.createPayment(employee, month, year, paymentType, amount.negate(), description);
            return "redirect:/accountant/deductions?month=" + month + "&year=" + year + "&success=Удержание добавлено";

        } catch (Exception e) {
            return "redirect:/accountant/deductions?month=" + month + "&year=" + year + "&error=" + e.getMessage();
        }
    }

    @PostMapping("/calculations/final")
    public String calculateFinalSalary(@RequestParam Integer employeeId,
                                       @RequestParam Integer month,
                                       @RequestParam Integer year) {
        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            salaryCalculationService.calculateFinalSalary(employee, month, year);
            return "redirect:/accountant/deductions?month=" + month + "&year=" + year + "&success=Финальный расчет завершен";

        } catch (Exception e) {
            return "redirect:/accountant/deductions?month=" + month + "&year=" + year + "&error=" + e.getMessage();
        }
    }

    @PostMapping("/deductions/calculate-all-taxes")
    public String calculateAllTaxes(@RequestParam Integer month,
                                    @RequestParam Integer year,
                                    @RequestParam(required = false) Integer departmentId) {
        try {
            List<Employee> employees = departmentId != null ?
                    employeeService.getActiveEmployeesByDepartment(departmentId) :
                    employeeService.getActiveEmployees();

            int successCount = 0;
            int errorCount = 0;

            for (Employee employee : employees) {
                try {
                    // Проверяем, есть ли уже налог для этого сотрудника
                    List<Payment> existingTaxes = paymentService.getEmployeePaymentsByType(employee, month, year, "INCOME_TAX");
                    if (existingTaxes.isEmpty()) {
                        salaryCalculationService.calculateIncomeTax(employee, month, year);
                        successCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                    System.err.println("Ошибка при расчете налога для " + employee.getFullName() + ": " + e.getMessage());
                }
            }

            String message = "Налоги рассчитаны для " + successCount + " сотрудников";
            if (errorCount > 0) {
                message += " (ошибки: " + errorCount + ")";
            }

            String redirectUrl = "redirect:/accountant/deductions?month=" + month + "&year=" + year + "&success=" + message;
            if (departmentId != null) {
                redirectUrl += "&departmentId=" + departmentId;
            }
            return redirectUrl;

        } catch (Exception e) {
            return "redirect:/accountant/deductions?month=" + month + "&year=" + year + "&error=" + e.getMessage();
        }
    }

    @GetMapping("/employee/{id}/deductions")
    public String employeeDeductions(@PathVariable Integer id,
                                     @RequestParam(defaultValue = "9") Integer month,
                                     @RequestParam(defaultValue = "2025") Integer year,
                                     Model model) {
        Employee employee = employeeService.getEmployeeById(id)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        List<Payment> employeePayments = paymentService.getEmployeePayments(employee, month, year);
        List<PaymentType> deductionTypes = paymentTypeService.getDeductionTypes();

        // Расчет сумм
        BigDecimal totalAccruals = employeePayments.stream()
                .filter(p -> p.getPaymentType().getCategory().equals("accrual"))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDeductions = employeePayments.stream()
                .filter(p -> p.getPaymentType().getCategory().equals("deduction"))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netSalary = totalAccruals.add(totalDeductions);

        model.addAttribute("employee", employee);
        model.addAttribute("payments", employeePayments);
        model.addAttribute("deductionTypes", deductionTypes);
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("totalAccruals", totalAccruals);
        model.addAttribute("totalDeductions", totalDeductions.abs());
        model.addAttribute("netSalary", netSalary);

        return "accountant/employee-deductions";
    }

    @GetMapping("/reports")
    public String reportsPage(@RequestParam(defaultValue = "9") Integer month,
                              @RequestParam(defaultValue = "2025") Integer year,
                              Model model) {

        List<Employee> employees = employeeService.getActiveEmployees();
        List<Department> departments = departmentService.getAllDepartments();

        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("employees", employees);
        model.addAttribute("departments", departments);

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

                case "deductions":
                    reportContent = reportService.generateDeductionsReport(month, year);
                    break;

                default:
                    throw new RuntimeException("Неизвестный тип отчета: " + reportType);
            }

            // В реальном приложении здесь был бы возврат файла или сохранение
            System.out.println("Сгенерирован отчет:\n" + reportContent);

            return "redirect:/accountant/reports?month=" + month + "&year=" + year + "&success=Отчет сгенерирован";

        } catch (Exception e) {
            return "redirect:/accountant/reports?month=" + month + "&year=" + year + "&error=" + e.getMessage();
        }
    }

    @GetMapping("/payments")
    public String paymentsPage(@RequestParam(defaultValue = "9") Integer month,
                               @RequestParam(defaultValue = "2025") Integer year,
                               @RequestParam(required = false) Integer departmentId,
                               Model model) {

        List<Payment> payments = paymentService.getPaymentsByPeriod(month, year);

        if (departmentId != null) {
            payments = payments.stream()
                    .filter(p -> p.getEmployee().getDepartment().getId().equals(departmentId))
                    .collect(Collectors.toList());
        }

        // Расчет итогов
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

        // Дополнительные данные для аналитики
        List<Employee> employees = employeeService.getActiveEmployees();
        Map<Integer, List<Payment>> employeePayments = paymentService.getPaymentsGroupedByEmployee(month, year);
        Map<Integer, BigDecimal> employeeAccruals = new HashMap<>();
        Map<Integer, BigDecimal> employeeDeductions = new HashMap<>();

        for (Employee employee : employees) {
            List<Payment> empPayments = employeePayments.get(employee.getId());
            if (empPayments != null) {
                BigDecimal accruals = empPayments.stream()
                        .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                        .map(Payment::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                employeeAccruals.put(employee.getId(), accruals);

                BigDecimal deductions = empPayments.stream()
                        .filter(p -> "deduction".equals(p.getPaymentType().getCategory()))
                        .map(Payment::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .abs();
                employeeDeductions.put(employee.getId(), deductions);
            } else {
                employeeAccruals.put(employee.getId(), BigDecimal.ZERO);
                employeeDeductions.put(employee.getId(), BigDecimal.ZERO);
            }
        }

        // Топ-5 сотрудников по начислениям (упрощенная версия)
        List<Employee> topEmployees = employees.stream()
                .filter(employee -> employeePayments.containsKey(employee.getId()))
                .sorted((e1, e2) -> employeeAccruals.get(e2.getId()).compareTo(employeeAccruals.get(e1.getId())))
                .limit(5)
                .collect(Collectors.toList());

        model.addAttribute("payments", payments);
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("departments", departments);
        model.addAttribute("totalAccruals", totalAccruals);
        model.addAttribute("totalDeductions", totalDeductions);
        model.addAttribute("netTotal", totalAccruals.subtract(totalDeductions));
        model.addAttribute("employees", employees);
        model.addAttribute("employeePayments", employeePayments);
        model.addAttribute("employeeAccruals", employeeAccruals);
        model.addAttribute("employeeDeductions", employeeDeductions);
        model.addAttribute("topEmployees", topEmployees); // ← ДОБАВЬ ЭТУ СТРОЧКУ

        return "accountant/payments";
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
}