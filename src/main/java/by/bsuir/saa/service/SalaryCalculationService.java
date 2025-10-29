package by.bsuir.saa.service;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.repository.PaymentRepository;
import by.bsuir.saa.repository.TimesheetRepository;
import by.bsuir.saa.util.WorkingHoursCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SalaryCalculationService {

    private final TimesheetRepository timesheetRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTypeService paymentTypeService;
    private final EmployeeService employeeService;
    private final WorkingHoursCalculator workingHoursCalculator;
    private final PaymentService paymentService;

    public SalaryCalculationService(TimesheetRepository timesheetRepository,
                                    PaymentRepository paymentRepository,
                                    PaymentTypeService paymentTypeService,
                                    EmployeeService employeeService,
                                    WorkingHoursCalculator workingHoursCalculator,
                                    PaymentService paymentService) {
        this.timesheetRepository = timesheetRepository;
        this.paymentRepository = paymentRepository;
        this.paymentTypeService = paymentTypeService;
        this.employeeService = employeeService;
        this.workingHoursCalculator = workingHoursCalculator;
        this.paymentService = paymentService;
    }

    public BigDecimal calculateBaseSalary(Employee employee, Integer month, Integer year) {
        Timesheet timesheet = getConfirmedTimesheet(employee, month, year);
        BigDecimal totalHours = timesheet.getTotalHours();
        BigDecimal baseSalaryRate = employee.getPosition().getBaseSalary();

        int standardMonthlyHours = getStandardMonthlyHours(month, year);
        validateWorkingDays(standardMonthlyHours);

        BigDecimal hourlyRate = baseSalaryRate.divide(
                new BigDecimal(standardMonthlyHours), 4, RoundingMode.HALF_UP);

        return hourlyRate.multiply(totalHours).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public void calculateAndSaveBaseSalary(Employee employee, Integer month, Integer year) {
        if (hasBonuses(employee, month, year)) {
            throw new RuntimeException("Нельзя пересчитывать оклад при наличии надбавок");
        }

        if (hasTaxes(employee, month, year)) {
            throw new RuntimeException("Нельзя пересчитывать оклад после начисления налогов");
        }

        BigDecimal baseSalary = calculateBaseSalary(employee, month, year);
        PaymentType salaryPaymentType = getSalaryPaymentType();

        validateNoExistingPayment(employee, month, year, salaryPaymentType);

        Payment payment = createPayment(employee, month, year, salaryPaymentType, baseSalary);
        paymentRepository.save(payment);
    }

    @Transactional
    public int calculateBatchBaseSalary(Integer month, Integer year) {
        List<Employee> activeEmployees = employeeService.getActiveEmployees();
        int calculatedCount = 0;

        for (Employee employee : activeEmployees) {
            if (canCalculateSalary(employee, month, year)) {
                try {
                    calculateAndSaveBaseSalary(employee, month, year);
                    calculatedCount++;
                } catch (Exception e) {
                    System.err.println("Ошибка расчета для " + employee.getFullName() + ": " + e.getMessage());
                }
            }
        }

        return calculatedCount;
    }

    public long getCalculatedEmployeesCount(Integer month, Integer year) {
        return employeeService.getActiveEmployees().stream()
                .filter(employee -> hasSalaryCalculation(employee, month, year))
                .count();
    }

    public BigDecimal getActualHoursWorked(Employee employee, Integer month, Integer year) {
        return timesheetRepository.findByEmployeeAndMonthAndYear(employee, month, year)
                .filter(timesheet -> timesheet.getStatus() == Timesheet.TimesheetStatus.CONFIRMED)
                .map(Timesheet::getTotalHours)
                .orElse(BigDecimal.ZERO);
    }

    public int getStandardMonthlyHours(Integer month, Integer year) {
        return workingHoursCalculator.calculateWorkingHours(year, month, 8);
    }

    public int getWorkingDaysCount(Integer month, Integer year) {
        return workingHoursCalculator.calculateWorkingDays(year, month);
    }

    private Timesheet getConfirmedTimesheet(Employee employee, Integer month, Integer year) {
        return timesheetRepository.findByEmployeeAndMonthAndYear(employee, month, year)
                .filter(timesheet -> timesheet.getStatus() == Timesheet.TimesheetStatus.CONFIRMED)
                .orElseThrow(() -> new RuntimeException(
                        "Подтвержденный табель не найден для сотрудника: " + employee.getFullName()));
    }

    private PaymentType getSalaryPaymentType() {
        return paymentTypeService.getPaymentTypeByCode("ОКЛ")
                .orElseThrow(() -> new RuntimeException("Тип оплаты ОКЛ не найден"));
    }

    private void validateNoExistingPayment(Employee employee, Integer month, Integer year, PaymentType paymentType) {
        Optional<Payment> existingPayment = paymentRepository.findByEmployeeAndMonthAndYearAndPaymentType(
                employee, month, year, paymentType);

        if (existingPayment.isPresent()) {
            throw new RuntimeException("Основная зарплата уже была начислена для этого периода");
        }
    }

    private void validateWorkingDays(int standardMonthlyHours) {
        if (standardMonthlyHours == 0) {
            throw new RuntimeException("Невозможно рассчитать зарплату: в месяце нет рабочих дней");
        }
    }

    private boolean canCalculateSalary(Employee employee, Integer month, Integer year) {
        Optional<Timesheet> timesheet = timesheetRepository.findByEmployeeAndMonthAndYear(employee, month, year);
        if (timesheet.isEmpty() || timesheet.get().getStatus() != Timesheet.TimesheetStatus.CONFIRMED) {
            return false;
        }

        PaymentType salaryPaymentType = paymentTypeService.getPaymentTypeByCode("ОКЛ")
                .orElseThrow(() -> new RuntimeException("Тип оплаты ОКЛ не найден"));

        Optional<Payment> existingPayment = paymentRepository.findByEmployeeAndMonthAndYearAndPaymentType(
                employee, month, year, salaryPaymentType);

        return existingPayment.isEmpty();
    }

    private boolean hasSalaryCalculation(Employee employee, Integer month, Integer year) {
        return paymentService.getEmployeePayments(employee, month, year).stream()
                .anyMatch(payment -> "ОКЛ".equals(payment.getPaymentType().getCode()));
    }

    private Payment createPayment(Employee employee, Integer month, Integer year,
                                  PaymentType paymentType, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setEmployee(employee);
        payment.setMonth(month);
        payment.setYear(year);
        payment.setPaymentType(paymentType);
        payment.setAmount(amount);
        payment.setDescription(buildPaymentDescription(employee, month, year));

        return payment;
    }

    private String buildPaymentDescription(Employee employee, Integer month, Integer year) {
        BigDecimal actualHours = getActualHoursWorked(employee, month, year);
        int standardHours = getStandardMonthlyHours(month, year);

        return String.format("Основная заработная плата за %d.%d (%s ч. из %d ч.)",
                month, year, actualHours, standardHours);
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