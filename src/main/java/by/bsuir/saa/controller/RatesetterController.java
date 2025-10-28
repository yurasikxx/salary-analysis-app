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

    public RatesetterController(PositionService positionService,
                                DepartmentService departmentService,
                                PaymentTypeService paymentTypeService,
                                EmployeeService employeeService,
                                TimesheetService timesheetService,
                                PaymentService paymentService,
                                SalaryCalculationService salaryCalculationService,
                                BonusCalculationService bonusCalculationService) {
        this.positionService = positionService;
        this.departmentService = departmentService;
        this.paymentTypeService = paymentTypeService;
        this.employeeService = employeeService;
        this.timesheetService = timesheetService;
        this.paymentService = paymentService;
        this.salaryCalculationService = salaryCalculationService;
        this.bonusCalculationService = bonusCalculationService;
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

                        boolean hasCalculation = paymentService.getEmployeePayments(employee, month, year).stream()
                                .anyMatch(p -> "ОКЛ".equals(p.getPaymentType().getCode()));
                        info.setHasCalculation(hasCalculation);

                        if (hasCalculation) {
                            Optional<Payment> salaryPayment = paymentService.getEmployeePayments(employee, month, year).stream()
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

            PaymentType salaryType = paymentTypeService.getPaymentTypeByCode("ОКЛ")
                    .orElseThrow(() -> new RuntimeException("Тип оплаты 'ОКЛ' не найден в системе"));

            Timesheet timesheet = timesheetService.getTimesheet(employee, month, year)
                    .orElseThrow(() -> new RuntimeException("Табель не найден"));

            if (timesheet.getStatus() != Timesheet.TimesheetStatus.CONFIRMED) {
                throw new RuntimeException("Табель не подтвержден");
            }

            boolean alreadyCalculated = paymentService.getEmployeePayments(employee, month, year).stream()
                    .anyMatch(p -> "ОКЛ".equals(p.getPaymentType().getCode()));

            if (alreadyCalculated) {
                throw new RuntimeException("Основная зарплата уже была рассчитана для этого периода");
            }

            salaryCalculationService.calculateAndSaveBaseSalary(employee, month, year);

            redirectAttributes.addFlashAttribute("success",
                    "Основная зарплата для " + employee.getFullName() + " рассчитана успешно");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
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
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/ratesetter/salary-calculation?month=%d&year=%d".formatted(month, year);
    }

    @GetMapping("/bonuses")
    public String bonusesPage(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().monthValue}") Integer month,
                              @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") Integer year,
                              @RequestParam(required = false) Integer departmentId,
                              Model model) {

        model.addAttribute("title", "Начисление надбавок и премий");
        model.addAttribute("icon", "bi-award");
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("russianMonth", MonthUtil.getRussianMonthName(month));

        List<Employee> employees = employeeService.getActiveEmployees();
        List<Department> departments = departmentService.getAllDepartments();

        List<PaymentType> bonusTypes = paymentTypeService.getAccrualTypes().stream()
                .filter(pt -> !"ОКЛ".equals(pt.getCode()) &&
                        !"ИТР".equals(pt.getCode()) &&
                        !"СТАЖ".equals(pt.getCode()))
                .toList();

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

                    List<Payment> bonuses = allPayments.stream()
                            .filter(p -> !"ОКЛ".equals(p.getPaymentType().getCode()) &&
                                    "accrual".equals(p.getPaymentType().getCategory()))
                            .toList();

                    info.setExistingBonuses(bonuses);
                    info.setTotalBonuses(bonuses.stream()
                            .map(Payment::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add));

                    boolean hasBaseSalary = allPayments.stream()
                            .anyMatch(p -> "ОКЛ".equals(p.getPaymentType().getCode()));
                    info.setHasBaseSalary(hasBaseSalary);

                    info.setSeniorityYears(bonusCalculationService.getEmployeeSeniority(employee));
                    info.setSeniorityPercentage(bonusCalculationService.getSeniorityPercentage(employee));

                    boolean hasItrBonus = allPayments.stream()
                            .anyMatch(p -> "ИТР".equals(p.getPaymentType().getCode()));
                    boolean hasSeniorityBonus = allPayments.stream()
                            .anyMatch(p -> "СТАЖ".equals(p.getPaymentType().getCode()));

                    info.setHasItrBonus(hasItrBonus);
                    info.setHasSeniorityBonus(hasSeniorityBonus);

                    return info;
                })
                .toList();

        long employeesWithBonuses = bonusInfos.stream()
                .filter(EmployeeBonusInfo::hasBonuses)
                .count();

        BigDecimal totalBonusesAmount = bonusInfos.stream()
                .map(EmployeeBonusInfo::getTotalBonuses)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long employeesWithoutAutomaticBonuses = bonusInfos.stream()
                .filter(info -> info.hasBaseSalary && (!info.hasItrBonus || !info.hasSeniorityBonus))
                .count();

        model.addAttribute("bonusInfos", bonusInfos);
        model.addAttribute("departments", departments);
        model.addAttribute("bonusTypes", bonusTypes);
        model.addAttribute("totalEmployees", employees.size());
        model.addAttribute("employeesWithBonuses", employeesWithBonuses);
        model.addAttribute("totalBonusesAmount", totalBonusesAmount);
        model.addAttribute("employeesWithoutAutomaticBonuses", employeesWithoutAutomaticBonuses);

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
                .filter(pt -> !"ОКЛ".equals(pt.getCode()) &&
                        !"ИТР".equals(pt.getCode()) &&
                        !"СТАЖ".equals(pt.getCode()))
                .toList();

        model.addAttribute("title", "Добавить надбавку");
        model.addAttribute("icon", "bi-plus-circle");
        model.addAttribute("employee", employee);
        model.addAttribute("bonusTypes", bonusTypes);
        model.addAttribute("month", month);
        model.addAttribute("year", year);

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

            PaymentType paymentType = paymentTypeService.getPaymentTypeById(paymentTypeId)
                    .orElseThrow(() -> new RuntimeException("Тип начисления не найден"));

            if ("ИТР".equals(paymentType.getCode()) || "СТАЖ".equals(paymentType.getCode())) {
                throw new RuntimeException("Данный тип надбавки рассчитывается автоматически");
            }

            if (!"accrual".equals(paymentType.getCategory())) {
                throw new RuntimeException("Можно начислять только виды accrual");
            }

            boolean alreadyExists = paymentService.getEmployeePayments(employee, month, year).stream()
                    .anyMatch(p -> p.getPaymentType().getId().equals(paymentTypeId));

            if (alreadyExists) {
                throw new RuntimeException("Надбавка данного типа уже была начислена за этот период");
            }

            paymentService.createPayment(employee, month, year, paymentType, amount, description);

            redirectAttributes.addFlashAttribute("success",
                    "Надбавка '" + paymentType.getName() + "' на сумму " + amount + " руб. начислена");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
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

            bonusCalculationService.calculateAllAutomaticBonuses(employee, month, year);

            redirectAttributes.addFlashAttribute("success",
                    "Автоматические надбавки для " + employee.getFullName() + " рассчитаны успешно");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
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
            if ("ОКЛ".equals(paymentCode) || "ИТР".equals(paymentCode) || "СТАЖ".equals(paymentCode)) {
                throw new RuntimeException("Нельзя удалить автоматическую надбавку или оклад");
            }

            paymentService.deletePayment(id);
            redirectAttributes.addFlashAttribute("success", "Надбавка удалена");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
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

    @Data
    public static class EmployeeSalaryInfo {
        private Employee employee;
        private boolean hasConfirmedTimesheet;
        private boolean hasCalculation;
        private BigDecimal actualHours;
        private BigDecimal calculatedSalary;
        private boolean hoursMetStandard;

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
    }

    @Data
    public static class EmployeeBonusInfo {
        private Employee employee;
        private List<Payment> existingBonuses;
        private BigDecimal totalBonuses;
        private Long seniorityYears;
        private BigDecimal seniorityPercentage;
        private boolean hasItrBonus;
        private boolean hasSeniorityBonus;
        private boolean hasBaseSalary;

        public boolean hasBonuses() {
            return existingBonuses != null && !existingBonuses.isEmpty();
        }

        public boolean canCalculateAutomaticBonuses() {
            return hasBaseSalary && (!hasItrBonus || !hasSeniorityBonus);
        }

        public String getSeniorityBadgeClass() {
            if (seniorityYears >= 10) return "bg-danger";
            if (seniorityYears >= 3) return "bg-warning";
            if (seniorityYears >= 1) return "bg-info";
            return "bg-secondary";
        }

        public String getSeniorityText() {
            if (seniorityYears >= 10) return "25%";
            if (seniorityYears >= 3) return "15%";
            if (seniorityYears >= 1) return "5%";
            return "0%";
        }
    }
}