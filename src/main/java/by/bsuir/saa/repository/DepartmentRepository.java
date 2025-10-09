package by.bsuir.saa.repository;

import by.bsuir.saa.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Integer> {

    Optional<Department> findByName(String name);

    boolean existsByName(String name);
}