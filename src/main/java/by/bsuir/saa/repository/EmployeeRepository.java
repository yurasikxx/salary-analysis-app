package by.bsuir.saa.repository;

import by.bsuir.saa.entity.Department;
import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

    List<Employee> findByDepartment(Department department);

    List<Employee> findByPosition(Position position);

    List<Employee> findByTerminationDateIsNull();

    List<Employee> findByFullNameContainingIgnoreCase(String name);

    @Query("SELECT e FROM Employee e WHERE e.department.name = :departmentName AND e.terminationDate IS NULL")
    List<Employee> findActiveEmployeesByDepartmentName(@Param("departmentName") String departmentName);

    boolean existsByFullName(String fullName);

    @Query("SELECT e FROM Employee e WHERE e.department = :department AND e.terminationDate IS NULL")
    List<Employee> findByDepartmentAndTerminationDateIsNull(@Param("department") Department department);

}