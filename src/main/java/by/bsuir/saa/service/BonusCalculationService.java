package by.bsuir.saa.service;

import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.entity.PaymentType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@Transactional
public class BonusCalculationService {

    private static final String BASE_SALARY_CODE = "ОКЛ";
    private static final String ITR_BONUS_CODE = "ИТР";
    private static final String SENIORITY_BONUS_CODE = "СТАЖ";

    private final PaymentService paymentService;
    private final PaymentTypeService paymentTypeService;
    private final SalaryCalculationService salaryCalculationService;

    public BonusCalculationService(PaymentService paymentService,
                                   PaymentTypeService paymentTypeService,
                                   SalaryCalculationService salaryCalculationService) {
        this.paymentService = paymentService;
        this.paymentTypeService = paymentTypeService;
        this.salaryCalculationService = salaryCalculationService;
    }

    @Transactional
    public void calculateAllAutomaticBonuses(Employee employee, Integer month, Integer year) {
        validateBaseSalaryCalculated(employee, month, year);
        calculateSeniorityBonus(employee, month, year);
    }

    @Transactional
    public void calculateItrBonus(Employee employee, Integer month, Integer year) {
        validateBaseSalaryCalculated(employee, month, year);

        PaymentType itrBonusType = getPaymentType(ITR_BONUS_CODE);
        validateNoExistingBonus(employee, month, year, ITR_BONUS_CODE);

        BigDecimal baseSalary = salaryCalculationService.calculateBaseSalary(employee, month, year);
        BigDecimal itrBonus = calculatePercentage(baseSalary, new BigDecimal("0.25"));

        createBonusPayment(employee, month, year, itrBonusType, itrBonus,
                "Премия ИТР (25% от оклада)");
    }

    @Transactional
    public void calculateSeniorityBonus(Employee employee, Integer month, Integer year) {
        validateBaseSalaryCalculated(employee, month, year);

        PaymentType seniorityType = getPaymentType(SENIORITY_BONUS_CODE);
        validateNoExistingBonus(employee, month, year, SENIORITY_BONUS_CODE);

        BigDecimal bonusPercentage = getSeniorityPercentage(employee);

        if (bonusPercentage.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal baseSalary = salaryCalculationService.calculateBaseSalary(employee, month, year);
            BigDecimal seniorityBonus = calculatePercentage(baseSalary, bonusPercentage);
            long seniorityYears = getEmployeeSeniority(employee);

            createBonusPayment(employee, month, year, seniorityType, seniorityBonus,
                    buildSeniorityDescription(bonusPercentage, seniorityYears));
        }
    }

    public boolean isBaseSalaryCalculated(Employee employee, Integer month, Integer year) {
        return paymentService.getEmployeePayments(employee, month, year).stream()
                .anyMatch(p -> BASE_SALARY_CODE.equals(p.getPaymentType().getCode()));
    }

    public long getEmployeeSeniority(Employee employee) {
        LocalDate hireDate = employee.getHireDate();
        LocalDate endDate = getEndDate(employee);
        return ChronoUnit.YEARS.between(hireDate, endDate);
    }

    public BigDecimal getSeniorityPercentage(Employee employee) {
        long years = getEmployeeSeniority(employee);

        if (years >= 10) {
            return new BigDecimal("0.25");
        } else if (years >= 3) {
            return new BigDecimal("0.15");
        } else if (years >= 1) {
            return new BigDecimal("0.05");
        } else {
            return BigDecimal.ZERO;
        }
    }

    public String getSeniorityBadgeClass(Employee employee) {
        long years = getEmployeeSeniority(employee);
        if (years >= 10) return "bg-danger";
        if (years >= 3) return "bg-warning";
        if (years >= 1) return "bg-info";
        return "bg-secondary";
    }

    public BigDecimal getSeniorityPercentageValue(Employee employee) {
        long years = getEmployeeSeniority(employee);
        if (years >= 10) return new BigDecimal("0.25");
        if (years >= 3) return new BigDecimal("0.15");
        if (years >= 1) return new BigDecimal("0.05");
        return BigDecimal.ZERO;
    }

    public String getSeniorityPercentageText(Employee employee) {
        BigDecimal percentage = getSeniorityPercentageValue(employee);
        return String.valueOf(percentage.multiply(new BigDecimal("100")).intValue());
    }

    private void validateBaseSalaryCalculated(Employee employee, Integer month, Integer year) {
        if (!isBaseSalaryCalculated(employee, month, year)) {
            throw new RuntimeException("Нельзя рассчитывать надбавки до расчета основной зарплаты (оклада)");
        }
    }

    private void validateNoExistingBonus(Employee employee, Integer month, Integer year, String bonusCode) {
        boolean alreadyCalculated = paymentService.getEmployeePayments(employee, month, year).stream()
                .anyMatch(p -> bonusCode.equals(p.getPaymentType().getCode()));

        if (alreadyCalculated) {
            throw new RuntimeException(getBonusName(bonusCode) + " уже была начислена для этого периода");
        }
    }

    private PaymentType getPaymentType(String code) {
        return paymentTypeService.getPaymentTypeByCode(code)
                .orElseThrow(() -> new RuntimeException("Тип оплаты " + code + " не найден"));
    }

    private BigDecimal calculatePercentage(BigDecimal amount, BigDecimal percentage) {
        return amount.multiply(percentage).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate getEndDate(Employee employee) {
        return employee.getTerminationDate() != null ? employee.getTerminationDate() : LocalDate.now();
    }

    private String getBonusName(String bonusCode) {
        return switch (bonusCode) {
            case ITR_BONUS_CODE -> "Премия ИТР";
            case SENIORITY_BONUS_CODE -> "Надбавка за стаж";
            default -> "Надбавка";
        };
    }

    private String buildSeniorityDescription(BigDecimal bonusPercentage, long seniorityYears) {
        int percentageValue = bonusPercentage.multiply(new BigDecimal("100")).intValue();
        return String.format("Надбавка за стаж (%d%%) за период. Стаж: %d лет",
                percentageValue, seniorityYears);
    }

    private void createBonusPayment(Employee employee, Integer month, Integer year,
                                    PaymentType paymentType, BigDecimal amount, String description) {
        paymentService.createPayment(employee, month, year, paymentType, amount, description);
    }
}