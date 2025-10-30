package by.bsuir.saa.service;

import by.bsuir.saa.controller.AnalystController;
import by.bsuir.saa.entity.*;
import by.bsuir.saa.repository.SalaryPaymentRepository;
import by.bsuir.saa.repository.PaymentRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final EmployeeService employeeService;
    private final DepartmentService departmentService;
    private final PositionService positionService;
    private final SalaryPaymentRepository salaryPaymentRepository;
    private final PaymentRepository paymentRepository;

    public DashboardStats getDashboardStats(Integer month, Integer year) {
        List<Employee> employees = employeeService.getActiveEmployees();
        List<SalaryPayment> salaryPayments = salaryPaymentRepository.findByMonthAndYear(month, year);

        BigDecimal totalFOT = salaryPayments.stream()
                .map(SalaryPayment::getNetSalary)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageSalary = salaryPayments.isEmpty() ? BigDecimal.ZERO :
                totalFOT.divide(new BigDecimal(salaryPayments.size()), 2, RoundingMode.HALF_UP);

        long employeesWithSalary = salaryPayments.size();
        long totalEmployees = employees.size();

        List<AnalystController.DepartmentStats> departmentStats = calculateDepartmentStats(month, year);

        BigDecimal highestDepartmentFOT = departmentStats.stream()
                .map(AnalystController.DepartmentStats::getTotalFOT)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal highestAverageSalary = departmentStats.stream()
                .map(AnalystController.DepartmentStats::getAverageSalary)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        List<SalaryPayment> topSalaries = salaryPaymentRepository.findTopByMonthAndYearOrderByNetSalaryDesc(month, year, 5);

        double salaryCalculationRate = totalEmployees > 0 ? (employeesWithSalary * 100.0 / totalEmployees) : 0;

        return DashboardStats.builder()
                .totalEmployees(totalEmployees)
                .employeesWithSalary(employeesWithSalary)
                .totalFOT(totalFOT)
                .averageSalary(averageSalary)
                .departmentStats(departmentStats)
                .highestDepartmentFOT(highestDepartmentFOT)
                .highestAverageSalary(highestAverageSalary)
                .topSalaries(topSalaries)
                .salaryCalculationRate(salaryCalculationRate)
                .build();
    }

    public List<AnalystController.SalaryTrendData> getSalaryTrends(Integer monthsBack, Integer departmentId) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(monthsBack - 1).withDayOfMonth(1);

        List<AnalystController.SalaryTrendData> trendData = new ArrayList<>();

        for (int i = 0; i < monthsBack; i++) {
            YearMonth yearMonth = YearMonth.from(startDate.plusMonths(i));
            int currentMonth = yearMonth.getMonthValue();
            int currentYear = yearMonth.getYear();

            List<SalaryPayment> salaries = departmentId != null ?
                    salaryPaymentRepository.findByDepartmentAndPeriod(departmentId, currentMonth, currentYear) :
                    salaryPaymentRepository.findByMonthAndYear(currentMonth, currentYear);

            if (!salaries.isEmpty()) {
                BigDecimal totalFOT = salaries.stream()
                        .map(SalaryPayment::getNetSalary)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal averageSalary = totalFOT.divide(
                        new BigDecimal(salaries.size()), 2, RoundingMode.HALF_UP);

                BigDecimal minSalary = salaries.stream()
                        .map(SalaryPayment::getNetSalary)
                        .min(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

                BigDecimal maxSalary = salaries.stream()
                        .map(SalaryPayment::getNetSalary)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

                AnalystController.SalaryTrendData data = new AnalystController.SalaryTrendData();
                data.setPeriod(getRussianMonthName(currentMonth) + " " + currentYear);
                data.setAverageSalary(averageSalary);
                data.setTotalFOT(totalFOT);
                data.setEmployeeCount(salaries.size());
                data.setMinSalary(minSalary);
                data.setMaxSalary(maxSalary);

                trendData.add(data);
            }
        }

        return trendData;
    }

    public List<AnalystController.DepartmentStats> calculateDepartmentStats(Integer month, Integer year) {
        List<Department> departments = departmentService.getAllDepartments();
        List<AnalystController.DepartmentStats> stats = new ArrayList<>();

        for (Department department : departments) {
            List<SalaryPayment> departmentSalaries = salaryPaymentRepository.findByDepartmentAndPeriod(department.getId(), month, year);

            if (!departmentSalaries.isEmpty()) {
                BigDecimal totalFOT = departmentSalaries.stream()
                        .map(SalaryPayment::getNetSalary)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal averageSalary = totalFOT.divide(
                        new BigDecimal(departmentSalaries.size()), 2, RoundingMode.HALF_UP);

                BigDecimal minSalary = departmentSalaries.stream()
                        .map(SalaryPayment::getNetSalary)
                        .min(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

                BigDecimal maxSalary = departmentSalaries.stream()
                        .map(SalaryPayment::getNetSalary)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

                AnalystController.DepartmentStats departmentStat = new AnalystController.DepartmentStats();
                departmentStat.setDepartmentName(department.getName());
                departmentStat.setAverageSalary(averageSalary);
                departmentStat.setTotalFOT(totalFOT);
                departmentStat.setEmployeeCount(departmentSalaries.size());
                departmentStat.setMinSalary(minSalary);
                departmentStat.setMaxSalary(maxSalary);

                stats.add(departmentStat);
            }
        }

        return stats.stream()
                .sorted((d1, d2) -> d2.getAverageSalary().compareTo(d1.getAverageSalary()))
                .collect(Collectors.toList());
    }

    public BigDecimal getTotalCompanyFOT(Integer month, Integer year) {
        List<AnalystController.DepartmentStats> departmentStats = calculateDepartmentStats(month, year);
        return departmentStats.stream()
                .map(AnalystController.DepartmentStats::getTotalFOT)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<AnalystController.PositionStats> getPositionStats(Integer month, Integer year) {
        List<Position> positions = positionService.getAllPositions();
        List<AnalystController.PositionStats> positionStats = new ArrayList<>();

        for (Position position : positions) {
            List<Employee> positionEmployees = employeeService.findByPosition(position);
            List<SalaryPayment> positionSalaries = positionEmployees.stream()
                    .map(employee -> salaryPaymentRepository.findByEmployeeAndMonthAndYear(employee, month, year))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();

            if (!positionSalaries.isEmpty()) {
                BigDecimal totalFOT = positionSalaries.stream()
                        .map(SalaryPayment::getNetSalary)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal averageSalary = totalFOT.divide(
                        new BigDecimal(positionSalaries.size()), 2, RoundingMode.HALF_UP);

                BigDecimal minSalary = positionSalaries.stream()
                        .map(SalaryPayment::getNetSalary)
                        .min(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

                BigDecimal maxSalary = positionSalaries.stream()
                        .map(SalaryPayment::getNetSalary)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

                AnalystController.PositionStats stats = new AnalystController.PositionStats();
                stats.setPositionTitle(position.getTitle());
                stats.setAverageSalary(averageSalary);
                stats.setMinSalary(minSalary);
                stats.setMaxSalary(maxSalary);
                stats.setEmployeeCount(positionSalaries.size());
                stats.setTotalFOT(totalFOT);

                positionStats.add(stats);
            }
        }

        positionStats.sort((p1, p2) -> p2.getAverageSalary().compareTo(p1.getAverageSalary()));
        return positionStats;
    }

    public List<AnalystController.SalaryStructure> getSalaryStructure(Integer month, Integer year, Integer employeeId) {
        List<AnalystController.SalaryStructure> structureData;

        if (employeeId != null) {
            Employee employee = employeeService.getEmployeeById(employeeId)
                    .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));
            structureData = getEmployeeSalaryStructure(employee, month, year);
        } else {
            structureData = getCompanySalaryStructure(month, year);
        }

        structureData.sort((s1, s2) -> s2.getAmount().compareTo(s1.getAmount()));
        return structureData;
    }

    private List<AnalystController.SalaryStructure> getEmployeeSalaryStructure(Employee employee, Integer month, Integer year) {
        List<AnalystController.SalaryStructure> structureData = new ArrayList<>();
        List<Payment> payments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);

        BigDecimal totalAccrued = payments.stream()
                .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return getSalaryStructures(structureData, payments, totalAccrued);
    }

    private List<AnalystController.SalaryStructure> getSalaryStructures(List<AnalystController.SalaryStructure> structureData, List<Payment> payments, BigDecimal totalAccrued) {
        if (totalAccrued.compareTo(BigDecimal.ZERO) > 0) {
            Map<String, BigDecimal> accrualsByType = payments.stream()
                    .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                    .collect(Collectors.groupingBy(
                            p -> p.getPaymentType().getName(),
                            Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)
                    ));

            for (Map.Entry<String, BigDecimal> entry : accrualsByType.entrySet()) {
                AnalystController.SalaryStructure structure = new AnalystController.SalaryStructure();
                structure.setCategory(entry.getKey());
                structure.setAmount(entry.getValue());
                structure.setPercentage(entry.getValue()
                        .divide(totalAccrued, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")));
                structureData.add(structure);
            }
        }

        return structureData;
    }

    private List<AnalystController.SalaryStructure> getCompanySalaryStructure(Integer month, Integer year) {
        List<AnalystController.SalaryStructure> structureData = new ArrayList<>();
        List<Payment> allPayments = paymentRepository.findByMonthAndYear(month, year);

        BigDecimal totalCompanyAccrued = allPayments.stream()
                .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return getSalaryStructures(structureData, allPayments, totalCompanyAccrued);
    }

    private String getRussianMonthName(int month) {
        String[] months = {"Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"};
        return months[month - 1];
    }

    @Data
    @Builder
    public static class DashboardStats {
        private long totalEmployees;
        private long employeesWithSalary;
        private BigDecimal totalFOT;
        private BigDecimal averageSalary;
        private List<AnalystController.DepartmentStats> departmentStats;
        private BigDecimal highestDepartmentFOT;
        private BigDecimal highestAverageSalary;
        private List<SalaryPayment> topSalaries;
        private double salaryCalculationRate;
    }
}