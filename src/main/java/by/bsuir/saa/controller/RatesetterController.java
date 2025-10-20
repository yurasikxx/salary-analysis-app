package by.bsuir.saa.controller;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.repository.PaymentRepository;
import by.bsuir.saa.service.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ratesetter")
@PreAuthorize("hasRole('RATESETTER')")
public class RatesetterController {

    private final EmployeeService employeeService;
    private final SalaryCalculationService salaryCalculationService;
    private final PaymentRepository paymentRepository;
    private final PaymentTypeService paymentTypeService;
    private final DepartmentService departmentService;
    private final PaymentService paymentService;

    public RatesetterController(EmployeeService employeeService,
                                SalaryCalculationService salaryCalculationService,
                                PaymentRepository paymentRepository,
                                PaymentTypeService paymentTypeService,
                                DepartmentService departmentService,
                                PaymentService paymentService) {
        this.employeeService = employeeService;
        this.salaryCalculationService = salaryCalculationService;
        this.paymentRepository = paymentRepository;
        this.paymentTypeService = paymentTypeService;
        this.departmentService = departmentService;
        this.paymentService = paymentService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "9") Integer month,
                            @RequestParam(defaultValue = "2025") Integer year,
                            Model model) {

        model.addAttribute("title", "Дашборд нормировщика");
        model.addAttribute("icon", "bi-calculator");
        model.addAttribute("month", month);
        model.addAttribute("year", year);

        // Статистика для дашборда
        long totalEmployees = employeeService.getActiveEmployees().size();
        long employeesWithCalculations = paymentService.getEmployeesWithCalculationsCount(month, year);

        BigDecimal totalAccruals = paymentService.getTotalAccruals(month, year);
        BigDecimal averageAccrual = totalEmployees > 0 ?
                totalAccruals.divide(BigDecimal.valueOf(totalEmployees), 2, java.math.RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        model.addAttribute("totalEmployees", totalEmployees);
        model.addAttribute("employeesWithCalculations", employeesWithCalculations);
        model.addAttribute("employeesWithoutCalculations", totalEmployees - employeesWithCalculations);
        model.addAttribute("totalAccruals", totalAccruals);
        model.addAttribute("averageAccrual", averageAccrual);
        model.addAttribute("completionRate", totalEmployees > 0 ?
                (employeesWithCalculations * 100 / totalEmployees) : 0);

        return "ratesetter/dashboard";
    }

    @GetMapping("/calculations")
    public String calculationsPage(@RequestParam(defaultValue = "9") Integer month,
                                   @RequestParam(defaultValue = "2025") Integer year,
                                   @RequestParam(required = false) Integer departmentId,
                                   Model model) {

        List<Employee> activeEmployees = employeeService.getActiveEmployees();

        if (departmentId != null) {
            activeEmployees = activeEmployees.stream()
                    .filter(e -> e.getDepartment().getId().equals(departmentId))
                    .toList();
        }

        Map<Integer, List<Payment>> employeePayments = salaryCalculationService.getPaymentsForPeriod(month, year);

        List<Department> departments = departmentService.getAllDepartments();
        List<PaymentType> bonusTypes = paymentTypeService.getAccrualTypes();

        model.addAttribute("employees", activeEmployees);
        model.addAttribute("employeePayments", employeePayments);
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("departments", departments);
        model.addAttribute("bonusTypes", bonusTypes);
        model.addAttribute("period", YearMonth.of(year, month));

        return "ratesetter/calculations";
    }

    @PostMapping("/calculations/basic")
    public String calculateBasicSalary(@RequestParam Integer employeeId,
                                       @RequestParam Integer month,
                                       @RequestParam Integer year) {
        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            salaryCalculationService.calculateBasicSalary(employee, month, year);
            return "redirect:/ratesetter/calculations?month=" + month + "&year=" + year + "&success=Основная часть зарплаты рассчитана";

        } catch (Exception e) {
            return "redirect:/ratesetter/calculations?month=" + month + "&year=" + year + "&error=" + e.getMessage();
        }
    }

    @PostMapping("/calculations/bonus")
    public String addBonus(@RequestParam Integer employeeId,
                           @RequestParam Integer month,
                           @RequestParam Integer year,
                           @RequestParam Integer paymentTypeId,
                           @RequestParam BigDecimal amount,
                           @RequestParam String description) {
        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            PaymentType paymentType = paymentTypeService.getPaymentTypeById(paymentTypeId)
                    .orElseThrow(() -> new RuntimeException("Тип выплаты не найден"));

            salaryCalculationService.addBonus(employee, month, year, paymentType, amount, description);
            return "redirect:/ratesetter/calculations?month=" + month + "&year=" + year + "&success=Премия/надбавка добавлена";

        } catch (Exception e) {
            return "redirect:/ratesetter/calculations?month=" + month + "&year=" + year + "&error=" + e.getMessage();
        }
    }

    @PostMapping("/calculations/recalculate-all")
    public String recalculateAll(@RequestParam Integer month,
                                 @RequestParam Integer year,
                                 @RequestParam(required = false) Integer departmentId) {
        try {
            List<Employee> employees = departmentId != null ?
                    employeeService.getActiveEmployeesByDepartment(departmentId) :
                    employeeService.getActiveEmployees();

            for (Employee employee : employees) {
                salaryCalculationService.calculateBasicSalary(employee, month, year);
            }

            String redirectUrl = "redirect:/ratesetter/calculations?month=" + month + "&year=" + year + "&success=Расчет завершен для всех сотрудников";
            if (departmentId != null) {
                redirectUrl += "&departmentId=" + departmentId;
            }
            return redirectUrl;

        } catch (Exception e) {
            return "redirect:/ratesetter/calculations?month=" + month + "&year=" + year + "&error=" + e.getMessage();
        }
    }

    @PostMapping("/calculations/{paymentId}/delete")
    public String deletePayment(@PathVariable Integer paymentId,
                                @RequestParam Integer month,
                                @RequestParam Integer year) {
        try {
            paymentRepository.deleteById(paymentId);
            return "redirect:/ratesetter/calculations?month=" + month + "&year=" + year + "&success=Запись удалена";

        } catch (Exception e) {
            return "redirect:/ratesetter/calculations?month=" + month + "&year=" + year + "&error=" + e.getMessage();
        }
    }

    @GetMapping("/reports")
    public String reportsPage(@RequestParam(defaultValue = "9") Integer month,
                              @RequestParam(defaultValue = "2025") Integer year,
                              @RequestParam(required = false) Integer departmentId,
                              Model model) {

        List<Payment> payments = paymentRepository.findByMonthAndYear(month, year);

        if (departmentId != null) {
            payments = payments.stream()
                    .filter(p -> p.getEmployee().getDepartment().getId().equals(departmentId))
                    .toList();
        }

        BigDecimal totalAccruals = payments.stream()
                .filter(p -> p.getPaymentType().getCategory().equals("accrual"))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDeductions = payments.stream()
                .filter(p -> p.getPaymentType().getCategory().equals("deduction"))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Department> departments = departmentService.getAllDepartments();
        List<PaymentType> paymentTypes = paymentTypeService.getAllPaymentTypes();

        model.addAttribute("payments", payments);
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("departments", departments);
        model.addAttribute("paymentTypes", paymentTypes);
        model.addAttribute("totalAccruals", totalAccruals);
        model.addAttribute("totalDeductions", totalDeductions);
        model.addAttribute("netTotal", totalAccruals.subtract(totalDeductions));

        return "ratesetter/reports";
    }

    @GetMapping("/employee/{id}/calculations")
    public String employeeCalculations(@PathVariable Integer id,
                                       @RequestParam(defaultValue = "9") Integer month,
                                       @RequestParam(defaultValue = "2025") Integer year,
                                       Model model) {
        Employee employee = employeeService.getEmployeeById(id)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        List<Payment> employeePayments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);
        List<PaymentType> bonusTypes = paymentTypeService.getAccrualTypes();

        // Расчет сумм
        BigDecimal totalAccruals = employeePayments.stream()
                .filter(p -> p.getPaymentType().getCategory().equals("accrual"))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDeductions = employeePayments.stream()
                .filter(p -> p.getPaymentType().getCategory().equals("deduction"))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("employee", employee);
        model.addAttribute("payments", employeePayments);
        model.addAttribute("bonusTypes", bonusTypes);
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("totalAccruals", totalAccruals);
        model.addAttribute("totalDeductions", totalDeductions);
        model.addAttribute("netSalary", totalAccruals.subtract(totalDeductions));

        return "ratesetter/employee-calculations";
    }
}