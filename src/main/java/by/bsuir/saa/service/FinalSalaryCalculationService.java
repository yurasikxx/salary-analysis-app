package by.bsuir.saa.service;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.repository.PaymentRepository;
import by.bsuir.saa.repository.SalaryPaymentRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class FinalSalaryCalculationService {

    private final PaymentRepository paymentRepository;
    private final SalaryPaymentRepository salaryPaymentRepository;
    private final EmployeeService employeeService;

    public FinalSalaryResult calculateFinalSalaryForEmployee(Employee employee, Integer month, Integer year) {
        List<Payment> payments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);

        BigDecimal totalAccrued = payments.stream()
                .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDeducted = payments.stream()
                .filter(p -> "deduction".equals(p.getPaymentType().getCategory()))
                .map(p -> p.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netSalary = totalAccrued.subtract(totalDeducted);

        FinalSalaryResult result = new FinalSalaryResult();
        result.setEmployee(employee);
        result.setMonth(month);
        result.setYear(year);
        result.setTotalAccrued(totalAccrued);
        result.setTotalDeducted(totalDeducted);
        result.setNetSalary(netSalary);
        result.setPayments(payments);

        return result;
    }

    @Transactional
    public void calculateAndSaveFinalSalary(Employee employee, Integer month, Integer year) {
        FinalSalaryResult result = calculateFinalSalaryForEmployee(employee, month, year);

        // Проверяем, не рассчитана ли уже итоговая зарплата
        Optional<SalaryPayment> existingSalary = salaryPaymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);
        if (existingSalary.isPresent()) {
            throw new RuntimeException("Итоговая зарплата уже рассчитана для сотрудника " + employee.getFullName());
        }

        // Проверяем, есть ли начисления
        if (result.getTotalAccrued().compareTo(BigDecimal.ZERO) == 0) {
            throw new RuntimeException("Нет начислений для расчета итоговой зарплаты для " + employee.getFullName());
        }

        SalaryPayment salaryPayment = new SalaryPayment();
        salaryPayment.setEmployee(employee);
        salaryPayment.setMonth(month);
        salaryPayment.setYear(year);
        salaryPayment.setTotalAccrued(result.getTotalAccrued());
        salaryPayment.setTotalDeducted(result.getTotalDeducted());
        salaryPayment.setNetSalary(result.getNetSalary());
        salaryPayment.setCalculationDate(LocalDateTime.now());
        salaryPayment.setStatus(SalaryPayment.SalaryStatus.CALCULATED);

        salaryPaymentRepository.save(salaryPayment);

        log.info("Рассчитана итоговая зарплата для {}: начислено {} руб., удержано {} руб., к выплате {} руб.",
                employee.getFullName(), result.getTotalAccrued(), result.getTotalDeducted(), result.getNetSalary());
    }

    @Transactional
    public int calculateFinalSalariesBatch(Integer month, Integer year) {
        List<Employee> employees = paymentRepository.findByMonthAndYear(month, year)
                .stream()
                .map(Payment::getEmployee)
                .distinct()
                .toList();

        int calculatedCount = 0;

        for (Employee employee : employees) {
            try {
                // Проверяем, не рассчитана ли уже итоговая зарплата
                if (!isFinalSalaryCalculated(employee, month, year)) {
                    calculateAndSaveFinalSalary(employee, month, year);
                    calculatedCount++;
                }
            } catch (Exception e) {
                log.error("Ошибка расчета итоговой зарплаты для {}: {}",
                        employee.getFullName(), e.getMessage());
            }
        }

        log.info("Автоматический расчет итоговых зарплат завершен: {} сотрудников", calculatedCount);
        return calculatedCount;
    }

    public boolean isFinalSalaryCalculated(Employee employee, Integer month, Integer year) {
        return salaryPaymentRepository.findByEmployeeAndMonthAndYear(employee, month, year).isPresent();
    }

    public FinalSalarySummary getFinalSalarySummary(Integer month, Integer year) {
        List<SalaryPayment> salaryPayments = salaryPaymentRepository.findByMonthAndYear(month, year);

        BigDecimal totalAccrued = salaryPayments.stream()
                .map(SalaryPayment::getTotalAccrued)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDeducted = salaryPayments.stream()
                .map(SalaryPayment::getTotalDeducted)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalNetSalary = salaryPayments.stream()
                .map(SalaryPayment::getNetSalary)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        FinalSalarySummary summary = new FinalSalarySummary();
        summary.setMonth(month);
        summary.setYear(year);
        summary.setTotalEmployees(salaryPayments.size());
        summary.setTotalAccrued(totalAccrued);
        summary.setTotalDeducted(totalDeducted);
        summary.setTotalNetSalary(totalNetSalary);
        summary.setAverageSalary(totalNetSalary.divide(
                new BigDecimal(Math.max(salaryPayments.size(), 1)), 2, RoundingMode.HALF_UP));

        return summary;
    }

    public List<SalaryPayment> getCalculatedSalaries(Integer month, Integer year) {
        return salaryPaymentRepository.findByMonthAndYear(month, year);
    }

    public long getEmployeesWithFinalSalaryCount(Integer month, Integer year) {
        return employeeService.getActiveEmployees().stream()
                .filter(employee -> isFinalSalaryCalculated(employee, month, year))
                .count();
    }

    @Data
    public static class FinalSalaryResult {
        private Employee employee;
        private Integer month;
        private Integer year;
        private BigDecimal totalAccrued = BigDecimal.ZERO;
        private BigDecimal totalDeducted = BigDecimal.ZERO;
        private BigDecimal netSalary = BigDecimal.ZERO;
        private List<Payment> payments;
    }

    @Data
    public static class FinalSalarySummary {
        private Integer month;
        private Integer year;
        private int totalEmployees;
        private BigDecimal totalAccrued = BigDecimal.ZERO;
        private BigDecimal totalDeducted = BigDecimal.ZERO;
        private BigDecimal totalNetSalary = BigDecimal.ZERO;
        private BigDecimal averageSalary = BigDecimal.ZERO;
    }
}