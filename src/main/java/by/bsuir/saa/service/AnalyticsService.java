package by.bsuir.saa.service;

import by.bsuir.saa.entity.Department;
import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.entity.Payment;
import by.bsuir.saa.entity.SalaryPayment;
import by.bsuir.saa.repository.SalaryPaymentRepository;
import by.bsuir.saa.repository.PaymentRepository;
import by.bsuir.saa.repository.EmployeeRepository;
import by.bsuir.saa.repository.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final SalaryPaymentRepository salaryPaymentRepository;
    private final PaymentRepository paymentRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    public AnalyticsService(SalaryPaymentRepository salaryPaymentRepository,
                            PaymentRepository paymentRepository,
                            EmployeeRepository employeeRepository,
                            DepartmentRepository departmentRepository) {
        this.salaryPaymentRepository = salaryPaymentRepository;
        this.paymentRepository = paymentRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
    }

    public Map<String, Object> getSalaryDynamicsByDepartment(Integer departmentId,
                                                             Integer startMonth, Integer startYear,
                                                             Integer endMonth, Integer endYear) {
        Map<String, Object> result = new HashMap<>();

        List<Object[]> departmentData = salaryPaymentRepository
                .findAverageSalaryByDepartmentAndPeriodRange(departmentId, startMonth, startYear, endMonth, endYear);

        List<String> labels = new ArrayList<>();
        List<BigDecimal> averageSalaries = new ArrayList<>();
        List<Long> employeeCounts = new ArrayList<>();

        for (Object[] data : departmentData) {
            YearMonth yearMonth = YearMonth.of((Integer) data[2], (Integer) data[1]);
            labels.add(yearMonth.getMonth().toString() + " " + data[2]);
            averageSalaries.add(((BigDecimal) data[3]).setScale(2, RoundingMode.HALF_UP));
            employeeCounts.add((Long) data[4]);
        }

        result.put("labels", labels);
        result.put("averageSalaries", averageSalaries);
        result.put("employeeCounts", employeeCounts);
        result.put("departmentName", getDepartmentName(departmentId));

        return result;
    }

    public Map<String, Object> getDepartmentSalaryComparison(Integer month, Integer year) {
        Map<String, Object> result = new HashMap<>();

        List<Department> allDepartments = departmentRepository.findAll();

        List<String> departments = new ArrayList<>();
        List<BigDecimal> averageSalaries = new ArrayList<>();
        List<BigDecimal> totalFOT = new ArrayList<>();
        List<Long> employeeCounts = new ArrayList<>();

        for (Department department : allDepartments) {
            BigDecimal avgSalary = salaryPaymentRepository
                    .findAverageSalaryByDepartmentAndPeriod(department.getId(), month, year);
            BigDecimal fot = salaryPaymentRepository
                    .findTotalFOTByDepartmentAndPeriod(department.getId(), month, year);
            Long empCount = salaryPaymentRepository
                    .findEmployeeCountByDepartmentAndPeriod(department.getId(), month, year);

            if (empCount > 0) { // Только отделы с сотрудниками
                departments.add(department.getName());
                averageSalaries.add(avgSalary.setScale(2, RoundingMode.HALF_UP));
                totalFOT.add(fot.setScale(2, RoundingMode.HALF_UP));
                employeeCounts.add(empCount);
            }
        }

        result.put("departments", departments);
        result.put("averageSalaries", averageSalaries);
        result.put("totalFOT", totalFOT);
        result.put("employeeCounts", employeeCounts);
        result.put("period", month + "/" + year);

        return result;
    }

    public Map<String, Object> getPaymentStructureAnalysis(Integer employeeId, Integer month, Integer year) {
        Map<String, Object> result = new HashMap<>();

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        List<Payment> payments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);

        Map<String, BigDecimal> paymentByType = payments.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getPaymentType().getName(),
                        Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)
                ));

        Map<String, BigDecimal> accruals = new HashMap<>();
        Map<String, BigDecimal> deductions = new HashMap<>();

        paymentByType.forEach((type, amount) -> {
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                accruals.put(type, amount);
            } else {
                deductions.put(type, amount.abs());
            }
        });

        result.put("employee", employee.getFullName());
        result.put("department", employee.getDepartment().getName());
        result.put("position", employee.getPosition().getTitle());
        result.put("accruals", accruals);
        result.put("deductions", deductions);
        result.put("netSalary", calculateNetSalary(accruals, deductions));
        result.put("period", month + "/" + year);

        return result;
    }

    public List<Map<String, Object>> getTopEmployeesBySalary(Integer month, Integer year, int limit) {
        List<SalaryPayment> topSalaries = salaryPaymentRepository
                .findTopByMonthAndYearOrderByNetSalaryDesc(month, year, limit);

        return topSalaries.stream()
                .map(sp -> {
                    Map<String, Object> empData = new HashMap<>();
                    empData.put("name", sp.getEmployee().getFullName());
                    empData.put("department", sp.getEmployee().getDepartment().getName());
                    empData.put("position", sp.getEmployee().getPosition().getTitle());
                    empData.put("salary", sp.getNetSalary().setScale(2, RoundingMode.HALF_UP));
                    empData.put("totalAccrued", sp.getTotalAccrued().setScale(2, RoundingMode.HALF_UP));
                    empData.put("totalDeducted", sp.getTotalDeducted().setScale(2, RoundingMode.HALF_UP));
                    return empData;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getSalaryTrendAnalysis(Integer departmentId,
                                                      Integer currentMonth, Integer currentYear) {
        Map<String, Object> result = new HashMap<>();

        YearMonth current = YearMonth.of(currentYear, currentMonth);
        YearMonth previous = current.minusMonths(1);

        BigDecimal currentAvg = getAverageSalaryForDepartment(departmentId, current.getMonthValue(), current.getYear());
        BigDecimal previousAvg = getAverageSalaryForDepartment(departmentId, previous.getMonthValue(), previous.getYear());

        BigDecimal changeAmount = currentAvg.subtract(previousAvg);
        BigDecimal changePercent = previousAvg.compareTo(BigDecimal.ZERO) > 0 ?
                changeAmount.divide(previousAvg, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;

        result.put("currentAverage", currentAvg.setScale(2, RoundingMode.HALF_UP));
        result.put("previousAverage", previousAvg.setScale(2, RoundingMode.HALF_UP));
        result.put("changeAmount", changeAmount.setScale(2, RoundingMode.HALF_UP));
        result.put("changePercent", changePercent.setScale(2, RoundingMode.HALF_UP));
        result.put("trend", changeAmount.compareTo(BigDecimal.ZERO) > 0 ? "growth" : "decline");
        result.put("departmentName", getDepartmentName(departmentId));

        return result;
    }

    public Map<String, Object> getCompanyStatistics(Integer month, Integer year) {
        Map<String, Object> result = new HashMap<>();

        List<Department> departments = departmentRepository.findAll();
        BigDecimal totalFOT = BigDecimal.ZERO;
        Long totalEmployees = 0L;
        BigDecimal avgCompanySalary = BigDecimal.ZERO;

        for (Department department : departments) {
            BigDecimal deptFOT = salaryPaymentRepository
                    .findTotalFOTByDepartmentAndPeriod(department.getId(), month, year);
            Long deptEmployees = salaryPaymentRepository
                    .findEmployeeCountByDepartmentAndPeriod(department.getId(), month, year);

            totalFOT = totalFOT.add(deptFOT);
            totalEmployees += deptEmployees;
        }

        if (totalEmployees > 0) {
            avgCompanySalary = totalFOT.divide(BigDecimal.valueOf(totalEmployees), 2, RoundingMode.HALF_UP);
        }

        result.put("totalFOT", totalFOT.setScale(2, RoundingMode.HALF_UP));
        result.put("totalEmployees", totalEmployees);
        result.put("averageSalary", avgCompanySalary);
        result.put("departmentCount", departments.size());
        result.put("period", month + "/" + year);

        return result;
    }

    private BigDecimal calculateNetSalary(Map<String, BigDecimal> accruals, Map<String, BigDecimal> deductions) {
        BigDecimal totalAccruals = accruals.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDeductions = deductions.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalAccruals.subtract(totalDeductions);
    }

    private BigDecimal getAverageSalaryForDepartment(Integer departmentId, Integer month, Integer year) {
        return salaryPaymentRepository.findAverageSalaryByDepartmentAndPeriod(departmentId, month, year);
    }

    private String getDepartmentName(Integer departmentId) {
        return departmentRepository.findById(departmentId)
                .map(Department::getName)
                .orElse("Неизвестный отдел");
    }
}