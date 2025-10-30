package by.bsuir.saa.service;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.repository.PaymentRepository;
import by.bsuir.saa.repository.TimesheetRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class VacationSickLeaveCalculationService {

    private final TimesheetRepository timesheetRepository;
    private final PaymentService paymentService;
    private final PaymentTypeService paymentTypeService;
    private final EmployeeService employeeService;
    private final PaymentRepository paymentRepository;
    private final TaxCalculationService taxCalculationService;

    private static final BigDecimal SICK_LEAVE_RATE = new BigDecimal("0.50");
    private static final BigDecimal VACATION_RATE = new BigDecimal("1.50");
    private static final int WORKING_DAYS_IN_MONTH = 20;

    public CalculationInfo getCalculationInfo(Employee employee, Integer month, Integer year) {
        CalculationInfo info = new CalculationInfo()
                .setEmployee(employee)
                .setMonth(month)
                .setYear(year);

        Map<String, Long> daysByMarkType = countDaysByMarkType(employee, month, year);

        List<Payment> existingPayments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);

        info.setSickLeaveDays(daysByMarkType.getOrDefault("Б", 0L))
                .setVacationDays(daysByMarkType.getOrDefault("О", 0L))
                .setHasSickLeavePayment(existingPayments.stream().anyMatch(p -> "БОЛ".equals(p.getPaymentType().getCode())))
                .setHasVacationPayment(existingPayments.stream().anyMatch(p -> "ОТП".equals(p.getPaymentType().getCode())))
                .setHasBaseSalary(existingPayments.stream().anyMatch(p -> "ОКЛ".equals(p.getPaymentType().getCode())))
                .setExistingPayments(existingPayments);

        if (info.isHasBaseSalary()) {
            BigDecimal dailyRate = calculateDailyRate(employee);
            info.setDailyRate(dailyRate);

            if (info.getSickLeaveDays() > 0) {
                BigDecimal sickLeaveAmount = calculateSickLeaveAmount(dailyRate, info.getSickLeaveDays());
                info.setSickLeaveAmount(sickLeaveAmount);
            }

            if (info.getVacationDays() > 0) {
                BigDecimal vacationAmount = calculateVacationAmount(dailyRate, info.getVacationDays());
                info.setVacationAmount(vacationAmount);
            }
        }

        return info;
    }

    @Transactional
    public void calculateAndSaveSickLeave(Employee employee, Integer month, Integer year) {
        validateBaseSalaryCalculated(employee, month, year);
        validateNoExistingPayment(employee, month, year, "БОЛ");

        if (taxCalculationService.hasTaxesCalculated(employee, month, year)) {
            throw new RuntimeException("Нельзя рассчитывать больничные после начисления налогов");
        }

        CalculationInfo info = getCalculationInfo(employee, month, year);
        if (info.getSickLeaveDays() == 0) {
            throw new RuntimeException("У сотрудника " + employee.getFullName() + " нет дней больничного в табеле");
        }

        PaymentType sickLeaveType = paymentTypeService.getSickLeavePaymentType()
                .orElseThrow(() -> new RuntimeException("Тип оплаты БОЛ не найден"));

        paymentService.createPayment(employee, month, year, sickLeaveType,
                info.getSickLeaveAmount(), buildSickLeaveDescription(info));

        log.info("Начислены больничные для {}: {} руб. за {} дней",
                employee.getFullName(), info.getSickLeaveAmount(), info.getSickLeaveDays());
    }

    @Transactional
    public void calculateAndSaveVacation(Employee employee, Integer month, Integer year) {
        validateBaseSalaryCalculated(employee, month, year);
        validateNoExistingPayment(employee, month, year, "ОТП");

        CalculationInfo info = getCalculationInfo(employee, month, year);
        if (info.getVacationDays() == 0) {
            throw new RuntimeException("У сотрудника " + employee.getFullName() + " нет дней отпуска в табеле");
        }

        PaymentType vacationType = paymentTypeService.getVacationPaymentType()
                .orElseThrow(() -> new RuntimeException("Тип оплаты ОТП не найден"));

        paymentService.createPayment(employee, month, year, vacationType,
                info.getVacationAmount(), buildVacationDescription(info));

        log.info("Начислены отпускные для {}: {} руб. за {} дней",
                employee.getFullName(), info.getVacationAmount(), info.getVacationDays());
    }

    @Transactional
    public void calculateAndSaveAll(Employee employee, Integer month, Integer year) {
        CalculationInfo info = getCalculationInfo(employee, month, year);
        boolean calculated = false;

        if (info.canCalculateSickLeave()) {
            calculateAndSaveSickLeave(employee, month, year);
            calculated = true;
        }

        if (info.canCalculateVacation()) {
            calculateAndSaveVacation(employee, month, year);
            calculated = true;
        }

        if (!calculated) {
            throw new RuntimeException("Нет данных для расчета отпускных или больничных");
        }
    }

    @Transactional
    public int calculateBatch(Integer month, Integer year) {
        List<Employee> employees = getEmployeesWithConfirmedTimesheets(month, year);
        int calculatedCount = 0;

        for (Employee employee : employees) {
            try {
                CalculationInfo info = getCalculationInfo(employee, month, year);
                if (info.hasAnyCalculation()) {
                    calculateAndSaveAll(employee, month, year);
                    calculatedCount++;
                }
            } catch (Exception e) {
                log.error("Ошибка расчета отпускных/больничных для {}: {}",
                        employee.getFullName(), e.getMessage());
            }
        }

        log.info("Автоматический расчет отпускных/больничных завершен: {} сотрудников", calculatedCount);
        return calculatedCount;
    }

    @Transactional
    public void deleteVacationSickLeavePayment(Integer paymentId, Integer month, Integer year) {
        Payment payment = paymentService.getPaymentById(paymentId)
                .orElseThrow(() -> new RuntimeException("Платеж не найден"));

        validateCanDeleteVacationSickLeave(payment.getEmployee(), month, year);

        paymentService.deletePayment(paymentId);
    }

    public List<Employee> getEmployeesWithConfirmedTimesheets(Integer month, Integer year) {
        return timesheetRepository.findByMonthAndYearWithEntries(month, year).stream()
                .filter(timesheet -> timesheet.getStatus() == Timesheet.TimesheetStatus.CONFIRMED)
                .map(Timesheet::getEmployee)
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<String, Long> countDaysByMarkType(Employee employee, Integer month, Integer year) {
        Optional<Timesheet> timesheetOpt = timesheetRepository.findByEmployeeAndMonthAndYear(employee, month, year);
        if (timesheetOpt.isEmpty() || timesheetOpt.get().getStatus() != Timesheet.TimesheetStatus.CONFIRMED) {
            return Map.of();
        }

        Timesheet timesheet = timesheetOpt.get();
        return timesheet.getTimesheetEntries().stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getMarkType().getCode(),
                        Collectors.counting()
                ));
    }

    private BigDecimal calculateDailyRate(Employee employee) {
        BigDecimal baseSalary = employee.getPosition().getBaseSalary();
        return baseSalary.divide(new BigDecimal(WORKING_DAYS_IN_MONTH), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateSickLeaveAmount(BigDecimal dailyRate, Long days) {
        return dailyRate.multiply(SICK_LEAVE_RATE)
                .multiply(new BigDecimal(days))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateVacationAmount(BigDecimal dailyRate, Long days) {
        return dailyRate.multiply(VACATION_RATE)
                .multiply(new BigDecimal(days))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean hasExistingPayment(Employee employee, Integer month, Integer year, String paymentCode) {
        return paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year).stream()
                .anyMatch(p -> paymentCode.equals(p.getPaymentType().getCode()));
    }

    private boolean hasBaseSalary(Employee employee, Integer month, Integer year) {
        return paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year).stream()
                .anyMatch(p -> "ОКЛ".equals(p.getPaymentType().getCode()));
    }

    private void validateBaseSalaryCalculated(Employee employee, Integer month, Integer year) {
        if (!hasBaseSalary(employee, month, year)) {
            throw new RuntimeException("Нельзя рассчитывать отпускные/больничные до расчета основной зарплаты для " + employee.getFullName());
        }
    }

    private void validateNoExistingPayment(Employee employee, Integer month, Integer year, String paymentCode) {
        if (hasExistingPayment(employee, month, year, paymentCode)) {
            String paymentName = "БОЛ".equals(paymentCode) ? "больничные" : "отпускные";
            throw new RuntimeException(paymentName + " уже начислены для " + employee.getFullName() + " за этот период");
        }
    }

    private String buildSickLeaveDescription(CalculationInfo info) {
        return String.format("Оплата больничного листа: %d дн. × %.2f руб. × 50%% = %.2f руб.",
                info.getSickLeaveDays(), info.getDailyRate(), info.getSickLeaveAmount());
    }

    private String buildVacationDescription(CalculationInfo info) {
        return String.format("Оплата отпуска: %d дн. × %.2f руб. × 150%% = %.2f руб.",
                info.getVacationDays(), info.getDailyRate(), info.getVacationAmount());
    }

    private void validateCanDeleteVacationSickLeave(Employee employee, Integer month, Integer year) {
        if (taxCalculationService.hasTaxesCalculated(employee, month, year)) {
            throw new RuntimeException("Нельзя удалять отпускные/больничные после начисления налогов");
        }
    }

    @Data
    @Accessors(chain = true)
    public static class CalculationInfo {
        private Employee employee;
        private Integer month;
        private Integer year;
        private Long sickLeaveDays = 0L;
        private Long vacationDays = 0L;
        private boolean hasSickLeavePayment;
        private boolean hasVacationPayment;
        private boolean hasBaseSalary;
        private BigDecimal dailyRate = BigDecimal.ZERO;
        private BigDecimal sickLeaveAmount = BigDecimal.ZERO;
        private BigDecimal vacationAmount = BigDecimal.ZERO;
        private List<Payment> existingPayments = new ArrayList<>();

        public boolean canCalculateSickLeave() {
            return hasBaseSalary && sickLeaveDays > 0 && !hasSickLeavePayment;
        }

        public boolean canCalculateVacation() {
            return hasBaseSalary && vacationDays > 0 && !hasVacationPayment;
        }

        public boolean hasAnyCalculation() {
            return canCalculateSickLeave() || canCalculateVacation();
        }

        public Payment getSickLeavePayment() {
            return existingPayments.stream()
                    .filter(p -> "БОЛ".equals(p.getPaymentType().getCode()))
                    .findFirst()
                    .orElse(null);
        }

        public Payment getVacationPayment() {
            return existingPayments.stream()
                    .filter(p -> "ОТП".equals(p.getPaymentType().getCode()))
                    .findFirst()
                    .orElse(null);
        }
    }
}