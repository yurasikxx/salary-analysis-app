package by.bsuir.saa.service;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.repository.PaymentRepository;
import by.bsuir.saa.repository.PaymentTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TaxCalculationService {

    private final PaymentRepository paymentRepository;
    private final PaymentTypeRepository paymentTypeRepository;
    private final FinalSalaryCalculationService finalSalaryCalculationService;

    @Transactional
    public void calculateAndSaveTaxes(Employee employee, Integer month, Integer year) {
        List<Payment> accruals = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year)
                .stream()
                .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                .toList();

        BigDecimal totalAccruals = accruals.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.debug("Расчет налогов для {}: общая сумма начислений = {} руб.",
                employee.getFullName(), totalAccruals);

        boolean hasIncomeTax = hasIncomeTax(employee, month, year);
        boolean hasSocialTax = hasSocialTax(employee, month, year);

        if (!hasIncomeTax && totalAccruals.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal incomeTax = totalAccruals.multiply(new BigDecimal("0.13"))
                    .setScale(2, RoundingMode.HALF_UP);

            PaymentType incomeTaxType = paymentTypeRepository.findByCode("ПН")
                    .orElseThrow(() -> new RuntimeException("Тип оплаты ПН не найден"));

            createTaxPayment(employee, month, year, incomeTaxType, incomeTax,
                    buildTaxDescription("Подоходный налог 13%", totalAccruals, incomeTax));

            log.info("Начислен подоходный налог для {}: {} руб. (с суммы {} руб.)",
                    employee.getFullName(), incomeTax, totalAccruals);
        }

        if (!hasSocialTax && totalAccruals.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal socialTax = totalAccruals.multiply(new BigDecimal("0.01"))
                    .setScale(2, RoundingMode.HALF_UP);

            PaymentType socialTaxType = paymentTypeRepository.findByCode("ФСЗН")
                    .orElseThrow(() -> new RuntimeException("Тип оплаты ФСЗН не найден"));

            createTaxPayment(employee, month, year, socialTaxType, socialTax,
                    buildTaxDescription("Взнос в ФСЗН 1%", totalAccruals, socialTax));

            log.info("Начислен взнос ФСЗН для {}: {} руб. (с суммы {} руб.)",
                    employee.getFullName(), socialTax, totalAccruals);
        }
    }

    @Transactional
    public void calculateTaxesBatch(Integer month, Integer year) {
        List<Employee> employees = paymentRepository.findByMonthAndYear(month, year)
                .stream()
                .map(Payment::getEmployee)
                .distinct()
                .toList();

        int calculatedCount = 0;

        for (Employee employee : employees) {
            try {
                List<Payment> accruals = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year)
                        .stream()
                        .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                        .toList();

                if (!accruals.isEmpty()) {
                    calculateAndSaveTaxes(employee, month, year);
                    calculatedCount++;
                }
            } catch (Exception e) {
                log.error("Ошибка расчета налогов для {}: {}",
                        employee.getFullName(), e.getMessage());
            }
        }

        log.info("Автоматический расчет налогов завершен: {} сотрудников", calculatedCount);
    }

    @Transactional
    public void deleteTaxes(Employee employee, Integer month, Integer year) {
        validateCanDeleteTaxes(employee, month, year);

        List<Payment> taxes = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year)
                .stream()
                .filter(p -> "ПН".equals(p.getPaymentType().getCode()) ||
                        "ФСЗН".equals(p.getPaymentType().getCode()))
                .toList();

        paymentRepository.deleteAll(taxes);
    }

    public BigDecimal calculateTotalAccrualsForEmployee(Employee employee, Integer month, Integer year) {
        return paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year)
                .stream()
                .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateIncomeTaxForEmployee(Employee employee, Integer month, Integer year) {
        BigDecimal totalAccruals = calculateTotalAccrualsForEmployee(employee, month, year);
        return totalAccruals.multiply(new BigDecimal("0.13"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateSocialTaxForEmployee(Employee employee, Integer month, Integer year) {
        BigDecimal totalAccruals = calculateTotalAccrualsForEmployee(employee, month, year);
        return totalAccruals.multiply(new BigDecimal("0.01"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public boolean hasTaxesCalculated(Employee employee, Integer month, Integer year) {
        return hasIncomeTax(employee, month, year) && hasSocialTax(employee, month, year);
    }

    public boolean hasIncomeTax(Employee employee, Integer month, Integer year) {
        return paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year)
                .stream().anyMatch(p -> "ПН".equals(p.getPaymentType().getCode()));
    }

    public boolean hasSocialTax(Employee employee, Integer month, Integer year) {
        return paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year)
                .stream().anyMatch(p -> "ФСЗН".equals(p.getPaymentType().getCode()));
    }

    private void createTaxPayment(Employee employee, Integer month, Integer year,
                                  PaymentType paymentType, BigDecimal taxAmount, String description) {
        Payment payment = new Payment();
        payment.setEmployee(employee);
        payment.setMonth(month);
        payment.setYear(year);
        payment.setPaymentType(paymentType);
        payment.setAmount(taxAmount.negate());
        payment.setDescription(description);

        paymentRepository.save(payment);
    }

    private String buildTaxDescription(String taxName, BigDecimal totalAccruals, BigDecimal taxAmount) {
        return String.format("%s: %.2f руб. × %.0f%% = %.2f руб.",
                taxName, totalAccruals,
                taxName.contains("13") ? 13.0 : 1.0,
                taxAmount);
    }

    private void validateCanDeleteTaxes(Employee employee, Integer month, Integer year) {
        if (finalSalaryCalculationService.isFinalSalaryCalculated(employee, month, year)) {
            throw new RuntimeException("Нельзя удалять налоги после расчета финальной зарплаты");
        }
    }
}