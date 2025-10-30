package by.bsuir.saa.controller;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.service.*;
import by.bsuir.saa.util.MonthUtil;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/ratesetter")
@PreAuthorize("hasRole('RATESETTER')")
public class RatesetterController {

    private final PositionService positionService;
    private final DepartmentService departmentService;
    private final PaymentTypeService paymentTypeService;
    private final EmployeeService employeeService;
    private final TimesheetService timesheetService;
    private final PaymentService paymentService;
    private final SalaryCalculationService salaryCalculationService;
    private final BonusCalculationService bonusCalculationService;
    private final TaxCalculationService taxCalculationService;
    private final FinalSalaryCalculationService finalSalaryCalculationService;

    public RatesetterController(PositionService positionService,
                                DepartmentService departmentService,
                                PaymentTypeService paymentTypeService,
                                EmployeeService employeeService,
                                TimesheetService timesheetService,
                                PaymentService paymentService,
                                SalaryCalculationService salaryCalculationService,
                                BonusCalculationService bonusCalculationService,
                                TaxCalculationService taxCalculationService,
                                FinalSalaryCalculationService finalSalaryCalculationService) {
        this.positionService = positionService;
        this.departmentService = departmentService;
        this.paymentTypeService = paymentTypeService;
        this.employeeService = employeeService;
        this.timesheetService = timesheetService;
        this.paymentService = paymentService;
        this.salaryCalculationService = salaryCalculationService;
        this.bonusCalculationService = bonusCalculationService;
        this.taxCalculationService = taxCalculationService;
        this.finalSalaryCalculationService = finalSalaryCalculationService;
    }

    @Data
    public static class EmployeeSalaryInfo {
        private Employee employee;
        private boolean hasConfirmedTimesheet;
        private boolean hasCalculation;
        private BigDecimal actualHours;
        private BigDecimal calculatedSalary;
        private boolean hoursMetStandard;
        private List<Payment> existingPayments;

        public boolean hasOvertime(int standardHours) {
            return actualHours != null && actualHours.compareTo(new BigDecimal(standardHours)) > 0;
        }

        public String getOvertimeInfo(int standardHours) {
            if (hasOvertime(standardHours)) {
                BigDecimal overtime = actualHours.subtract(new BigDecimal(standardHours));
                return "+" + overtime + " ч. сверх нормы";
            }
            return "В пределах нормы";
        }

        public Payment getSalaryPayment() {
            if (existingPayments != null) {
                return existingPayments.stream()
                        .filter(p -> "ОКЛ".equals(p.getPaymentType().getCode()))
                        .findFirst()
                        .orElse(null);
            }
            return null;
        }
    }

    @Data
    public static class EmployeeBonusInfo {
        private Employee employee;
        private List<Payment> existingBonuses;
        private BigDecimal totalBonuses;
        private Long seniorityYears;
        private String seniorityPercentage;
        private boolean hasSeniorityBonus;
        private boolean hasBaseSalary;
        private boolean hasTaxes;
        private boolean hasFinalSalary;
        private boolean canModifyBonuses;

        public boolean hasBonuses() {
            return existingBonuses != null && !existingBonuses.isEmpty();
        }

        public boolean canCalculateAutomaticBonuses() {
            return hasBaseSalary && !hasSeniorityBonus && seniorityYears >= 1 && canModifyBonuses;
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                            Model model) {

        model.addAttribute("title", "Дашборд нормировщика труда");
        model.addAttribute("icon", "bi-gear");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(month));

        long totalPositions = positionService.getAllPositions().size();
        long totalDepartments = departmentService.getAllDepartments().size();
        long totalPaymentTypes = paymentTypeService.getAllPaymentTypes().size();
        long employeesWithConfirmedTimesheets = timesheetService.getConfirmedTimesheetsCount(month, year);
        long totalActiveEmployees = employeeService.getActiveEmployees().size();
        long employeesCalculated = salaryCalculationService.getCalculatedEmployeesCount(month, year);

        double salaryCalculationRate = totalActiveEmployees > 0 ?
                (employeesCalculated * 100.0 / totalActiveEmployees) : 0;

        double timesheetCompletionRate = totalActiveEmployees > 0 ?
                (employeesWithConfirmedTimesheets * 100.0 / totalActiveEmployees) : 0;

        double readyForCalculationPercent = totalActiveEmployees > 0 ?
                ((employeesWithConfirmedTimesheets - employeesCalculated) * 100.0 / totalActiveEmployees) : 0;
        double waitingTimesheetsPercent = totalActiveEmployees > 0 ?
                ((totalActiveEmployees - employeesWithConfirmedTimesheets) * 100.0 / totalActiveEmployees) : 0;

        model.addAttribute("totalPositions", totalPositions);
        model.addAttribute("totalDepartments", totalDepartments);
        model.addAttribute("totalPaymentTypes", totalPaymentTypes);
        model.addAttribute("employeesWithConfirmedTimesheets", employeesWithConfirmedTimesheets);
        model.addAttribute("totalActiveEmployees", totalActiveEmployees);
        model.addAttribute("employeesCalculated", employeesCalculated);
        model.addAttribute("salaryCalculationRate", Math.round(salaryCalculationRate));
        model.addAttribute("timesheetCompletionRate", Math.round(timesheetCompletionRate));
        model.addAttribute("calculatedPercent", salaryCalculationRate);
        model.addAttribute("readyForCalculationPercent", readyForCalculationPercent);
        model.addAttribute("waitingTimesheetsPercent", waitingTimesheetsPercent);

        return "ratesetter/dashboard";
    }

    @GetMapping("/positions")
    public String positionsPage(Model model) {
        model.addAttribute("title", "Управление должностями");
        model.addAttribute("icon", "bi-person-badge");

        List<Position> positions = positionService.getAllPositions();
        model.addAttribute("positions", positions);

        return "ratesetter/positions";
    }

    @GetMapping("/positions/create")
    public String createPositionForm(Model model) {
        model.addAttribute("title", "Добавить должность");
        model.addAttribute("icon", "bi-person-plus");
        return "ratesetter/create-position.html";
    }

    @PostMapping("/positions")
    public String createPosition(@RequestParam String title,
                                 @RequestParam BigDecimal baseSalary,
                                 RedirectAttributes redirectAttributes) {
        try {
            positionService.createPosition(title, baseSalary);
            redirectAttributes.addFlashAttribute("success", "Должность '" + title + "' успешно создана");
            return "redirect:/ratesetter/positions";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/ratesetter/positions/create";
        }
    }

    @GetMapping("/positions/{id}/edit")
    public String editPositionForm(@PathVariable Integer id, Model model) {
        Position position = positionService.getPositionById(id)
                .orElseThrow(() -> new RuntimeException("Должность не найдена"));

        model.addAttribute("title", "Редактирование должности");
        model.addAttribute("icon", "bi-pencil");
        model.addAttribute("position", position);

        return "ratesetter/edit-position";
    }

    @PostMapping("/positions/{id}")
    public String updatePosition(@PathVariable Integer id,
                                 @RequestParam String title,
                                 @RequestParam BigDecimal baseSalary,
                                 RedirectAttributes redirectAttributes) {
        try {
            positionService.updatePosition(id, title, baseSalary);
            redirectAttributes.addFlashAttribute("success", "Должность обновлена");
            return "redirect:/ratesetter/positions";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/ratesetter/positions/%d/edit".formatted(id);
        }
    }

    @PostMapping("/positions/{id}/delete")
    public String deletePosition(@PathVariable Integer id,
                                 RedirectAttributes redirectAttributes) {
        try {
            positionService.deletePosition(id);
            redirectAttributes.addFlashAttribute("success", "Должность удалена");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ratesetter/positions";
    }

    @GetMapping("/departments")
    public String departmentsPage(Model model) {
        model.addAttribute("title", "Управление подразделениями");
        model.addAttribute("icon", "bi-building");

        List<Department> departments = departmentService.getAllDepartments();
        model.addAttribute("departments", departments);

        return "ratesetter/departments";
    }

    @GetMapping("/departments/create")
    public String createDepartmentForm(Model model) {
        model.addAttribute("title", "Добавить подразделение");
        model.addAttribute("icon", "bi-plus-circle");
        return "ratesetter/create-department";
    }

    @PostMapping("/departments")
    public String createDepartment(@RequestParam String name,
                                   RedirectAttributes redirectAttributes) {
        try {
            departmentService.createDepartment(name);
            redirectAttributes.addFlashAttribute("success", "Подразделение '" + name + "' успешно создано");
            return "redirect:/ratesetter/departments";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("name", name);
            return "redirect:/ratesetter/departments/create";
        }
    }

    @GetMapping("/departments/{id}/edit")
    public String editDepartmentForm(@PathVariable Integer id, Model model) {
        Department department = departmentService.getDepartmentById(id)
                .orElseThrow(() -> new RuntimeException("Подразделение не найдено"));

        model.addAttribute("title", "Редактирование подразделения");
        model.addAttribute("icon", "bi-pencil");
        model.addAttribute("department", department);

        return "ratesetter/edit-department";
    }

    @PostMapping("/departments/{id}")
    public String updateDepartment(@PathVariable Integer id,
                                   @RequestParam String name,
                                   RedirectAttributes redirectAttributes) {
        try {
            departmentService.updateDepartment(id, name);
            redirectAttributes.addFlashAttribute("success", "Подразделение обновлено");
            return "redirect:/ratesetter/departments";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("name", name);
            return "redirect:/ratesetter/departments/%d/edit".formatted(id);
        }
    }

    @PostMapping("/departments/{id}/delete")
    public String deleteDepartment(@PathVariable Integer id,
                                   RedirectAttributes redirectAttributes) {
        try {
            Department department = departmentService.getDepartmentById(id)
                    .orElseThrow(() -> new RuntimeException("Подразделение не найдено"));

            departmentService.deleteDepartment(id);
            redirectAttributes.addFlashAttribute("success", "Подразделение '" + department.getName() + "' удалено");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ratesetter/departments";
    }

    @GetMapping("/payment-types")
    public String paymentTypesPage(Model model) {
        model.addAttribute("title", "Управление видами оплаты");
        model.addAttribute("icon", "bi-cash-coin");

        List<PaymentType> paymentTypes = paymentTypeService.getAllPaymentTypes();
        List<PaymentType> accrualTypes = paymentTypeService.getAccrualTypes();
        List<PaymentType> deductionTypes = paymentTypeService.getDeductionTypes();

        model.addAttribute("paymentTypes", paymentTypes);
        model.addAttribute("accrualTypes", accrualTypes);
        model.addAttribute("deductionTypes", deductionTypes);

        return "ratesetter/payment-types";
    }

    @GetMapping("/payment-types/create")
    public String createPaymentTypeForm(Model model) {
        model.addAttribute("title", "Добавить вид оплаты");
        model.addAttribute("icon", "bi-plus-circle");
        return "ratesetter/create-payment-type";
    }

    @PostMapping("/payment-types")
    public String createPaymentType(@RequestParam String code,
                                    @RequestParam String name,
                                    @RequestParam String category,
                                    @RequestParam(required = false) String description,
                                    @RequestParam(required = false) String formula,
                                    RedirectAttributes redirectAttributes,
                                    Model model) {
        try {
            paymentTypeService.createPaymentType(code, name, category, description, formula);
            redirectAttributes.addFlashAttribute("success", "Вид оплаты '" + name + "' успешно создан");
            return "redirect:/ratesetter/payment-types";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("code", code);
            redirectAttributes.addFlashAttribute("name", name);
            redirectAttributes.addFlashAttribute("category", category);
            redirectAttributes.addFlashAttribute("description", description);
            redirectAttributes.addFlashAttribute("formula", formula);

            String errorMessage = e.getMessage();
            if (errorMessage.contains("Код может содержать только")) {
                redirectAttributes.addFlashAttribute("codeError", errorMessage);
            } else if (errorMessage.contains("Тип оплаты с кодом")) {
                redirectAttributes.addFlashAttribute("codeError", errorMessage);
            } else if (errorMessage.contains("Название не может")) {
                redirectAttributes.addFlashAttribute("nameError", errorMessage);
            } else if (errorMessage.contains("Категория должна быть")) {
                redirectAttributes.addFlashAttribute("categoryError", errorMessage);
            } else {
                redirectAttributes.addFlashAttribute("error", errorMessage);
            }

            return "redirect:/ratesetter/payment-types/create";
        }
    }

    @GetMapping("/payment-types/{id}/edit")
    public String editPaymentTypeForm(@PathVariable Integer id, Model model) {
        PaymentType paymentType = paymentTypeService.getPaymentTypeById(id)
                .orElseThrow(() -> new RuntimeException("Вид оплаты не найден"));

        model.addAttribute("title", "Редактирование вида оплаты");
        model.addAttribute("icon", "bi-pencil");
        model.addAttribute("paymentType", paymentType);

        return "ratesetter/edit-payment-type";
    }

    @PostMapping("/payment-types/{id}")
    public String updatePaymentType(@PathVariable Integer id,
                                    @RequestParam String code,
                                    @RequestParam String name,
                                    @RequestParam String category,
                                    @RequestParam(required = false) String description,
                                    @RequestParam(required = false) String formula,
                                    RedirectAttributes redirectAttributes) {
        try {
            paymentTypeService.updatePaymentType(id, code, name, category, description, formula);
            redirectAttributes.addFlashAttribute("success", "Вид оплаты обновлен");
            return "redirect:/ratesetter/payment-types";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("code", code);
            redirectAttributes.addFlashAttribute("name", name);
            redirectAttributes.addFlashAttribute("category", category);
            redirectAttributes.addFlashAttribute("description", description);
            redirectAttributes.addFlashAttribute("formula", formula);

            String errorMessage = e.getMessage();
            if (errorMessage.contains("Код может содержать только") || errorMessage.contains("Тип оплаты с кодом")) {
                redirectAttributes.addFlashAttribute("codeError", errorMessage);
            } else if (errorMessage.contains("Название не может")) {
                redirectAttributes.addFlashAttribute("nameError", errorMessage);
            } else if (errorMessage.contains("Категория должна быть")) {
                redirectAttributes.addFlashAttribute("categoryError", errorMessage);
            } else {
                redirectAttributes.addFlashAttribute("error", errorMessage);
            }

            return "redirect:/ratesetter/payment-types/%d/edit".formatted(id);
        }
    }

    @PostMapping("/payment-types/{id}/delete")
    public String deletePaymentType(@PathVariable Integer id,
                                    RedirectAttributes redirectAttributes) {
        try {
            PaymentType paymentType = paymentTypeService.getPaymentTypeById(id)
                    .orElseThrow(() -> new RuntimeException("Вид оплаты не найден"));

            paymentTypeService.deletePaymentType(id);
            redirectAttributes.addFlashAttribute("success", "Вид оплаты '" + paymentType.getName() + "' удален");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ratesetter/payment-types";
    }

    @GetMapping("/salary-calculation")
    public String salaryCalculationPage(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                                        @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                                        @RequestParam(required = false) Integer departmentId,
                                        Model model) {

        model.addAttribute("title", "Расчет основной заработной платы");
        model.addAttribute("icon", "bi-calculator");
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

        int standardHours = salaryCalculationService.getStandardMonthlyHours(month, year);
        int workingDays = salaryCalculationService.getWorkingDaysCount(month, year);
        model.addAttribute("standardHours", standardHours);
        model.addAttribute("workingDays", workingDays);

        List<EmployeeSalaryInfo> salaryInfos = employees.stream()
                .map(employee -> {
                    EmployeeSalaryInfo info = new EmployeeSalaryInfo();
                    info.setEmployee(employee);

                    Optional<Timesheet> timesheet = timesheetService.getTimesheet(employee, month, year);
                    info.setHasConfirmedTimesheet(timesheet.isPresent() &&
                            timesheet.get().getStatus() == Timesheet.TimesheetStatus.CONFIRMED);

                    if (info.isHasConfirmedTimesheet()) {
                        info.setActualHours(salaryCalculationService.getActualHoursWorked(employee, month, year));

                        boolean hoursMetStandard = info.getActualHours().compareTo(new BigDecimal(standardHours)) >= 0;
                        info.setHoursMetStandard(hoursMetStandard);

                        List<Payment> employeePayments = paymentService.getEmployeePayments(employee, month, year);
                        info.setExistingPayments(employeePayments);

                        boolean hasCalculation = employeePayments.stream()
                                .anyMatch(p -> "ОКЛ".equals(p.getPaymentType().getCode()));
                        info.setHasCalculation(hasCalculation);

                        if (hasCalculation) {
                            Optional<Payment> salaryPayment = employeePayments.stream()
                                    .filter(p -> "ОКЛ".equals(p.getPaymentType().getCode()))
                                    .findFirst();
                            salaryPayment.ifPresent(payment -> info.setCalculatedSalary(payment.getAmount()));
                        }
                    }

                    return info;
                })
                .toList();

        long employeesWithTimesheets = salaryInfos.stream()
                .filter(EmployeeSalaryInfo::isHasConfirmedTimesheet)
                .count();
        long employeesCalculated = salaryInfos.stream()
                .filter(EmployeeSalaryInfo::isHasCalculation)
                .count();

        model.addAttribute("salaryInfos", salaryInfos);
        model.addAttribute("departments", departments);
        model.addAttribute("employeesWithTimesheets", employeesWithTimesheets);
        model.addAttribute("employeesCalculated", employeesCalculated);
        model.addAttribute("totalEmployees", employees.size());

        return "ratesetter/salary-calculation";
    }

    @PostMapping("/salary-calculation/calculate")
    public String calculateBaseSalary(@RequestParam Integer employeeId,
                                      @RequestParam Integer month,
                                      @RequestParam Integer year,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            salaryCalculationService.calculateAndSaveBaseSalary(employee, month, year);

            redirectAttributes.addFlashAttribute("success",
                    "Основная зарплата для " + employee.getFullName() + " рассчитана успешно");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка расчета зарплаты: " + e.getMessage());
        }

        return "redirect:/ratesetter/salary-calculation?month=%d&year=%d".formatted(month, year);
    }

    @PostMapping("/salary-calculation/calculate-batch")
    public String calculateBatchBaseSalary(@RequestParam Integer month,
                                           @RequestParam Integer year,
                                           Authentication authentication,
                                           RedirectAttributes redirectAttributes) {
        try {
            int calculatedCount = salaryCalculationService.calculateBatchBaseSalary(month, year);

            redirectAttributes.addFlashAttribute("success",
                    "Основная зарплата рассчитана для " + calculatedCount + " сотрудников");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка пакетного расчета зарплаты: " + e.getMessage());
        }

        return "redirect:/ratesetter/salary-calculation?month=%d&year=%d".formatted(month, year);
    }

    @PostMapping("/salary-calculation/{id}/recalculate")
    public String recalculateBaseSalary(@PathVariable Integer id,
                                        @RequestParam Integer month,
                                        @RequestParam Integer year,
                                        RedirectAttributes redirectAttributes) {
        try {
            Payment payment = paymentService.getPaymentById(id)
                    .orElseThrow(() -> new RuntimeException("Платеж не найден"));

            if (!"ОКЛ".equals(payment.getPaymentType().getCode())) {
                throw new RuntimeException("Можно пересчитывать только платежи оклада");
            }

            salaryCalculationService.recalculateBaseSalary(payment.getEmployee(), month, year);
            redirectAttributes.addFlashAttribute("success",
                    "Оклад для " + payment.getEmployee().getFullName() + " успешно пересчитан");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка пересчета оклада: " + e.getMessage());
        }

        return "redirect:/ratesetter/salary-calculation?month=" + month + "&year=" + year;
    }

    @PostMapping("/salary-calculation/{id}/delete")
    public String deleteBaseSalary(@PathVariable Integer id,
                                   @RequestParam Integer month,
                                   @RequestParam Integer year,
                                   RedirectAttributes redirectAttributes) {
        try {
            Payment payment = paymentService.getPaymentById(id)
                    .orElseThrow(() -> new RuntimeException("Платеж не найден"));

            if (!"ОКЛ".equals(payment.getPaymentType().getCode())) {
                throw new RuntimeException("Можно удалять только платежи оклада");
            }

            salaryCalculationService.deleteBaseSalary(payment.getEmployee(), month, year);
            redirectAttributes.addFlashAttribute("success",
                    "Оклад для " + payment.getEmployee().getFullName() + " успешно удален");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка удаления оклада: " + e.getMessage());
        }

        return "redirect:/ratesetter/salary-calculation?month=" + month + "&year=" + year;
    }

    @GetMapping("/bonuses")
    public String bonusesPage(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                              @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                              @RequestParam(required = false) Integer departmentId,
                              Model model) {

        model.addAttribute("title", "Надбавки и премии");
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

        List<PaymentType> bonusTypes = paymentTypeService.getAccrualTypes().stream()
                .filter(pt -> "ПХД".equals(pt.getCode()) || "ПСС".equals(pt.getCode()))
                .toList();

        List<EmployeeBonusInfo> bonusInfos = employees.stream()
                .map(employee -> {
                    EmployeeBonusInfo info = new EmployeeBonusInfo();
                    info.setEmployee(employee);

                    List<Payment> allPayments = paymentService.getEmployeePayments(employee, month, year);

                    List<Payment> ratesetterBonuses = allPayments.stream()
                            .filter(p -> "ПХД".equals(p.getPaymentType().getCode()) ||
                                    "ПСС".equals(p.getPaymentType().getCode()) ||
                                    "СТАЖ".equals(p.getPaymentType().getCode()))
                            .toList();

                    info.setExistingBonuses(ratesetterBonuses);
                    info.setTotalBonuses(ratesetterBonuses.stream()
                            .map(Payment::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add));

                    boolean hasBaseSalary = allPayments.stream()
                            .anyMatch(p -> "ОКЛ".equals(p.getPaymentType().getCode()));
                    info.setHasBaseSalary(hasBaseSalary);

                    info.setSeniorityYears(bonusCalculationService.getEmployeeSeniority(employee));
                    info.setSeniorityPercentage(bonusCalculationService.getSeniorityPercentageText(employee));

                    boolean hasSeniorityBonus = allPayments.stream()
                            .anyMatch(p -> "СТАЖ".equals(p.getPaymentType().getCode()));

                    info.setHasSeniorityBonus(hasSeniorityBonus);

                    boolean hasTaxes = taxCalculationService.hasTaxesCalculated(employee, month, year);
                    boolean hasFinalSalary = finalSalaryCalculationService.isFinalSalaryCalculated(employee, month, year);
                    info.setHasTaxes(hasTaxes);
                    info.setHasFinalSalary(hasFinalSalary);
                    info.setCanModifyBonuses(!hasTaxes && !hasFinalSalary);

                    return info;
                })
                .toList();

        long employeesWithBonuses = bonusInfos.stream()
                .filter(EmployeeBonusInfo::hasBonuses)
                .count();

        BigDecimal totalBonusesAmount = bonusInfos.stream()
                .map(EmployeeBonusInfo::getTotalBonuses)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long employeesWithoutSeniorityBonus = bonusInfos.stream()
                .filter(info -> info.hasBaseSalary && !info.hasSeniorityBonus && info.seniorityYears >= 1)
                .count();

        model.addAttribute("bonusInfos", bonusInfos);
        model.addAttribute("departments", departments);
        model.addAttribute("bonusTypes", bonusTypes);
        model.addAttribute("totalEmployees", employees.size());
        model.addAttribute("employeesWithBonuses", employeesWithBonuses);
        model.addAttribute("totalBonusesAmount", totalBonusesAmount);
        model.addAttribute("employeesWithoutSeniorityBonus", employeesWithoutSeniorityBonus);

        return "ratesetter/bonuses";
    }

    @GetMapping("/bonuses/add")
    public String addBonusForm(@RequestParam Integer employeeId,
                               @RequestParam Integer month,
                               @RequestParam Integer year,
                               Model model) {

        Employee employee = employeeService.getEmployeeById(employeeId)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        if (!bonusCalculationService.isBaseSalaryCalculated(employee, month, year)) {
            throw new RuntimeException("Нельзя добавлять надбавки до расчета основной зарплаты");
        }

        List<PaymentType> bonusTypes = paymentTypeService.getAccrualTypes().stream()
                .filter(pt -> "ПХД".equals(pt.getCode()) || "ПСС".equals(pt.getCode()))
                .toList();

        model.addAttribute("title", "Добавить надбавку");
        model.addAttribute("icon", "bi-plus-circle");
        model.addAttribute("employee", employee);
        model.addAttribute("bonusTypes", bonusTypes);
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(month));

        return "ratesetter/add-bonus";
    }

    @PostMapping("/bonuses")
    public String addBonus(@RequestParam Integer employeeId,
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
                throw new RuntimeException("Нельзя добавлять надбавки до расчета основной зарплаты");
            }

            if (taxCalculationService.hasTaxesCalculated(employee, month, year)) {
                throw new RuntimeException("Нельзя добавлять начисления после расчета налогов");
            }

            PaymentType paymentType = paymentTypeService.getPaymentTypeById(paymentTypeId)
                    .orElseThrow(() -> new RuntimeException("Тип начисления не найден"));

            if (!"ПХД".equals(paymentType.getCode()) && !"ПСС".equals(paymentType.getCode())) {
                throw new RuntimeException("Нормировщик может начислять только ПХД и ПСС");
            }

            boolean alreadyExists = paymentService.getEmployeePayments(employee, month, year).stream()
                    .anyMatch(p -> p.getPaymentType().getId().equals(paymentTypeId));

            if (alreadyExists) {
                throw new RuntimeException("Надбавка '" + paymentType.getName() + "' уже начислена за этот период");
            }

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Сумма надбавки должна быть положительной");
            }

            paymentService.createPayment(employee, month, year, paymentType, amount, description);

            redirectAttributes.addFlashAttribute("success",
                    "Надбавка '" + paymentType.getName() + "' на сумму " + amount + " руб. успешно начислена сотруднику " + employee.getFullName());

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка начисления надбавки: " + e.getMessage());
        }

        return "redirect:/ratesetter/bonuses?month=%d&year=%d".formatted(month, year);
    }

    @PostMapping("/bonuses/calculate-automatic")
    public String calculateAutomaticBonuses(@RequestParam Integer employeeId,
                                            @RequestParam Integer month,
                                            @RequestParam Integer year,
                                            RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

            if (taxCalculationService.hasTaxesCalculated(employee, month, year)) {
                throw new RuntimeException("Нельзя рассчитывать надбавки после начисления налогов");
            }

            if (finalSalaryCalculationService.isFinalSalaryCalculated(employee, month, year)) {
                throw new RuntimeException("Нельзя рассчитывать надбавки после расчета финальной зарплаты");
            }

            bonusCalculationService.calculateAllAutomaticBonuses(employee, month, year);

            redirectAttributes.addFlashAttribute("success",
                    "Автоматические надбавки для " + employee.getFullName() + " рассчитаны успешно");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка расчета автоматических надбавок: " + e.getMessage());
        }

        return "redirect:/ratesetter/bonuses?month=%d&year=%d".formatted(month, year);
    }

    @PostMapping("/bonuses/calculate-automatic-batch")
    public String calculateAutomaticBonusesBatch(@RequestParam Integer month,
                                                 @RequestParam Integer year,
                                                 RedirectAttributes redirectAttributes) {
        try {
            List<Employee> employees = employeeService.getActiveEmployees();
            int calculatedCount = 0;

            for (Employee employee : employees) {
                if (bonusCalculationService.isBaseSalaryCalculated(employee, month, year) &&
                        bonusCalculationService.getEmployeeSeniority(employee) >= 1 &&
                        paymentService.getEmployeePayments(employee, month, year).stream()
                                .noneMatch(p -> "СТАЖ".equals(p.getPaymentType().getCode())) &&
                        !taxCalculationService.hasTaxesCalculated(employee, month, year) &&
                        !finalSalaryCalculationService.isFinalSalaryCalculated(employee, month, year)) {
                    try {
                        bonusCalculationService.calculateSeniorityBonus(employee, month, year);
                        calculatedCount++;
                    } catch (Exception ignored) {
                    }
                }
            }

            redirectAttributes.addFlashAttribute("success",
                    "Надбавка за стаж рассчитана для " + calculatedCount + " сотрудников");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка пакетного расчета надбавок: " + e.getMessage());
        }

        return "redirect:/ratesetter/bonuses?month=%d&year=%d".formatted(month, year);
    }

    @PostMapping("/bonuses/{id}/delete")
    public String deleteBonus(@PathVariable Integer id,
                              @RequestParam Integer month,
                              @RequestParam Integer year,
                              RedirectAttributes redirectAttributes) {
        try {
            Payment payment = paymentService.getPaymentById(id)
                    .orElseThrow(() -> new RuntimeException("Надбавка не найдена"));

            String paymentCode = payment.getPaymentType().getCode();

            if (!"ПХД".equals(paymentCode) && !"ПСС".equals(paymentCode) && !"СТАЖ".equals(paymentCode)) {
                throw new RuntimeException("Нормировщик может удалять только надбавки ПХД, ПСС и СТАЖ");
            }

            bonusCalculationService.deleteBonus(id, month, year);
            redirectAttributes.addFlashAttribute("success",
                    "Надбавка '" + payment.getPaymentType().getName() + "' для " + payment.getEmployee().getFullName() + " успешно удалена");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка удаления надбавки: " + e.getMessage());
        }

        return "redirect:/ratesetter/bonuses?month=%d&year=%d".formatted(month, year);
    }

    @GetMapping("/debug-payments")
    @ResponseBody
    public String debugPayments(@RequestParam Integer employeeId,
                                @RequestParam Integer month,
                                @RequestParam Integer year) {
        Employee employee = employeeService.getEmployeeById(employeeId)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        List<Payment> payments = paymentService.getEmployeePayments(employee, month, year);

        StringBuilder result = new StringBuilder();
        result.append("Платежи для: ").append(employee.getFullName()).append("<br>");
        result.append("Месяц: ").append(month).append(", Год: ").append(year).append("<br>");
        result.append("Всего платежей: ").append(payments.size()).append("<br><br>");

        for (Payment payment : payments) {
            result.append("Платеж: ")
                    .append(payment.getPaymentType().getCode())
                    .append(" - ")
                    .append(payment.getPaymentType().getName())
                    .append(" - ")
                    .append(payment.getAmount())
                    .append(" руб.<br>");
        }

        boolean hasOklad = payments.stream()
                .anyMatch(p -> "ОКЛ".equals(p.getPaymentType().getCode()));
        result.append("<br>Оклад найден: ").append(hasOklad);

        return result.toString();
    }

    private boolean hasTaxes(Employee employee, Integer month, Integer year) {
        List<Payment> payments = paymentService.getEmployeePayments(employee, month, year);
        return payments.stream()
                .anyMatch(p -> "deduction".equals(p.getPaymentType().getCategory()) &&
                        ("ПН".equals(p.getPaymentType().getCode()) ||
                                "ФСЗН".equals(p.getPaymentType().getCode())));
    }

    private boolean hasBonuses(Employee employee, Integer month, Integer year) {
        List<Payment> payments = paymentService.getEmployeePayments(employee, month, year);
        return payments.stream()
                .anyMatch(p -> "accrual".equals(p.getPaymentType().getCategory()) &&
                        !"ОКЛ".equals(p.getPaymentType().getCode()));
    }
}