package by.bsuir.saa.service;

import by.bsuir.saa.entity.Department;
import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.entity.Position;
import by.bsuir.saa.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    public Optional<Employee> getEmployeeById(Integer id) {
        return employeeRepository.findById(id);
    }

    public List<Employee> searchEmployeesByName(String name) {
        return employeeRepository.findByFullNameContainingIgnoreCase(name);
    }

    public Employee createEmployee(String fullName, LocalDate hireDate,
                                   Position position, Department department) {
        Employee employee = new Employee();
        employee.setFullName(fullName);
        employee.setHireDate(hireDate);
        employee.setPosition(position);
        employee.setDepartment(department);

        return employeeRepository.save(employee);
    }

    public Employee updateEmployee(Integer id, String fullName, Position position,
                                   Department department) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        employee.setFullName(fullName);
        employee.setPosition(position);
        employee.setDepartment(department);

        return employeeRepository.save(employee);
    }

    public void terminateEmployee(Integer id, LocalDate terminationDate) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        employee.setTerminationDate(terminationDate);
        employeeRepository.save(employee);
    }

    public long getEmployeeCountByDepartment(Department department) {
        return employeeRepository.findByDepartment(department).size();
    }
}