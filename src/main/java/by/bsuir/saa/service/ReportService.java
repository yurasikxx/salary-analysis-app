package by.bsuir.saa.service;

import by.bsuir.saa.entity.Department;
import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.entity.Payment;
import by.bsuir.saa.entity.SalaryPayment;
import by.bsuir.saa.repository.DepartmentRepository;
import by.bsuir.saa.repository.EmployeeRepository;
import by.bsuir.saa.repository.SalaryPaymentRepository;
import by.bsuir.saa.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final SalaryPaymentRepository salaryPaymentRepository;
    private final PaymentRepository paymentRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    public ReportService(SalaryPaymentRepository salaryPaymentRepository,
                         PaymentRepository paymentRepository,
                         EmployeeRepository employeeRepository,
                         DepartmentRepository departmentRepository) {
        this.salaryPaymentRepository = salaryPaymentRepository;
        this.paymentRepository = paymentRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
    }

    public String generateSalaryReport(Integer month, Integer year) {
        List<SalaryPayment> salaryPayments = salaryPaymentRepository.findByMonthAndYear(month, year);

        StringBuilder report = new StringBuilder();
        report.append("ВЕДОМОСТЬ ПО ЗАРПЛАТЕ\n");
        report.append("За период: ").append(month).append("/").append(year).append("\n\n");

        report.append(String.format("%-30s %-15s %-15s %-15s\n",
                "Сотрудник", "Начисления", "Удержания", "К выплате"));
        report.append("-".repeat(75)).append("\n");

        BigDecimal totalAccrued = BigDecimal.ZERO;
        BigDecimal totalDeducted = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        for (SalaryPayment sp : salaryPayments) {
            report.append(String.format("%-30s %-15.2f %-15.2f %-15.2f\n",
                    sp.getEmployee().getFullName(),
                    sp.getTotalAccrued(),
                    sp.getTotalDeducted(),
                    sp.getNetSalary()));

            totalAccrued = totalAccrued.add(sp.getTotalAccrued());
            totalDeducted = totalDeducted.add(sp.getTotalDeducted());
            totalNet = totalNet.add(sp.getNetSalary());
        }

        report.append("-".repeat(75)).append("\n");
        report.append(String.format("%-30s %-15.2f %-15.2f %-15.2f\n",
                "ИТОГО:", totalAccrued, totalDeducted, totalNet));

        return report.toString();
    }

    public String generateTaxReport(Integer month, Integer year) {
        BigDecimal totalTax = paymentRepository.sumTaxesByPeriod(month, year);

        StringBuilder report = new StringBuilder();
        report.append("ОТЧЕТ ПО НАЛОГАМ И ВЗНОСАМ\n");
        report.append("За период: ").append(month).append("/").append(year).append("\n\n");

        report.append(String.format("Общая сумма налогов и взносов: %.2f руб.\n\n", totalTax));

        List<Object[]> taxDetails = paymentRepository.findTaxDetailsByPeriod(month, year);
        report.append("Детализация:\n");
        for (Object[] detail : taxDetails) {
            String taxName = (String) detail[0];
            BigDecimal amount = (BigDecimal) detail[1];
            report.append(String.format("- %s: %.2f руб.\n", taxName, amount));
        }

        return report.toString();
    }

    public String generatePayslip(Integer employeeId, Integer month, Integer year) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        SalaryPayment salaryPayment = salaryPaymentRepository
                .findByEmployeeAndMonthAndYear(employee, month, year)
                .orElseThrow(() -> new RuntimeException("Расчет зарплаты не найден"));

        List<Payment> payments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);

        StringBuilder payslip = new StringBuilder();

        payslip.append("РАСЧЕТНЫЙ ЛИСТОК\n");
        payslip.append("за ").append(month).append(".").append(year).append("\n\n");

        payslip.append("Сотрудник: ").append(employee.getFullName()).append("\n");
        payslip.append("Должность: ").append(employee.getPosition().getTitle()).append("\n");
        payslip.append("Отдел: ").append(employee.getDepartment().getName()).append("\n");
        payslip.append("Дата приема: ").append(employee.getHireDate()).append("\n\n");

        payslip.append("НАЧИСЛЕНИЯ:\n");
        payslip.append("----------------------------------------\n");

        BigDecimal totalAccrued = BigDecimal.ZERO;
        List<Payment> accruals = payments.stream()
                .filter(p -> p.getPaymentType().getCategory().equals("accrual"))
                .toList();

        for (Payment payment : accruals) {
            payslip.append(String.format("%-25s %10.2f руб.\n",
                    payment.getPaymentType().getName(),
                    payment.getAmount()));
            totalAccrued = totalAccrued.add(payment.getAmount());
        }

        payslip.append("----------------------------------------\n");
        payslip.append(String.format("%-25s %10.2f руб.\n", "Итого начислено:", totalAccrued));
        payslip.append("\n");

        payslip.append("УДЕРЖАНИЯ:\n");
        payslip.append("----------------------------------------\n");

        BigDecimal totalDeducted = BigDecimal.ZERO;
        List<Payment> deductions = payments.stream()
                .filter(p -> p.getPaymentType().getCategory().equals("deduction"))
                .toList();

        for (Payment payment : deductions) {
            payslip.append(String.format("%-25s %10.2f руб.\n",
                    payment.getPaymentType().getName(),
                    payment.getAmount().abs()));
            totalDeducted = totalDeducted.add(payment.getAmount().abs());
        }

        payslip.append("----------------------------------------\n");
        payslip.append(String.format("%-25s %10.2f руб.\n", "Итого удержано:", totalDeducted));
        payslip.append("\n");

        payslip.append("ИТОГИ:\n");
        payslip.append("----------------------------------------\n");
        payslip.append(String.format("%-25s %10.2f руб.\n", "Начислено всего:", totalAccrued));
        payslip.append(String.format("%-25s %10.2f руб.\n", "Удержано всего:", totalDeducted));
        payslip.append(String.format("%-25s %10.2f руб.\n", "К ВЫПЛАТЕ:", salaryPayment.getNetSalary()));
        payslip.append("----------------------------------------\n\n");

        payslip.append("Дата формирования: ").append(java.time.LocalDate.now()).append("\n");
        payslip.append("Бухгалтер: _________________\n");

        return payslip.toString();
    }

    public String generateSalaryStatement(Integer departmentId, Integer month, Integer year) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Отдел не найден"));

        List<SalaryPayment> salaryPayments = salaryPaymentRepository
                .findByDepartmentAndPeriod(departmentId, month, year);

        StringBuilder statement = new StringBuilder();

        statement.append("РАСЧЕТНАЯ ВЕДОМОСТЬ\n");
        statement.append("Отдел: ").append(department.getName()).append("\n");
        statement.append("за ").append(month).append(".").append(year).append("\n\n");

        statement.append(String.format("%-3s %-25s %-20s %12s %12s %12s\n",
                "№", "Сотрудник", "Должность", "Начислено", "Удержано", "К выплате"));
        statement.append("-".repeat(95)).append("\n");

        BigDecimal departmentTotalAccrued = BigDecimal.ZERO;
        BigDecimal departmentTotalDeducted = BigDecimal.ZERO;
        BigDecimal departmentTotalNet = BigDecimal.ZERO;

        int counter = 1;
        for (SalaryPayment sp : salaryPayments) {
            statement.append(String.format("%-3d %-25s %-20s %12.2f %12.2f %12.2f\n",
                    counter++,
                    sp.getEmployee().getFullName(),
                    sp.getEmployee().getPosition().getTitle(),
                    sp.getTotalAccrued(),
                    sp.getTotalDeducted(),
                    sp.getNetSalary()));

            departmentTotalAccrued = departmentTotalAccrued.add(sp.getTotalAccrued());
            departmentTotalDeducted = departmentTotalDeducted.add(sp.getTotalDeducted());
            departmentTotalNet = departmentTotalNet.add(sp.getNetSalary());
        }

        statement.append("-".repeat(95)).append("\n");
        statement.append(String.format("%-49s %12.2f %12.2f %12.2f\n",
                "ИТОГО по отделу:",
                departmentTotalAccrued,
                departmentTotalDeducted,
                departmentTotalNet));

        statement.append("\n");
        statement.append("Количество сотрудников: ").append(salaryPayments.size()).append("\n");
        statement.append("Средняя зарплата: ").append(
                departmentTotalNet.divide(BigDecimal.valueOf(salaryPayments.size()), 2, RoundingMode.HALF_UP)
        ).append(" руб.\n\n");

        statement.append("Дата формирования: ").append(java.time.LocalDate.now()).append("\n");
        statement.append("Главный бухгалтер: _________________\n");

        return statement.toString();
    }
}