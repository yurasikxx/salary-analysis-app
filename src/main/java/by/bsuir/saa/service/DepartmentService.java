package by.bsuir.saa.service;

import by.bsuir.saa.entity.Department;
import by.bsuir.saa.repository.DepartmentRepository;
import by.bsuir.saa.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
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

    public boolean departmentExists(String name) {
        return departmentRepository.existsByName(name);
    }

    public Department createDepartment(String name) {
        validateName(name);
        String trimmedName = name.trim();

        validateUniqueName(trimmedName);

        Department department = buildDepartment(trimmedName);
        return departmentRepository.save(department);
    }

    public Department updateDepartment(Integer id, String name) {
        Department department = getExistingDepartment(id);
        validateName(name);

        String trimmedName = name.trim();
        validateUniqueNameForUpdate(department, trimmedName);

        department.setName(trimmedName);
        return departmentRepository.save(department);
    }

    public void deleteDepartment(Integer id) {
        Department department = getExistingDepartment(id);
        validateNoRelatedEmployees(department);
        departmentRepository.delete(department);
    }

    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("Название подразделения не может быть пустым");
        }
    }

    private void validateUniqueName(String name) {
        if (departmentRepository.existsByName(name)) {
            throw new RuntimeException("Подразделение с названием '" + name + "' уже существует");
        }
    }

    private void validateUniqueNameForUpdate(Department department, String newName) {
        if (!department.getName().equals(newName) && departmentRepository.existsByName(newName)) {
            throw new RuntimeException("Подразделение с названием '" + newName + "' уже существует");
        }
    }

    private void validateNoRelatedEmployees(Department department) {
        if (employeeRepository.existsByDepartment(department)) {
            throw new RuntimeException("Невозможно удалить подразделение. Существуют связанные сотрудники.");
        }
    }

    private Department getExistingDepartment(Integer id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Подразделение не найдено"));
    }

    private Department buildDepartment(String name) {
        Department department = new Department();
        department.setName(name);
        return department;
    }
}