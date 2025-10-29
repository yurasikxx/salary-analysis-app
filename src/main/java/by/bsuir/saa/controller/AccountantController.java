package by.bsuir.saa.controller;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.repository.PaymentRepository;
import by.bsuir.saa.repository.SalaryPaymentRepository;
import by.bsuir.saa.service.*;
import by.bsuir.saa.util.MonthUtil;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Controller
@RequestMapping("/accountant")
@PreAuthorize("hasRole('ACCOUNTANT')")
public class AccountantController {

    private final EmployeeService employeeService;
    private final DepartmentService departmentService;
    private final PaymentTypeService paymentTypeService;
    private final PaymentService paymentService;
    private final BonusCalculationService bonusCalculationService;
    private final TaxCalculationService taxCalculationService;
    private final ReportService reportService;
    private final VacationSickLeaveCalculationService vacationSickLeaveService;
    private final PaymentRepository paymentRepository;
    private final FinalSalaryCalculationService finalSalaryCalculationService;
    private final SalaryPaymentRepository salaryPaymentRepository;

    public AccountantController(EmployeeService employeeService,
                                DepartmentService departmentService,
                                PaymentTypeService paymentTypeService,
                                PaymentService paymentService,
                                BonusCalculationService bonusCalculationService,
                                TaxCalculationService taxCalculationService,
                                ReportService reportService,
                                VacationSickLeaveCalculationService vacationSickLeaveService,
                                PaymentRepository paymentRepository,
                                FinalSalaryCalculationService finalSalaryCalculationService,
                                SalaryPaymentRepository salaryPaymentRepository) {
        this.employeeService = employeeService;
        this.departmentService = departmentService;
        this.paymentTypeService = paymentTypeService;
        this.paymentService = paymentService;
        this.bonusCalculationService = bonusCalculationService;
        this.taxCalculationService = taxCalculationService;
        this.reportService = reportService;
        this.vacationSickLeaveService = vacationSickLeaveService;
        this.paymentRepository = paymentRepository;
        this.finalSalaryCalculationService = finalSalaryCalculationService;
        this.salaryPaymentRepository = salaryPaymentRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                            Model model) {

        model.addAttribute("title", "Дашборд бухгалтера");
        model.addAttribute("icon", "bi-calculator");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(month));

        List<Employee> employees = employeeService.getActiveEmployees();
        long totalEmployees = employees.size();

        // Базовая статистика
        long employeesWithCalculations = paymentService.getEmployeesWithCalculationsCount(month, year);
        BigDecimal totalAccruals = paymentService.getTotalAccruals(month, year);
        BigDecimal totalDeductions = paymentService.getTotalDeductions(month, year);
        BigDecimal totalNetSalary = totalAccruals.add(totalDeductions);

        // Дополнительная статистика
        long employeesWithBonuses = paymentService.getEmployeesWithBonusesCount(month, year);
        long employeesWithTaxes = paymentService.getEmployeesWithTaxesCount(month, year);
        long employeesWithFinalSalary = finalSalaryCalculationService.getEmployeesWithFinalSalaryCount(month, year);

        // Статистика по типам начислений
        BigDecimal totalBaseSalary = paymentService.getTotalBaseSalary(month, year);
        BigDecimal totalEnterpriseBonuses = paymentService.getTotalEnterpriseBonuses(month, year);
        BigDecimal totalItrBonuses = paymentService.getTotalItrBonuses(month, year);
        BigDecimal totalSeniorityBonuses = paymentService.getTotalSeniorityBonuses(month, year);

        // Статистика по удержаниям
        BigDecimal totalIncomeTax = paymentService.getTotalIncomeTax(month, year);
        BigDecimal totalSocialTax = paymentService.getTotalSocialTax(month, year);
        BigDecimal totalOtherDeductions = paymentService.getTotalOtherDeductions(month, year);

        // Проценты для прогресс-баров
        double calculationRate = totalEmployees > 0 ? (employeesWithCalculations * 100.0 / totalEmployees) : 0;
        double bonusRate = totalEmployees > 0 ? (employeesWithBonuses * 100.0 / totalEmployees) : 0;
        double taxRate = totalEmployees > 0 ? (employeesWithTaxes * 100.0 / totalEmployees) : 0;
        double finalSalaryRate = totalEmployees > 0 ? (employeesWithFinalSalary * 100.0 / totalEmployees) : 0;

        // Проценты для этапов расчета
        double readyForBonusesPercent = calculationRate - bonusRate;
        double readyForTaxesPercent = bonusRate - taxRate;
        double readyForFinalPercent = taxRate - finalSalaryRate;
        double waitingForCalculationPercent = 100 - calculationRate;

        model.addAttribute("totalEmployees", totalEmployees);
        model.addAttribute("employeesWithCalculations", employeesWithCalculations);
        model.addAttribute("employeesWithBonuses", employeesWithBonuses);
        model.addAttribute("employeesWithTaxes", employeesWithTaxes);
        model.addAttribute("employeesWithFinalSalary", employeesWithFinalSalary);

        model.addAttribute("totalAccruals", totalAccruals);
        model.addAttribute("totalDeductions", totalDeductions);
        model.addAttribute("totalNetSalary", totalNetSalary);

        model.addAttribute("totalBaseSalary", totalBaseSalary);
        model.addAttribute("totalEnterpriseBonuses", totalEnterpriseBonuses);
        model.addAttribute("totalItrBonuses", totalItrBonuses);
        model.addAttribute("totalSeniorityBonuses", totalSeniorityBonuses);

        model.addAttribute("totalIncomeTax", totalIncomeTax);
        model.addAttribute("totalSocialTax", totalSocialTax);
        model.addAttribute("totalOtherDeductions", totalOtherDeductions);

        model.addAttribute("calculationRate", Math.round(calculationRate));
        model.addAttribute("bonusRate", Math.round(bonusRate));
        model.addAttribute("taxRate", Math.round(taxRate));
        model.addAttribute("finalSalaryRate", Math.round(finalSalaryRate));

        model.addAttribute("calculatedPercent", calculationRate);
        model.addAttribute("bonusPercent", bonusRate);
        model.addAttribute("taxPercent", taxRate);
        model.addAttribute("finalSalaryPercent", finalSalaryRate);

        model.addAttribute("readyForBonusesPercent", readyForBonusesPercent);
        model.addAttribute("readyForTaxesPercent", readyForTaxesPercent);
        model.addAttribute("readyForFinalPercent", readyForFinalPercent);
        model.addAttribute("waitingForCalculationPercent", waitingForCalculationPercent);

        return "accountant/dashboard";
    }

    @GetMapping("/vacation-sickleave")
    public String vacationSickLeavePage(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                                        @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                                        @RequestParam(required = false) Integer departmentId,
                                        Model model) {

        model.addAttribute("title", "Расчет отпускных и больничных");
        model.addAttribute("icon", "bi-calendar-heart");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(month));

        List<Employee> employees = employeeService.getActiveEmployees();
        List<Department> departments = departmentService.getAllDepartments();

        if (departmentId != null) {
            employees = employees.stream()
                    .filter(e -> e.getDepartment().getId().equals(departmentId))
                    .toList();
        }

        List<VacationSickLeaveCalculationService.CalculationInfo> calculationInfos = employees.stream()
                .map(employee -> vacationSickLeaveService.getCalculationInfo(employee, month, year))
                .toList();

        long employeesWithSickLeave = calculationInfos.stream()
                .filter(info -> info.getSickLeaveDays() > 0)
                .count();
        long employeesWithVacation = calculationInfos.stream()
                .filter(info -> info.getVacationDays() > 0)
                .count();
        long employeesCalculated = calculationInfos.stream()
                .filter(info -> info.isHasSickLeavePayment() || info.isHasVacationPayment())
                .count();

        model.addAttribute("calculationInfos", calculationInfos);
        model.addAttribute("departments", departments);
        model.addAttribute("totalEmployees", employees.size());
        model.addAttribute("employeesWithSickLeave", employeesWithSickLeave);
        model.addAttribute("employeesWithVacation", employeesWithVacation);
        model.addAttribute("employeesCalculated", employeesCalculated);

        return "accountant/vacation-sickleave";
    }

    @PostMapping("/vacation-sickleave/calculate-sickleave")
    public String calculateSickLeave(@RequestParam Integer employeeId,
                                     @RequestParam Integer month,
                                     @RequestParam Integer year,
                                     RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            vacationSickLeaveService.calculateAndSaveSickLeave(employee, month, year);

            redirectAttributes.addFlashAttribute("success",
                    "Больничные для " + employee.getFullName() + " рассчитаны успешно");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/accountant/vacation-sickleave?month=" + month + "&year=" + year;
    }

    @PostMapping("/vacation-sickleave/calculate-vacation")
    public String calculateVacation(@RequestParam Integer employeeId,
                                    @RequestParam Integer month,
                                    @RequestParam Integer year,
                                    RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            vacationSickLeaveService.calculateAndSaveVacation(employee, month, year);

            redirectAttributes.addFlashAttribute("success",
                    "Отпускные для " + employee.getFullName() + " рассчитаны успешно");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/accountant/vacation-sickleave?month=" + month + "&year=" + year;
    }

    @PostMapping("/vacation-sickleave/calculate-all")
    public String calculateAllForEmployee(@RequestParam Integer employeeId,
                                          @RequestParam Integer month,
                                          @RequestParam Integer year,
                                          RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            vacationSickLeaveService.calculateAndSaveAll(employee, month, year);

            redirectAttributes.addFlashAttribute("success",
                    "Отпускные и больничные для " + employee.getFullName() + " рассчитаны успешно");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/accountant/vacation-sickleave?month=" + month + "&year=" + year;
    }

    @PostMapping("/vacation-sickleave/calculate-batch")
    public String calculateBatch(@RequestParam Integer month,
                                 @RequestParam Integer year,
                                 RedirectAttributes redirectAttributes) {
        try {
            int calculatedCount = vacationSickLeaveService.calculateBatch(month, year);

            redirectAttributes.addFlashAttribute("success",
                    "Отпускные и больничные рассчитаны для " + calculatedCount + " сотрудников");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/accountant/vacation-sickleave?month=" + month + "&year=" + year;
    }

    @PostMapping("/vacation-sickleave/{id}/delete")
    public String deleteVacationSickLeavePayment(@PathVariable Integer id,
                                                 @RequestParam Integer month,
                                                 @RequestParam Integer year,
                                                 RedirectAttributes redirectAttributes) {
        try {
            Payment payment = paymentService.getPaymentById(id)
                    .orElseThrow(() -> new RuntimeException("Платеж не найден"));

            if (!paymentTypeService.isVacationOrSickLeavePayment(payment.getPaymentType())) {
                throw new RuntimeException("Можно удалять только платежи отпускных и больничных");
            }

            if (hasTaxes(payment.getEmployee(), month, year)) {
                throw new RuntimeException("Нельзя удалять платежи после начисления налогов");
            }

            paymentService.deletePayment(id);
            redirectAttributes.addFlashAttribute("success", "Платеж удален");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/accountant/vacation-sickleave?month=" + month + "&year=" + year;
    }

    @GetMapping("/enterprise-bonuses")
    public String enterpriseBonusesPage(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                                        @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                                        @RequestParam(required = false) Integer departmentId,
                                        Model model) {

        model.addAttribute("title", "Начисление общих премий по предприятию");
        model.addAttribute("icon", "bi-award");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(month));

        List<Employee> employees = employeeService.getActiveEmployees();
        List<Department> departments = departmentService.getAllDepartments();

        if (departmentId != null) {
            employees = employees.stream()
                    .filter(e -> e.getDepartment().getId().equals(departmentId))
                    .toList();
        }

        List<EmployeeBonusInfo> bonusInfos = employees.stream()
                .map(employee -> {
                    EmployeeBonusInfo info = new EmployeeBonusInfo();
                    info.setEmployee(employee);

                    List<Payment> allPayments = paymentService.getEmployeePayments(employee, month, year);

                    // Только премии бухгалтера (ПРЕД, ИТР)
                    List<Payment> accountantBonuses = allPayments.stream()
                            .filter(p -> "ПРЕД".equals(p.getPaymentType().getCode()) ||
                                    "ИТР".equals(p.getPaymentType().getCode()))
                            .toList();

                    info.setExistingBonuses(accountantBonuses);
                    info.setTotalBonuses(accountantBonuses.stream()
                            .map(Payment::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add));

                    // Проверяем наличие конкретных премий
                    boolean hasEnterpriseBonus = allPayments.stream()
                            .anyMatch(p -> "ПРЕД".equals(p.getPaymentType().getCode()));
                    boolean hasItrBonus = allPayments.stream()
                            .anyMatch(p -> "ИТР".equals(p.getPaymentType().getCode()));

                    info.setHasEnterpriseBonus(hasEnterpriseBonus);
                    info.setHasItrBonus(hasItrBonus);

                    // Суммы премий
                    BigDecimal enterpriseBonusAmount = allPayments.stream()
                            .filter(p -> "ПРЕД".equals(p.getPaymentType().getCode()))
                            .map(Payment::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    info.setEnterpriseBonusAmount(enterpriseBonusAmount);

                    BigDecimal itrBonusAmount = allPayments.stream()
                            .filter(p -> "ИТР".equals(p.getPaymentType().getCode()))
                            .map(Payment::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    info.setItrBonusAmount(itrBonusAmount);

                    // Проверяем, рассчитан ли оклад
                    boolean hasBaseSalary = allPayments.stream()
                            .anyMatch(p -> "ОКЛ".equals(p.getPaymentType().getCode()));
                    info.setHasBaseSalary(hasBaseSalary);

                    return info;
                })
                .toList();

        long employeesWithEnterpriseBonus = bonusInfos.stream()
                .filter(EmployeeBonusInfo::isHasEnterpriseBonus)
                .count();

        BigDecimal totalEnterpriseBonuses = bonusInfos.stream()
                .map(EmployeeBonusInfo::getEnterpriseBonusAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long employeesWithoutItrBonus = bonusInfos.stream()
                .filter(info -> info.hasBaseSalary && !info.hasItrBonus)
                .count();

        BigDecimal totalItrBonuses = bonusInfos.stream()
                .map(EmployeeBonusInfo::getItrBonusAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long employeesWithItrBonus = bonusInfos.stream()
                .filter(EmployeeBonusInfo::isHasItrBonus)
                .count();

        model.addAttribute("bonusInfos", bonusInfos);
        model.addAttribute("departments", departments);
        model.addAttribute("totalEmployees", employees.size());
        model.addAttribute("employeesWithEnterpriseBonus", employeesWithEnterpriseBonus);
        model.addAttribute("totalEnterpriseBonuses", totalEnterpriseBonuses);
        model.addAttribute("employeesWithoutItrBonus", employeesWithoutItrBonus);
        model.addAttribute("totalItrBonuses", totalItrBonuses);
        model.addAttribute("employeesWithItrBonus", employeesWithItrBonus);

        return "accountant/enterprise-bonuses";
    }

    @GetMapping("/enterprise-bonuses/add")
    public String addEnterpriseBonusForm(@RequestParam Integer employeeId,
                                         @RequestParam Integer month,
                                         @RequestParam Integer year,
                                         Model model) {

        Employee employee = employeeService.getEmployeeById(employeeId)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        if (!bonusCalculationService.isBaseSalaryCalculated(employee, month, year)) {
            throw new RuntimeException("Нельзя добавлять премии до расчета основной зарплаты");
        }

        PaymentType enterpriseBonusType = paymentTypeService.getPaymentTypeByCode("ПРЕД")
                .orElseThrow(() -> new RuntimeException("Тип оплаты ПРЕД не найден"));

        model.addAttribute("title", "Добавить премию по предприятию");
        model.addAttribute("icon", "bi-plus-circle");
        model.addAttribute("employee", employee);
        model.addAttribute("enterpriseBonusType", enterpriseBonusType);
        model.addAttribute("month", month);
        model.addAttribute("year", year);

        return "accountant/add-enterprise-bonus";
    }

    // В методах добавления/удаления платежей добавим проверку:

    @PostMapping("/enterprise-bonuses")
    public String addEnterpriseBonus(@RequestParam Integer employeeId,
                                     @RequestParam Integer month,
                                     @RequestParam Integer year,
                                     @RequestParam BigDecimal amount,
                                     @RequestParam(required = false) String description,
                                     RedirectAttributes redirectAttributes) {

        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            if (!bonusCalculationService.isBaseSalaryCalculated(employee, month, year)) {
                throw new RuntimeException("Нельзя добавлять премии до расчета основной зарплаты");
            }

            // Проверяем, не рассчитаны ли уже налоги
            if (taxCalculationService.hasTaxesCalculated(employee, month, year)) {
                throw new RuntimeException("Нельзя добавлять начисления после расчета налогов");
            }

            PaymentType enterpriseBonusType = paymentTypeService.getPaymentTypeByCode("ПРЕД")
                    .orElseThrow(() -> new RuntimeException("Тип оплаты ПРЕД не найден"));

            boolean alreadyExists = paymentService.getEmployeePayments(employee, month, year).stream()
                    .anyMatch(p -> "ПРЕД".equals(p.getPaymentType().getCode()));

            if (alreadyExists) {
                throw new RuntimeException("Премия по предприятию уже была начислена за этот период");
            }

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Сумма премии должна быть положительной");
            }

            paymentService.createPayment(employee, month, year, enterpriseBonusType, amount,
                    description != null ? description.trim() : null);

            redirectAttributes.addFlashAttribute("success",
                    "Премия по предприятию на сумму " + amount + " руб. начислена");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/accountant/enterprise-bonuses?month=" + month + "&year=" + year;
    }

    @PostMapping("/enterprise-bonuses/{id}/delete")
    public String deleteEnterpriseBonus(@PathVariable Integer id,
                                        @RequestParam Integer month,
                                        @RequestParam Integer year,
                                        RedirectAttributes redirectAttributes) {
        try {
            Payment payment = paymentService.getPaymentById(id)
                    .orElseThrow(() -> new RuntimeException("Премия не найдена"));

            String paymentCode = payment.getPaymentType().getCode();

            // Бухгалтер может удалять ПРЕД и ИТР
            if (!"ПРЕД".equals(paymentCode) && !"ИТР".equals(paymentCode)) {
                throw new RuntimeException("Бухгалтер может удалять только премии ПРЕД и ИТР");
            }

            // Проверяем, есть ли уже налоги
            if (taxCalculationService.hasTaxesCalculated(payment.getEmployee(), month, year)) {
                throw new RuntimeException("Нельзя удалять премии после начисления налогов");
            }

            paymentService.deletePayment(id);
            redirectAttributes.addFlashAttribute("success", "Премия удалена");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/accountant/enterprise-bonuses?month=" + month + "&year=" + year;
    }

    @PostMapping("/enterprise-bonuses/calculate-itr")
    public String calculateItrBonus(@RequestParam Integer employeeId,
                                    @RequestParam Integer month,
                                    @RequestParam Integer year,
                                    RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            bonusCalculationService.calculateItrBonus(employee, month, year);

            redirectAttributes.addFlashAttribute("success",
                    "Премия ИТР для " + employee.getFullName() + " рассчитана успешно");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/accountant/enterprise-bonuses?month=" + month + "&year=" + year;
    }

    @PostMapping("/enterprise-bonuses/calculate-itr-batch")
    public String calculateItrBonusBatch(@RequestParam Integer month,
                                         @RequestParam Integer year,
                                         RedirectAttributes redirectAttributes) {
        try {
            List<Employee> employees = employeeService.getActiveEmployees();
            int calculatedCount = 0;

            for (Employee employee : employees) {
                if (bonusCalculationService.isBaseSalaryCalculated(employee, month, year) &&
                        paymentService.getEmployeePayments(employee, month, year).stream()
                                .noneMatch(p -> "ИТР".equals(p.getPaymentType().getCode()))) {
                    try {
                        bonusCalculationService.calculateItrBonus(employee, month, year);
                        calculatedCount++;
                    } catch (Exception e) {
                        System.err.println("Ошибка расчета премии ИТР для " + employee.getFullName() + ": " + e.getMessage());
                    }
                }
            }

            redirectAttributes.addFlashAttribute("success",
                    "Премия ИТР рассчитана для " + calculatedCount + " сотрудников");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/accountant/enterprise-bonuses?month=" + month + "&year=" + year;
    }

    @GetMapping("/deductions")
    public String deductionsPage(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                                 @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                                 @RequestParam(required = false) Integer departmentId,
                                 Model model) {

        model.addAttribute("title", "Удержания из заработной платы");
        model.addAttribute("icon", "bi-calculator");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(month));

        List<Employee> employees = employeeService.getActiveEmployees();
        List<Department> departments = departmentService.getAllDepartments();
        List<PaymentType> deductionTypes = paymentTypeService.getDeductionTypes();

        if (departmentId != null) {
            employees = employees.stream()
                    .filter(e -> e.getDepartment().getId().equals(departmentId))
                    .toList();
        }

        List<EmployeeDeductionInfo> deductionInfos = employees.stream()
                .map(employee -> {
                    EmployeeDeductionInfo info = new EmployeeDeductionInfo();
                    info.setEmployee(employee);

                    List<Payment> allPayments = paymentService.getEmployeePayments(employee, month, year);

                    List<Payment> deductions = allPayments.stream()
                            .filter(p -> "deduction".equals(p.getPaymentType().getCategory()))
                            .toList();

                    info.setExistingDeductions(deductions);
                    info.setTotalDeductions(deductions.stream()
                            .map(p -> p.getAmount().abs())
                            .reduce(BigDecimal.ZERO, BigDecimal::add));

                    // Рассчитываем общую сумму начислений для отображения
                    BigDecimal totalAccruals = allPayments.stream()
                            .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                            .map(Payment::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    info.setTotalAccruals(totalAccruals);

                    // Рассчитываем предполагаемые налоги
                    info.setCalculatedIncomeTax(totalAccruals.multiply(new BigDecimal("0.13")).setScale(2, RoundingMode.HALF_UP));
                    info.setCalculatedSocialTax(totalAccruals.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP));

                    // Проверяем наличие основных удержаний
                    info.setHasIncomeTax(deductions.stream().anyMatch(p -> "ПН".equals(p.getPaymentType().getCode())));
                    info.setHasSocialTax(deductions.stream().anyMatch(p -> "ФСЗН".equals(p.getPaymentType().getCode())));

                    // Проверяем, рассчитан ли оклад и можно ли рассчитывать налоги
                    boolean hasBaseSalary = allPayments.stream()
                            .anyMatch(p -> "ОКЛ".equals(p.getPaymentType().getCode()));
                    info.setHasBaseSalary(hasBaseSalary);

                    // Проверяем, есть ли надбавки для расчета налогов
                    boolean hasBonuses = allPayments.stream()
                            .anyMatch(p -> "accrual".equals(p.getPaymentType().getCategory()) &&
                                    !"ОКЛ".equals(p.getPaymentType().getCode()));
                    info.setHasBonuses(hasBonuses);

                    return info;
                })
                .toList();

        long employeesWithDeductions = deductionInfos.stream()
                .filter(EmployeeDeductionInfo::hasDeductions)
                .count();

        BigDecimal totalDeductionsAmount = deductionInfos.stream()
                .map(EmployeeDeductionInfo::getTotalDeductions)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAccrualsAmount = deductionInfos.stream()
                .map(EmployeeDeductionInfo::getTotalAccruals)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("deductionInfos", deductionInfos);
        model.addAttribute("departments", departments);
        model.addAttribute("deductionTypes", deductionTypes);
        model.addAttribute("totalEmployees", employees.size());
        model.addAttribute("employeesWithDeductions", employeesWithDeductions);
        model.addAttribute("totalDeductionsAmount", totalDeductionsAmount);
        model.addAttribute("totalAccrualsAmount", totalAccrualsAmount);

        return "accountant/deductions";
    }

    @GetMapping("/deductions/add")
    public String addDeductionForm(@RequestParam Integer employeeId,
                                   @RequestParam Integer month,
                                   @RequestParam Integer year,
                                   Model model) {

        Employee employee = employeeService.getEmployeeById(employeeId)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        if (!bonusCalculationService.isBaseSalaryCalculated(employee, month, year)) {
            throw new RuntimeException("Нельзя добавлять удержания до расчета основной зарплаты");
        }

        // Только профсоюз и алименты для ручного добавления
        List<PaymentType> deductionTypes = paymentTypeService.getDeductionTypes().stream()
                .filter(pt -> "АЛ".equals(pt.getCode()) || "ПВ".equals(pt.getCode()))
                .toList();

        model.addAttribute("title", "Добавить удержание");
        model.addAttribute("icon", "bi-plus-circle");
        model.addAttribute("employee", employee);
        model.addAttribute("deductionTypes", deductionTypes);
        model.addAttribute("month", month);
        model.addAttribute("year", year);

        return "accountant/add-deduction";
    }

    @PostMapping("/deductions")
    public String addDeduction(@RequestParam Integer employeeId,
                               @RequestParam Integer month,
                               @RequestParam Integer year,
                               @RequestParam Integer paymentTypeId,
                               @RequestParam BigDecimal amount,
                               @RequestParam(required = false) String description,
                               RedirectAttributes redirectAttributes) {

        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            if (!bonusCalculationService.isBaseSalaryCalculated(employee, month, year)) {
                throw new RuntimeException("Нельзя добавлять удержания до расчета основной зарплаты");
            }

            PaymentType paymentType = paymentTypeService.getPaymentTypeById(paymentTypeId)
                    .orElseThrow(() -> new RuntimeException("Тип удержания не найден"));

            if (!"deduction".equals(paymentType.getCategory())) {
                throw new RuntimeException("Можно добавлять только виды deduction");
            }

            // Для удержаний сумма должна быть отрицательной
            BigDecimal deductionAmount = amount.negate();

            paymentService.createPayment(employee, month, year, paymentType, deductionAmount, description);

            redirectAttributes.addFlashAttribute("success",
                    "Удержание '" + paymentType.getName() + "' на сумму " + amount + " руб. добавлено");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/accountant/deductions?month=%d&year=%d".formatted(month, year);
    }

    @PostMapping("/deductions/{id}/delete")
    public String deleteDeduction(@PathVariable Integer id,
                                  @RequestParam Integer month,
                                  @RequestParam Integer year,
                                  RedirectAttributes redirectAttributes) {
        try {
            Payment payment = paymentService.getPaymentById(id)
                    .orElseThrow(() -> new RuntimeException("Удержание не найдено"));

            String paymentCode = payment.getPaymentType().getCode();

            // Можно удалять только профсоюз и алименты, налоги удалять нельзя
            if (!"АЛ".equals(paymentCode) && !"ПВ".equals(paymentCode)) {
                throw new RuntimeException("Можно удалять только удержания АЛ и ПВ");
            }

            paymentService.deletePayment(id);
            redirectAttributes.addFlashAttribute("success", "Удержание удалено");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/accountant/deductions?month=%d&year=%d".formatted(month, year);
    }

    @PostMapping("/deductions/calculate-taxes")
    public String calculateTaxes(@RequestParam Integer month,
                                 @RequestParam Integer year,
                                 RedirectAttributes redirectAttributes) {
        try {
            taxCalculationService.calculateTaxesBatch(month, year);
            redirectAttributes.addFlashAttribute("success", "Налоги рассчитаны для всех сотрудников");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/accountant/deductions?month=" + month + "&year=" + year;
    }

    @GetMapping("/recalculate-taxes")
    public String recalculateTaxes(@RequestParam Integer employeeId,
                                   @RequestParam Integer month,
                                   @RequestParam Integer year,
                                   RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            // Удаляем существующие налоги
            List<Payment> existingTaxes = paymentService.getEmployeePayments(employee, month, year)
                    .stream()
                    .filter(p -> "ПН".equals(p.getPaymentType().getCode()) || "ФСЗН".equals(p.getPaymentType().getCode()))
                    .toList();

            for (Payment tax : existingTaxes) {
                paymentService.deletePayment(tax.getId());
            }

            // Пересчитываем налоги
            taxCalculationService.calculateAndSaveTaxes(employee, month, year);

            redirectAttributes.addFlashAttribute("success", "Налоги пересчитаны для " + employee.getFullName());

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/accountant/deductions?month=" + month + "&year=" + year;
    }

    @PostMapping("/deductions/calculate-taxes-employee")
    public String calculateTaxesForEmployee(@RequestParam Integer employeeId,
                                            @RequestParam Integer month,
                                            @RequestParam Integer year,
                                            RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            taxCalculationService.calculateAndSaveTaxes(employee, month, year);
            redirectAttributes.addFlashAttribute("success", "Налоги рассчитаны для сотрудника");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/accountant/deductions?month=" + month + "&year=" + year;
    }

    @GetMapping("/final-salary")
    public String finalSalaryPage(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                                  @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                                  @RequestParam(required = false) Integer departmentId,
                                  Model model) {

        model.addAttribute("title", "Расчет итоговой заработной платы");
        model.addAttribute("icon", "bi-cash-stack");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(month));

        List<Employee> employees = employeeService.getActiveEmployees();
        List<Department> departments = departmentService.getAllDepartments();

        if (departmentId != null) {
            employees = employees.stream()
                    .filter(e -> e.getDepartment().getId().equals(departmentId))
                    .toList();
        }

        List<FinalSalaryInfo> salaryInfos = employees.stream()
                .map(employee -> {
                    FinalSalaryInfo info = new FinalSalaryInfo();
                    info.setEmployee(employee);

                    // Рассчитываем зарплату
                    FinalSalaryCalculationService.FinalSalaryResult result =
                            finalSalaryCalculationService.calculateFinalSalaryForEmployee(employee, month, year);

                    info.setTotalAccrued(result.getTotalAccrued());
                    info.setTotalDeducted(result.getTotalDeducted());
                    info.setNetSalary(result.getNetSalary());
                    info.setPayments(result.getPayments());

                    // Проверяем, рассчитана ли уже итоговая зарплата
                    info.setFinalSalaryCalculated(
                            finalSalaryCalculationService.isFinalSalaryCalculated(employee, month, year));

                    // Проверяем, есть ли начисления
                    info.setHasPayments(!result.getPayments().isEmpty());

                    return info;
                })
                .toList();

        // Получаем общую статистику
        FinalSalaryCalculationService.FinalSalarySummary summary =
                finalSalaryCalculationService.getFinalSalarySummary(month, year);

        long employeesWithPayments = salaryInfos.stream()
                .filter(FinalSalaryInfo::isHasPayments)
                .count();

        long employeesWithFinalSalary = salaryInfos.stream()
                .filter(FinalSalaryInfo::isFinalSalaryCalculated)
                .count();

        model.addAttribute("salaryInfos", salaryInfos);
        model.addAttribute("departments", departments);
        model.addAttribute("summary", summary);
        model.addAttribute("totalEmployees", employees.size());
        model.addAttribute("employeesWithPayments", employeesWithPayments);
        model.addAttribute("employeesWithFinalSalary", employeesWithFinalSalary);

        return "accountant/final-salary";
    }

    @PostMapping("/final-salary/calculate")
    public String calculateFinalSalary(@RequestParam Integer employeeId,
                                       @RequestParam Integer month,
                                       @RequestParam Integer year,
                                       RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            finalSalaryCalculationService.calculateAndSaveFinalSalary(employee, month, year);

            // Получаем результат для отображения суммы
            FinalSalaryCalculationService.FinalSalaryResult result =
                    finalSalaryCalculationService.calculateFinalSalaryForEmployee(employee, month, year);

            redirectAttributes.addFlashAttribute("success",
                    "Итоговая зарплата для " + employee.getFullName() + " рассчитана: " +
                            result.getNetSalary() + " руб. к выплате");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/accountant/final-salary?month=" + month + "&year=" + year;
    }

    @PostMapping("/final-salary/calculate-batch")
    public String calculateFinalSalaryBatch(@RequestParam Integer month,
                                            @RequestParam Integer year,
                                            RedirectAttributes redirectAttributes) {
        try {
            int calculatedCount = finalSalaryCalculationService.calculateFinalSalariesBatch(month, year);

            redirectAttributes.addFlashAttribute("success",
                    "Итоговая зарплата рассчитана для " + calculatedCount + " сотрудников");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/accountant/final-salary?month=" + month + "&year=" + year;
    }

    // В AccountantController добавим эти методы

    @GetMapping("/reports")
    public String reportsPage(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                              @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                              @RequestParam(required = false) Integer departmentId,
                              Model model) {

        model.addAttribute("title", "Формирование отчетности");
        model.addAttribute("icon", "bi-file-earmark-text");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(month));

        List<Department> departments = departmentService.getAllDepartments();
        List<Employee> employees = employeeService.getActiveEmployees();

        // Получаем статистику для отображения
        ReportStatistics statistics = getReportStatistics(month, year);

        // Получаем недавно рассчитанные зарплаты
        List<SalaryPayment> recentSalaries = salaryPaymentRepository.findByMonthAndYear(month, year)
                .stream()
                .limit(10)
                .toList();

        model.addAttribute("departments", departments);
        model.addAttribute("employees", employees);
        model.addAttribute("recentSalaries", recentSalaries);
        model.addAttribute("statistics", statistics);

        return "accountant/reports";
    }

    @GetMapping("/reports/payslip-pdf")
    public ResponseEntity<byte[]> generatePayslipPdf(@RequestParam Integer employeeId,
                                                     @RequestParam Integer month,
                                                     @RequestParam Integer year) {
        try {
            byte[] pdfBytes = reportService.generatePayslipPdf(employeeId, month, year);

            String filename = "payslip_" + employeeId + "_" + month + "_" + year + ".pdf";

            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body(pdfBytes);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/reports/salary-statement-pdf")
    public ResponseEntity<byte[]> generateSalaryStatementPdf(@RequestParam(required = false) Integer departmentId,
                                                             @RequestParam Integer month,
                                                             @RequestParam Integer year) {
        try {
            byte[] pdfBytes = reportService.generateSalaryStatementPdf(departmentId, month, year);

            String filename = "salary_statement_" + (departmentId != null ? departmentId : "all") +
                    "_" + month + "_" + year + ".pdf";

            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body(pdfBytes);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/reports/salary-excel")
    public ResponseEntity<byte[]> generateSalaryExcel(@RequestParam Integer month,
                                                      @RequestParam Integer year) {
        try {
            byte[] excelBytes = reportService.generateSalaryReportExcel(month, year);

            String filename = "salary_report_" + month + "_" + year + ".xlsx";

            return ResponseEntity.ok()
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body(excelBytes);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Добавим эти методы в AccountantController

    @GetMapping("/reports/detailed-salary-excel")
    public ResponseEntity<byte[]> generateDetailedSalaryExcel(@RequestParam Integer month,
                                                              @RequestParam Integer year) {
        try {
            byte[] excelBytes = reportService.generateDetailedSalaryReportExcel(month, year);

            String filename = "detailed_salary_report_" + month + "_" + year + ".xlsx";

            return ResponseEntity.ok()
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body(excelBytes);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/reports/department-salary-excel")
    public ResponseEntity<byte[]> generateDepartmentSalaryExcel(@RequestParam Integer departmentId,
                                                                @RequestParam Integer month,
                                                                @RequestParam Integer year) {
        try {
            // Можно создать специализированный метод для отчетов по отделам
            byte[] excelBytes = reportService.generateSalaryReportExcel(month, year); // временно

            String filename = "department_salary_" + departmentId + "_" + month + "_" + year + ".xlsx";

            return ResponseEntity.ok()
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .body(excelBytes);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private boolean hasTaxes(Employee employee, Integer month, Integer year) {
        return paymentRepository.hasTaxesForEmployee(employee, month, year);
    }

    private boolean hasBonuses(Employee employee, Integer month, Integer year) {
        List<Payment> payments = paymentService.getEmployeePayments(employee, month, year);
        return payments.stream()
                .anyMatch(p -> "accrual".equals(p.getPaymentType().getCategory()) &&
                        !"ОКЛ".equals(p.getPaymentType().getCode()));
    }

    private ReportStatistics getReportStatistics(Integer month, Integer year) {
        List<Employee> employees = employeeService.getActiveEmployees();
        long totalEmployees = employees.size();

        BigDecimal totalAccrued = BigDecimal.ZERO;
        BigDecimal totalDeducted = BigDecimal.ZERO;
        BigDecimal totalNetSalary = BigDecimal.ZERO;

        for (Employee employee : employees) {
            List<Payment> payments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);

            BigDecimal accruals = payments.stream()
                    .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal deductions = payments.stream()
                    .filter(p -> "deduction".equals(p.getPaymentType().getCategory()))
                    .map(p -> p.getAmount().abs())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal netSalary = accruals.subtract(deductions);

            totalAccrued = totalAccrued.add(accruals);
            totalDeducted = totalDeducted.add(deductions);
            totalNetSalary = totalNetSalary.add(netSalary);
        }

        return new ReportStatistics(totalEmployees, totalAccrued, totalDeducted, totalNetSalary);
    }

    @Data
    public static class EmployeeBonusInfo {
        private Employee employee;
        private List<Payment> existingBonuses;
        private BigDecimal totalBonuses;
        private boolean hasEnterpriseBonus;
        private boolean hasItrBonus;
        private boolean hasBaseSalary;
        private BigDecimal enterpriseBonusAmount = BigDecimal.ZERO;
        private BigDecimal itrBonusAmount = BigDecimal.ZERO;

        public boolean hasBonuses() {
            return existingBonuses != null && !existingBonuses.isEmpty();
        }

        public boolean canCalculateItrBonus() {
            return hasBaseSalary && !hasItrBonus;
        }
    }

    @Data
    public static class EmployeeDeductionInfo {
        private Employee employee;
        private List<Payment> existingDeductions;
        private BigDecimal totalDeductions = BigDecimal.ZERO;
        private BigDecimal totalAccruals = BigDecimal.ZERO;
        private BigDecimal calculatedIncomeTax = BigDecimal.ZERO;
        private BigDecimal calculatedSocialTax = BigDecimal.ZERO;
        private boolean hasIncomeTax;
        private boolean hasSocialTax;
        private boolean hasBaseSalary;
        private boolean hasBonuses;

        public boolean hasDeductions() {
            return existingDeductions != null && !existingDeductions.isEmpty();
        }

        public boolean canCalculateTaxes() {
            return hasBaseSalary && hasBonuses && !hasIncomeTax && !hasSocialTax && totalAccruals.compareTo(BigDecimal.ZERO) > 0;
        }

        public boolean hasCalculatedTaxes() {
            return hasIncomeTax && hasSocialTax;
        }
    }

    @Data
    public static class FinalSalaryInfo {
        private Employee employee;
        private BigDecimal totalAccrued = BigDecimal.ZERO;
        private BigDecimal totalDeducted = BigDecimal.ZERO;
        private BigDecimal netSalary = BigDecimal.ZERO;
        private List<Payment> payments;
        private boolean finalSalaryCalculated;
        private boolean hasPayments;

        public boolean canCalculateFinalSalary() {
            return hasPayments && !finalSalaryCalculated && totalAccrued.compareTo(BigDecimal.ZERO) > 0;
        }
    }

    @Data
    public static class ReportStatistics {
        private long totalEmployees;
        private BigDecimal totalAccrued;
        private BigDecimal totalDeducted;
        private BigDecimal totalNetSalary;

        public ReportStatistics(long totalEmployees, BigDecimal totalAccrued,
                                BigDecimal totalDeducted, BigDecimal totalNetSalary) {
            this.totalEmployees = totalEmployees;
            this.totalAccrued = totalAccrued;
            this.totalDeducted = totalDeducted;
            this.totalNetSalary = totalNetSalary;
        }
    }
}