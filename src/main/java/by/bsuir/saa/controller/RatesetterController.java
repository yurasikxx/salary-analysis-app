package by.bsuir.saa.controller;

import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.entity.Payment;
import by.bsuir.saa.repository.PaymentRepository;
import by.bsuir.saa.service.EmployeeService;
import by.bsuir.saa.service.SalaryCalculationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/ratesetter")
@PreAuthorize("hasRole('RATESETTER')")
public class RatesetterController {

    private final EmployeeService employeeService;
    private final SalaryCalculationService salaryCalculationService;
    private final PaymentRepository paymentRepository;

    public RatesetterController(EmployeeService employeeService,
                                SalaryCalculationService salaryCalculationService,
                                PaymentRepository paymentRepository) {
        this.employeeService = employeeService;
        this.salaryCalculationService = salaryCalculationService;
        this.paymentRepository = paymentRepository;
    }

    @GetMapping("/calculations")
    public String calculationsPage(@RequestParam(defaultValue = "09") Integer month,
                                   @RequestParam(defaultValue = "2025") Integer year,
                                   Model model) {
        List<Employee> activeEmployees = employeeService.getAllEmployees();
        model.addAttribute("employees", activeEmployees);
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        return "ratesetter/calculations";
    }

    @PostMapping("/calculations/basic")
    public String calculateBasicSalary(@RequestParam Integer employeeId,
                                       @RequestParam Integer month,
                                       @RequestParam Integer year) {
        Employee employee = employeeService.getEmployeeById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        salaryCalculationService.calculateBasicSalary(employee, month, year);
        return "redirect:/ratesetter/calculations?month=" + month + "&year=" + year + "&success=Basic salary calculated";
    }

    @PostMapping("/calculations/bonus")
    public String addBonus(@RequestParam Integer employeeId,
                           @RequestParam Integer month,
                           @RequestParam Integer year,
                           @RequestParam String bonusType,
                           @RequestParam BigDecimal amount,
                           @RequestParam String description) {
        Employee employee = employeeService.getEmployeeById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        salaryCalculationService.addBonus(employee, month, year, bonusType, amount, description);
        return "redirect:/ratesetter/calculations?month=" + month + "&year=" + year + "&success=Bonus added";
    }

    @GetMapping("/reports")
    public String reportsPage(@RequestParam(defaultValue = "9") Integer month,
                              @RequestParam(defaultValue = "2025") Integer year,
                              Model model) {
        model.addAttribute("month", month);
        model.addAttribute("year", year);

        List<Payment> payments = paymentRepository.findByMonthAndYear(month, year);
        model.addAttribute("payments", payments);

        return "ratesetter/reports";
    }
}