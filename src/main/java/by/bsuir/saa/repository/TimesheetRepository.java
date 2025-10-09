package by.bsuir.saa.repository;

import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.entity.Timesheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TimesheetRepository extends JpaRepository<Timesheet, Integer> {

    Optional<Timesheet> findByEmployeeAndMonthAndYear(Employee employee, Integer month, Integer year);

    List<Timesheet> findByEmployee(Employee employee);

    @Query("SELECT t FROM Timesheet t WHERE t.employee.department.id = :departmentId AND t.month = :month AND t.year = :year")
    List<Timesheet> findByDepartmentAndPeriod(@Param("departmentId") Integer departmentId,
                                              @Param("month") Integer month,
                                              @Param("year") Integer year);

    boolean existsByEmployeeAndMonthAndYear(Employee employee, Integer month, Integer year);
}