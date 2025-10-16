package by.bsuir.saa.service;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.repository.PaymentRepository;
import by.bsuir.saa.repository.PaymentTypeRepository;
import by.bsuir.saa.repository.SalaryPaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class SalaryCalculationService {

    private final PaymentRepository paymentRepository;
    private final SalaryPaymentRepository salaryPaymentRepository;
    private final PaymentTypeRepository paymentTypeRepository;
    private final TimesheetService timesheetService;

    public SalaryCalculationService(PaymentRepository paymentRepository,
                                    SalaryPaymentRepository salaryPaymentRepository,
                                    PaymentTypeRepository paymentTypeRepository,
                                    TimesheetService timesheetService) {
        this.paymentRepository = paymentRepository;
        this.salaryPaymentRepository = salaryPaymentRepository;
        this.paymentTypeRepository = paymentTypeRepository;
        this.timesheetService = timesheetService;
    }

    public void calculateBasicSalary(Employee employee, Integer month, Integer year) {
        PaymentType salaryType = paymentTypeRepository.findByCode("SALARY")
                .orElseThrow(() -> new RuntimeException("Тип оплаты 'Оклад' не найден"));

        Optional<Payment> existingSalary = paymentRepository.findByEmployeeAndMonthAndYearAndPaymentType(
                employee, month, year, salaryType);

        if (existingSalary.isPresent()) {
            throw new RuntimeException("Основная зарплата уже рассчитана для этого периода");
        }

        Optional<Timesheet> timesheet = timesheetService.getTimesheet(employee, month, year);
        if (timesheet.isEmpty()) {
            throw new RuntimeException("Табель не найден для сотрудника: " + employee.getFullName());
        }

        if (timesheet.get().getStatus() != Timesheet.TimesheetStatus.CONFIRMED) {
            throw new RuntimeException("Табель не подтвержден. Расчет невозможен.");
        }

        BigDecimal workedHours = timesheet.get().getTotalHours();
        BigDecimal monthlyHours = BigDecimal.valueOf(160);
        BigDecimal baseSalary = employee.getPosition().getBaseSalary();

        BigDecimal salaryAmount = baseSalary
                .divide(monthlyHours, 2, RoundingMode.HALF_UP)
                .multiply(workedHours);

        Payment payment = new Payment();
        payment.setEmployee(employee);
        payment.setMonth(month);
        payment.setYear(year);
        payment.setPaymentType(salaryType);
        payment.setAmount(salaryAmount);
        payment.setDescription("Основной оклад за " + month + "/" + year + " (" + workedHours + " часов)");

        paymentRepository.save(payment);
    }

    public Payment addBonus(Employee employee, Integer month, Integer year,
                            PaymentType paymentType, BigDecimal amount, String description) {

        if (!"accrual".equals(paymentType.getCategory())) {
            throw new RuntimeException("Можно добавлять только начисления (accrual)");
        }

        PaymentType salaryType = paymentTypeRepository.findByCode("SALARY")
                .orElseThrow(() -> new RuntimeException("Тип оплаты 'Оклад' не найден"));

        Optional<Payment> basicSalary = paymentRepository.findByEmployeeAndMonthAndYearAndPaymentType(
                employee, month, year, salaryType);

        if (basicSalary.isEmpty()) {
            throw new RuntimeException("Сначала необходимо рассчитать основную зарплату");
        }

        Payment payment = new Payment();
        payment.setEmployee(employee);
        payment.setMonth(month);
        payment.setYear(year);
        payment.setPaymentType(paymentType);
        payment.setAmount(amount);
        payment.setDescription(description != null ? description : paymentType.getName());

        return paymentRepository.save(payment);
    }

    public Payment addBonus(Employee employee, Integer month, Integer year,
                            String bonusTypeCode, BigDecimal amount, String description) {
        PaymentType bonusType = paymentTypeRepository.findByCode(bonusTypeCode)
                .orElseThrow(() -> new RuntimeException("Тип оплаты не найден: " + bonusTypeCode));

        return addBonus(employee, month, year, bonusType, amount, description);
    }

    public void calculateIncomeTax(Employee employee, Integer month, Integer year) {
        BigDecimal totalAccrued = paymentRepository
                .sumAmountByEmployeeAndPeriodAndCategory(employee, month, year, "accrual");

        BigDecimal taxAmount = totalAccrued.multiply(BigDecimal.valueOf(0.13))
                .setScale(2, RoundingMode.HALF_UP);

        PaymentType taxType = paymentTypeRepository.findByCode("INCOME_TAX")
                .orElseThrow(() -> new RuntimeException("Тип оплаты 'ПОДОХОДНЫЙ НАЛОГ' не найден"));

        Payment payment = new Payment();
        payment.setEmployee(employee);
        payment.setMonth(month);
        payment.setYear(year);
        payment.setPaymentType(taxType);
        payment.setAmount(taxAmount.negate());
        payment.setDescription("Подоходный налог 13%");

        paymentRepository.save(payment);
    }

    public void calculateFinalSalary(Employee employee, Integer month, Integer year) {
        BigDecimal totalAccrued = paymentRepository
                .sumAmountByEmployeeAndPeriodAndCategory(employee, month, year, "accrual");

        BigDecimal totalDeducted = paymentRepository
                .sumAmountByEmployeeAndPeriodAndCategory(employee, month, year, "deduction");

        BigDecimal netSalary = totalAccrued.add(totalDeducted);

        SalaryPayment salaryPayment = new SalaryPayment();
        salaryPayment.setEmployee(employee);
        salaryPayment.setMonth(month);
        salaryPayment.setYear(year);
        salaryPayment.setTotalAccrued(totalAccrued);
        salaryPayment.setTotalDeducted(totalDeducted.abs());
        salaryPayment.setNetSalary(netSalary);
        salaryPayment.setStatus(SalaryPayment.SalaryStatus.CALCULATED);

        salaryPaymentRepository.save(salaryPayment);
    }

    public List<Payment> getEmployeePayments(Employee employee, Integer month, Integer year) {
        return paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);
    }

    public Map<Integer, List<Payment>> getPaymentsForPeriod(Integer month, Integer year) {
        List<Payment> payments = paymentRepository.findByMonthAndYear(month, year);

        return payments.stream()
                .collect(Collectors.groupingBy(p -> p.getEmployee().getId()));
    }

    public BigDecimal calculateTotalAccruals(Employee employee, Integer month, Integer year) {
        List<Payment> payments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);

        return payments.stream()
                .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateTotalDeductions(Employee employee, Integer month, Integer year) {
        List<Payment> payments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);

        return payments.stream()
                .filter(p -> "deduction".equals(p.getPaymentType().getCategory()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateNetSalary(Employee employee, Integer month, Integer year) {
        BigDecimal accruals = calculateTotalAccruals(employee, month, year);
        BigDecimal deductions = calculateTotalDeductions(employee, month, year);

        return accruals.subtract(deductions);
    }

    public void deletePayment(Integer paymentId) {
        paymentRepository.deleteById(paymentId);
    }

    public void recalculateAllForPeriod(Integer month, Integer year, List<Employee> employees) {
        for (Employee employee : employees) {
            try {
                List<Payment> oldPayments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);
                paymentRepository.deleteAll(oldPayments);

                calculateBasicSalary(employee, month, year);

            } catch (Exception e) {
                System.err.println("Ошибка при расчете для сотрудника " + employee.getFullName() + ": " + e.getMessage());
            }
        }
    }
}