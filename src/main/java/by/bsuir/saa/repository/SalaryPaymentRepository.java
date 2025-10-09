package by.bsuir.saa.repository;

import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.entity.SalaryPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalaryPaymentRepository extends JpaRepository<SalaryPayment, Integer> {

    Optional<SalaryPayment> findByEmployeeAndMonthAndYear(Employee employee, Integer month, Integer year);

    List<SalaryPayment> findByEmployee(Employee employee);

    @Query("SELECT sp FROM SalaryPayment sp WHERE sp.employee.department.id = :departmentId AND sp.month = :month AND sp.year = :year")
    List<SalaryPayment> findByDepartmentAndPeriod(@Param("departmentId") Integer departmentId,
                                                  @Param("month") Integer month,
                                                  @Param("year") Integer year);

    @Query("SELECT sp.employee.department.name, AVG(sp.netSalary) FROM SalaryPayment sp WHERE sp.month = :month AND sp.year = :year GROUP BY sp.employee.department")
    List<Object[]> findAverageSalaryByDepartmentAndPeriod(@Param("month") Integer month,
                                                          @Param("year") Integer year);
}