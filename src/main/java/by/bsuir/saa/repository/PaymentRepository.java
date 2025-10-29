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

    List<Payment> findByPaymentType(PaymentType paymentType);

    List<Payment> findByMonthAndYear(Integer month, Integer year);

    boolean existsByPaymentType(PaymentType paymentType);

    @Query("SELECT p FROM Payment p WHERE p.employee = :employee AND p.month = :month AND p.year = :year AND p.paymentType = :paymentType")
    Optional<Payment> findByEmployeeAndMonthAndYearAndPaymentType(@Param("employee") Employee employee,
                                                                  @Param("month") Integer month,
                                                                  @Param("year") Integer year,
                                                                  @Param("paymentType") PaymentType paymentType);

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

    @Query("SELECT COUNT(DISTINCT p.employee.id) FROM Payment p WHERE p.month = :month AND p.year = :year")
    Long countDistinctEmployeesByMonthAndYear(@Param("month") Integer month,
                                              @Param("year") Integer year);

    @Query("SELECT COUNT(DISTINCT p.employee.id) FROM Payment p WHERE p.month = :month AND p.year = :year AND p.paymentType.category = 'accrual'")
    Long countEmployeesWithAccruals(@Param("month") Integer month,
                                    @Param("year") Integer year);

    @Query("SELECT p FROM Payment p " +
            "WHERE p.month = :month AND p.year = :year " +
            "AND p.paymentType.category = 'deduction' " +
            "ORDER BY p.employee.fullName, p.paymentType.name")
    List<Payment> findTaxPaymentsByPeriod(@Param("month") Integer month,
                                          @Param("year") Integer year);

    @Query("SELECT p FROM Payment p " +
            "WHERE p.month = :month AND p.year = :year " +
            "AND p.paymentType.category = 'deduction' " +
            "AND p.employee.department.id = :departmentId " +
            "ORDER BY p.employee.fullName, p.paymentType.name")
    List<Payment> findTaxPaymentsByDepartmentAndPeriod(@Param("departmentId") Integer departmentId,
                                                       @Param("month") Integer month,
                                                       @Param("year") Integer year);

    @Query("SELECT COALESCE(SUM(ABS(p.amount)), 0) FROM Payment p " +
            "WHERE p.employee.id = :employeeId AND p.month = :month AND p.year = :year " +
            "AND p.paymentType.category = 'deduction'")
    BigDecimal sumTaxesByEmployeeAndPeriod(@Param("employeeId") Integer employeeId,
                                           @Param("month") Integer month,
                                           @Param("year") Integer year);

    @Query("SELECT p.paymentType, COUNT(p), SUM(ABS(p.amount)) " +
            "FROM Payment p " +
            "WHERE p.month = :month AND p.year = :year " +
            "AND p.paymentType.category = 'deduction' " +
            "GROUP BY p.paymentType " +
            "ORDER BY SUM(ABS(p.amount)) DESC")
    List<Object[]> findTaxStatisticsByType(@Param("month") Integer month,
                                           @Param("year") Integer year);

    @Query("SELECT COUNT(p) > 0 FROM Payment p WHERE p.employee = :employee AND p.month = :month AND p.year = :year " +
            "AND p.paymentType.category = 'deduction' AND p.paymentType.code IN ('ПН', 'ФСЗН')")
    boolean hasTaxesForEmployee(@Param("employee") Employee employee,
                                @Param("month") Integer month,
                                @Param("year") Integer year);
}