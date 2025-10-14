package by.bsuir.saa.service;

import by.bsuir.saa.entity.Department;
import by.bsuir.saa.repository.DepartmentRepository;
import by.bsuir.saa.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    public DepartmentService(DepartmentRepository departmentRepository,
                             EmployeeRepository employeeRepository) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    public Optional<Department> getDepartmentById(Integer id) {
        return departmentRepository.findById(id);
    }

    public Optional<Department> getDepartmentByName(String name) {
        return departmentRepository.findByName(name);
    }

    @Transactional
    public Department createDepartment(String name) {
        if (departmentRepository.existsByName(name)) {
            throw new RuntimeException("Отдел с таким названием уже существует: " + name);
        }

        Department department = new Department();
        department.setName(name);

        return departmentRepository.save(department);
    }

    @Transactional
    public Department updateDepartment(Integer id, String name) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Отдел не найден"));

        if (!department.getName().equals(name) && departmentRepository.existsByName(name)) {
            throw new RuntimeException("Отдел с таким названием уже существует: " + name);
        }

        department.setName(name);
        return departmentRepository.save(department);
    }

    @Transactional
    public void deleteDepartment(Integer id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Отдел не найден"));
        departmentRepository.delete(department);
    }

    public long getEmployeeCountByDepartment(Integer departmentId) {
        return employeeRepository.countByDepartmentId(departmentId);
    }
}