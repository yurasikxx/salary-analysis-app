package by.bsuir.saa.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "salary_payments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "month", "year"}))
public class SalaryPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "month", nullable = false)
    private Integer month;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "total_accrued", precision = 10, scale = 2)
    private BigDecimal totalAccrued = BigDecimal.ZERO;

    @Column(name = "total_deducted", precision = 10, scale = 2)
    private BigDecimal totalDeducted = BigDecimal.ZERO;

    @Column(name = "net_salary", precision = 10, scale = 2)
    private BigDecimal netSalary = BigDecimal.ZERO;

    @Column(name = "calculation_date")
    private LocalDateTime calculationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private SalaryStatus status = SalaryStatus.CALCULATED;

    @PrePersist
    protected void onCreate() {
        if (calculationDate == null) {
            calculationDate = LocalDateTime.now();
        }
        if (status == null) {
            status = SalaryStatus.CALCULATED;
        }
        calculateNetSalary();
    }

    public void calculateNetSalary() {
        this.netSalary = totalAccrued.subtract(totalDeducted);
    }

    public enum SalaryStatus {
        CALCULATED, PAID
    }
}