package by.bsuir.saa.service;

import by.bsuir.saa.entity.Position;
import by.bsuir.saa.repository.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PositionService {

    private final PositionRepository positionRepository;
    private final EmployeeService employeeService;

    public PositionService(PositionRepository positionRepository, EmployeeService employeeService) {
        this.positionRepository = positionRepository;
        this.employeeService = employeeService;
    }

    public List<Position> getAllPositions() {
        return positionRepository.findAll();
    }

    public Optional<Position> getPositionById(Integer id) {
        return positionRepository.findById(id);
    }

    public Optional<Position> getPositionByTitle(String title) {
        return positionRepository.findByTitle(title);
    }

    public boolean positionExists(String title) {
        return positionRepository.existsByTitle(title);
    }

    public Position createPosition(String title, BigDecimal baseSalary) {
        if (positionRepository.existsByTitle(title)) {
            throw new RuntimeException("Должность с названием '" + title + "' уже существует");
        }

        if (title == null || title.trim().isEmpty()) {
            throw new RuntimeException("Название должности не может быть пустым");
        }

        if (baseSalary == null || baseSalary.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Оклад должен быть положительным числом");
        }

        Position position = new Position();
        position.setTitle(title.trim());
        position.setBaseSalary(baseSalary);

        return positionRepository.save(position);
    }

    public Position updatePosition(Integer id, String title, BigDecimal baseSalary) {
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Должность не найдена"));

        if (!position.getTitle().equals(title) && positionRepository.existsByTitle(title)) {
            throw new RuntimeException("Должность с названием '" + title + "' уже существует");
        }

        if (title == null || title.trim().isEmpty()) {
            throw new RuntimeException("Название должности не может быть пустым");
        }

        if (baseSalary == null || baseSalary.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Оклад должен быть положительным числом");
        }

        position.setTitle(title.trim());
        position.setBaseSalary(baseSalary);

        return positionRepository.save(position);
    }

    public void deletePosition(Integer id) {
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Должность не найдена"));

        List<by.bsuir.saa.entity.Employee> employeesOnPosition = employeeService.findByPosition(position);
        if (!employeesOnPosition.isEmpty()) {
            throw new RuntimeException("Невозможно удалить должность. Есть сотрудники на этой должности: " +
                    employeesOnPosition.size() + " чел.");
        }

        positionRepository.delete(position);
    }
}