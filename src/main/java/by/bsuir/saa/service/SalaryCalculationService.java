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
import java.util.Optional;

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

    public Payment calculateBasicSalary(Employee employee, Integer month, Integer year) {
        Optional<Timesheet> timesheet = timesheetService.getTimesheet(employee, month, year);
        if (timesheet.isEmpty()) {
            throw new RuntimeException("Табель не найден для сотрудника: " + employee.getFullName());
        }

        BigDecimal workedHours = timesheet.get().getTotalHours();
        BigDecimal monthlyHours = BigDecimal.valueOf(160); // Норма часов в месяце
        BigDecimal baseSalary = employee.getPosition().getBaseSalary();

        BigDecimal salaryAmount = baseSalary
                .divide(monthlyHours, 2, RoundingMode.HALF_UP)
                .multiply(workedHours);

        PaymentType salaryType = paymentTypeRepository.findByCode("SALARY")
                .orElseThrow(() -> new RuntimeException("Тип оплаты 'ЗАРПЛАТА' не найден"));

        Payment payment = new Payment();
        payment.setEmployee(employee);
        payment.setMonth(month);
        payment.setYear(year);
        payment.setPaymentType(salaryType);
        payment.setAmount(salaryAmount);
        payment.setDescription("Основной оклад за " + month + "/" + year);

        return paymentRepository.save(payment);
    }

    public Payment addBonus(Employee employee, Integer month, Integer year,
                            String bonusTypeCode, BigDecimal amount, String description) {
        PaymentType bonusType = paymentTypeRepository.findByCode(bonusTypeCode)
                .orElseThrow(() -> new RuntimeException("Тип оплаты не найден: " + bonusTypeCode));

        Payment payment = new Payment();
        payment.setEmployee(employee);
        payment.setMonth(month);
        payment.setYear(year);
        payment.setPaymentType(bonusType);
        payment.setAmount(amount);
        payment.setDescription(description);

        return paymentRepository.save(payment);
    }

    public Payment calculateIncomeTax(Employee employee, Integer month, Integer year) {
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
        payment.setDescription("Подоходный налог");

        return paymentRepository.save(payment);
    }

    public SalaryPayment calculateFinalSalary(Employee employee, Integer month, Integer year) {
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

        return salaryPaymentRepository.save(salaryPayment);
    }

    public List<Payment> getEmployeePayments(Employee employee, Integer month, Integer year) {
        return paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);
    }
}