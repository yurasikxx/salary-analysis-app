package by.bsuir.saa.repository;

import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.entity.SalaryPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface SalaryPaymentRepository extends JpaRepository<SalaryPayment, Integer> {

    Optional<SalaryPayment> findByEmployeeAndMonthAndYear(Employee employee, Integer month, Integer year);

    List<SalaryPayment> findByEmployee(Employee employee);

    List<SalaryPayment> findByMonthAndYear(Integer month, Integer year);

    @Query("SELECT sp FROM SalaryPayment sp WHERE sp.employee.department.id = :departmentId AND sp.month = :month AND sp.year = :year")
    List<SalaryPayment> findByDepartmentAndPeriod(@Param("departmentId") Integer departmentId,
                                                  @Param("month") Integer month,
                                                  @Param("year") Integer year);

    @Query("SELECT sp.employee.department.name, sp.month, sp.year, AVG(sp.netSalary), COUNT(sp.employee) " +
            "FROM SalaryPayment sp " +
            "WHERE sp.employee.department.id = :departmentId " +
            "AND ((sp.year = :startYear AND sp.month >= :startMonth) OR sp.year > :startYear) " +
            "AND ((sp.year = :endYear AND sp.month <= :endMonth) OR sp.year < :endYear) " +
            "GROUP BY sp.employee.department.name, sp.month, sp.year " +
            "ORDER BY sp.year, sp.month")
    List<Object[]> findAverageSalaryByDepartmentAndPeriodRange(@Param("departmentId") Integer departmentId,
                                                               @Param("startMonth") Integer startMonth,
                                                               @Param("startYear") Integer startYear,
                                                               @Param("endMonth") Integer endMonth,
                                                               @Param("endYear") Integer endYear);

    @Query("SELECT sp.employee.department.name, AVG(sp.netSalary), SUM(sp.netSalary) " +
            "FROM SalaryPayment sp " +
            "WHERE sp.month = :month AND sp.year = :year " +
            "GROUP BY sp.employee.department.name")
    List<Object[]> findAverageSalaryByDepartmentAndPeriod(@Param("month") Integer month,
                                                          @Param("year") Integer year);

    @Query("SELECT sp FROM SalaryPayment sp " +
            "WHERE sp.month = :month AND sp.year = :year " +
            "ORDER BY sp.netSalary DESC " +
            "LIMIT :limit")
    List<SalaryPayment> findTopByMonthAndYearOrderByNetSalaryDesc(@Param("month") Integer month,
                                                                  @Param("year") Integer year,
                                                                  @Param("limit") int limit);

    @Query("SELECT COALESCE(AVG(sp.netSalary), 0) FROM SalaryPayment sp " +
            "WHERE sp.employee.department.id = :departmentId " +
            "AND sp.month = :month AND sp.year = :year")
    BigDecimal findAverageSalaryByDepartmentAndPeriod(@Param("departmentId") Integer departmentId,
                                                      @Param("month") Integer month,
                                                      @Param("year") Integer year);

    @Query("SELECT COALESCE(SUM(sp.netSalary), 0) FROM SalaryPayment sp " +
            "WHERE sp.employee.department.id = :departmentId " +
            "AND sp.month = :month AND sp.year = :year")
    BigDecimal findTotalFOTByDepartmentAndPeriod(@Param("departmentId") Integer departmentId,
                                                 @Param("month") Integer month,
                                                 @Param("year") Integer year);

    @Query("SELECT COUNT(DISTINCT sp.employee) FROM SalaryPayment sp " +
            "WHERE sp.employee.department.id = :departmentId " +
            "AND sp.month = :month AND sp.year = :year")
    Long findEmployeeCountByDepartmentAndPeriod(@Param("departmentId") Integer departmentId,
                                                @Param("month") Integer month,
                                                @Param("year") Integer year);

    @Query("SELECT COALESCE(SUM(ABS(p.amount)), 0) FROM Payment p " +
            "WHERE p.month = :month AND p.year = :year " +
            "AND p.paymentType.category = 'deduction'")
    BigDecimal sumTaxesByPeriod(@Param("month") Integer month, @Param("year") Integer year);

    @Query("SELECT sp.employee.department.name, AVG(sp.netSalary), SUM(sp.netSalary), COUNT(sp.employee) " +
            "FROM SalaryPayment sp " +
            "WHERE sp.month = :month AND sp.year = :year " +
            "GROUP BY sp.employee.department.name")
    List<Object[]> findDepartmentSummary(@Param("month") Integer month, @Param("year") Integer year);


    @Query("SELECT new map(d.name as departmentName, AVG(sp.netSalary) as avgSalary, " +
            "SUM(sp.netSalary) as totalFOT, COUNT(sp) as employeeCount) " +
            "FROM SalaryPayment sp " +
            "JOIN sp.employee e " +
            "JOIN e.department d " +
            "WHERE sp.month = :month AND sp.year = :year " +
            "GROUP BY d.id, d.name " +
            "ORDER BY AVG(sp.netSalary) DESC")
    List<Map<String, Object>> findDepartmentStatsForPeriod(@Param("month") Integer month,
                                                           @Param("year") Integer year);

    @Query("SELECT sp FROM SalaryPayment sp " +
            "WHERE sp.month = :month AND sp.year = :year " +
            "ORDER BY sp.netSalary DESC " +
            "LIMIT :limit")
    List<SalaryPayment> findTopByMonthAndYearOrderByNetSalaryDesc(@Param("month") Integer month,
                                                                  @Param("year") Integer year,
                                                                  @Param("limit") Integer limit);
}