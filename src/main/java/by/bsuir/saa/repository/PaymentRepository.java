package by.bsuir.saa.repository;

import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.entity.Payment;
import by.bsuir.saa.entity.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    List<Payment> findByEmployeeAndMonthAndYear(Employee employee, Integer month, Integer year);

    Optional<Payment> findByEmployeeAndMonthAndYearAndPaymentType(Employee employee,
                                                                  Integer month,
                                                                  Integer year,
                                                                  PaymentType paymentType);

    List<Payment> findByPaymentType(PaymentType paymentType);

    List<Payment> findByMonthAndYear(Integer month, Integer year);

    @Query("SELECT p FROM Payment p WHERE p.employee.department.id = :departmentId AND p.month = :month AND p.year = :year AND p.paymentType.category = :category")
    List<Payment> findByDepartmentAndPeriodAndCategory(@Param("departmentId") Integer departmentId,
                                                       @Param("month") Integer month,
                                                       @Param("year") Integer year,
                                                       @Param("category") String category);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE p.employee = :employee AND p.month = :month AND p.year = :year " +
            "AND p.paymentType.category = :category")
    BigDecimal sumAmountByEmployeeAndPeriodAndCategory(@Param("employee") Employee employee,
                                                       @Param("month") Integer month,
                                                       @Param("year") Integer year,
                                                       @Param("category") String category);

    @Query("SELECT COALESCE(SUM(ABS(p.amount)), 0) FROM Payment p " +
            "WHERE p.month = :month AND p.year = :year " +
            "AND p.paymentType.category = 'deduction'")
    BigDecimal sumTaxesByPeriod(@Param("month") Integer month, @Param("year") Integer year);

    @Query("SELECT p.paymentType.name, SUM(ABS(p.amount)) " +
            "FROM Payment p " +
            "WHERE p.month = :month AND p.year = :year " +
            "AND p.paymentType.category = 'deduction' " +
            "GROUP BY p.paymentType.name")
    List<Object[]> findTaxDetailsByPeriod(@Param("month") Integer month, @Param("year") Integer year);
}