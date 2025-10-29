package by.bsuir.saa.service;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.repository.PaymentRepository;
import by.bsuir.saa.repository.TimesheetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final TimesheetRepository timesheetRepository;
    private final EmployeeService employeeService;

    public List<Payment> getEmployeePayments(Employee employee, Integer month, Integer year) {
        return paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);
    }

    public Optional<Payment> getPaymentById(Integer id) {
        return paymentRepository.findById(id);
    }

    @Transactional
    public void createPayment(Employee employee, Integer month, Integer year,
                              PaymentType paymentType, BigDecimal amount, String description) {

        boolean paymentExists = paymentRepository.findByEmployeeAndMonthAndYearAndPaymentType(
                employee, month, year, paymentType).isPresent();

        if (paymentExists) {
            throw new RuntimeException("Платеж типа '" + paymentType.getName() + "' уже существует за этот период");
        }

        Payment payment = new Payment();
        payment.setEmployee(employee);
        payment.setMonth(month);
        payment.setYear(year);
        payment.setPaymentType(paymentType);
        payment.setAmount(amount);
        payment.setDescription(description != null ? description.trim() : null);

        paymentRepository.save(payment);

        log.info("Создан платеж: {} - {} руб. для {} за {}.{}",
                paymentType.getName(), amount, employee.getFullName(), month, year);
    }

    @Transactional
    public void deletePayment(Integer paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Платеж не найден"));

        paymentRepository.delete(payment);

        log.info("Удален платеж: {} - {} руб. для {} за {}.{}",
                payment.getPaymentType().getName(), payment.getAmount(),
                payment.getEmployee().getFullName(), payment.getMonth(), payment.getYear());
    }

    public List<Payment> getPaymentsByPeriod(Integer month, Integer year) {
        return paymentRepository.findByMonthAndYear(month, year);
    }

    public long getEmployeesWithCalculationsCount(Integer month, Integer year) {
        return paymentRepository.countDistinctEmployeesByMonthAndYear(month, year);
    }

    public BigDecimal getTotalAccruals(Integer month, Integer year) {
        return paymentRepository.findByMonthAndYear(month, year).stream()
                .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalDeductions(Integer month, Integer year) {
        return paymentRepository.findByMonthAndYear(month, year).stream()
                .filter(p -> "deduction".equals(p.getPaymentType().getCategory()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Метод для проверки существования налогов (без зависимости от TaxCalculationService)
    public boolean hasTaxes(Employee employee, Integer month, Integer year) {
        return paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year).stream()
                .anyMatch(p -> "deduction".equals(p.getPaymentType().getCategory()) &&
                        ("ПН".equals(p.getPaymentType().getCode()) ||
                                "ФСЗН".equals(p.getPaymentType().getCode())));
    }

    public long getEmployeesWithBonusesCount(Integer month, Integer year) {
        return employeeService.getActiveEmployees().stream()
                .filter(employee -> {
                    List<Payment> payments = getEmployeePayments(employee, month, year);
                    return payments.stream()
                            .anyMatch(p -> "accrual".equals(p.getPaymentType().getCategory()) &&
                                    !"ОКЛ".equals(p.getPaymentType().getCode()));
                })
                .count();
    }

    public long getEmployeesWithTaxesCount(Integer month, Integer year) {
        return employeeService.getActiveEmployees().stream()
                .filter(employee -> {
                    List<Payment> payments = getEmployeePayments(employee, month, year);
                    return payments.stream()
                            .anyMatch(p -> "ПН".equals(p.getPaymentType().getCode()) ||
                                    "ФСЗН".equals(p.getPaymentType().getCode()));
                })
                .count();
    }

    public BigDecimal getTotalBaseSalary(Integer month, Integer year) {
        return employeeService.getActiveEmployees().stream()
                .flatMap(employee -> getEmployeePayments(employee, month, year).stream())
                .filter(p -> "ОКЛ".equals(p.getPaymentType().getCode()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalEnterpriseBonuses(Integer month, Integer year) {
        return employeeService.getActiveEmployees().stream()
                .flatMap(employee -> getEmployeePayments(employee, month, year).stream())
                .filter(p -> "ПРЕД".equals(p.getPaymentType().getCode()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalItrBonuses(Integer month, Integer year) {
        return employeeService.getActiveEmployees().stream()
                .flatMap(employee -> getEmployeePayments(employee, month, year).stream())
                .filter(p -> "ИТР".equals(p.getPaymentType().getCode()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalSeniorityBonuses(Integer month, Integer year) {
        return employeeService.getActiveEmployees().stream()
                .flatMap(employee -> getEmployeePayments(employee, month, year).stream())
                .filter(p -> "СТАЖ".equals(p.getPaymentType().getCode()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalIncomeTax(Integer month, Integer year) {
        return employeeService.getActiveEmployees().stream()
                .flatMap(employee -> getEmployeePayments(employee, month, year).stream())
                .filter(p -> "ПН".equals(p.getPaymentType().getCode()))
                .map(p -> p.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalSocialTax(Integer month, Integer year) {
        return employeeService.getActiveEmployees().stream()
                .flatMap(employee -> getEmployeePayments(employee, month, year).stream())
                .filter(p -> "ФСЗН".equals(p.getPaymentType().getCode()))
                .map(p -> p.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalOtherDeductions(Integer month, Integer year) {
        return employeeService.getActiveEmployees().stream()
                .flatMap(employee -> getEmployeePayments(employee, month, year).stream())
                .filter(p -> "deduction".equals(p.getPaymentType().getCategory()) &&
                        !"ПН".equals(p.getPaymentType().getCode()) &&
                        !"ФСЗН".equals(p.getPaymentType().getCode()))
                .map(p -> p.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}