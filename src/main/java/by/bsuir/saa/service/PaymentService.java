package by.bsuir.saa.service;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public List<Payment> getPaymentsByPeriod(Integer month, Integer year) {
        return paymentRepository.findByMonthAndYear(month, year);
    }

    public List<Payment> getEmployeePayments(Employee employee, Integer month, Integer year) {
        return paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);
    }

    public List<Payment> getEmployeePaymentsByType(Employee employee, Integer month, Integer year, String paymentTypeCode) {
        return paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year).stream()
                .filter(p -> p.getPaymentType().getCode().equals(paymentTypeCode))
                .collect(Collectors.toList());
    }

    public Map<Integer, List<Payment>> getPaymentsGroupedByEmployee(Integer month, Integer year) {
        List<Payment> payments = paymentRepository.findByMonthAndYear(month, year);
        return payments.stream()
                .collect(Collectors.groupingBy(p -> p.getEmployee().getId()));
    }

    public BigDecimal getTotalAccruals(Integer month, Integer year) {
        List<Payment> payments = paymentRepository.findByMonthAndYear(month, year);
        return payments.stream()
                .filter(p -> p.getPaymentType().getCategory().equals("accrual"))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalDeductions(Integer month, Integer year) {
        List<Payment> payments = paymentRepository.findByMonthAndYear(month, year);
        return payments.stream()
                .filter(p -> p.getPaymentType().getCategory().equals("deduction"))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .abs();
    }

    public Payment createPayment(Employee employee, Integer month, Integer year,
                                 PaymentType paymentType, BigDecimal amount, String description) {
        Payment payment = new Payment();
        payment.setEmployee(employee);
        payment.setMonth(month);
        payment.setYear(year);
        payment.setPaymentType(paymentType);
        payment.setAmount(amount);
        payment.setDescription(description);

        return paymentRepository.save(payment);
    }

    public void deletePayment(Integer paymentId) {
        paymentRepository.deleteById(paymentId);
    }

    public Long getEmployeesWithCalculationsCount(Integer month, Integer year) {
        return paymentRepository.countDistinctEmployeesByMonthAndYear(month, year);
    }
}