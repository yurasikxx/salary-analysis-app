package by.bsuir.saa.repository;

import by.bsuir.saa.entity.Department;
import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

    List<Employee> findByDepartment(Department department);

    List<Employee> findByPosition(Position position);

    List<Employee> findByTerminationDateIsNull();

    List<Employee> findByFullNameContainingIgnoreCase(String name);

    List<Employee> findByDepartmentIdAndTerminationDateIsNull(Integer departmentId);

    long countByTerminationDateIsNull();

    long countByDepartment(Department department);

    boolean existsByFullName(String fullName);

    @Query("SELECT e FROM Employee e WHERE e.department.name = :departmentName AND e.terminationDate IS NULL")
    List<Employee> findActiveEmployeesByDepartmentName(@Param("departmentName") String departmentName);

    @Query("SELECT e FROM Employee e WHERE e.department = :department AND e.terminationDate IS NULL")
    List<Employee> findByDepartmentAndTerminationDateIsNull(@Param("department") Department department);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.department.id = :departmentId AND e.terminationDate IS NULL")
    long countByDepartmentId(@Param("departmentId") Integer departmentId);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.hireDate > :sinceDate AND e.terminationDate IS NULL")
    Long countByHireDateAfter(@Param("sinceDate") LocalDate sinceDate);

    @Query("SELECT COUNT(e) FROM Employee e WHERE e.terminationDate IS NULL")
    Long countActiveEmployees();

    @Query("SELECT e FROM Employee e LEFT JOIN FETCH e.position LEFT JOIN FETCH e.department WHERE e.terminationDate IS NULL")
    List<Employee> findActiveEmployeesWithDetails();

    @Query("SELECT e FROM Employee e LEFT JOIN FETCH e.position LEFT JOIN FETCH e.department")
    List<Employee> findAllWithDetails();
}