package by.bsuir.saa.service;

import by.bsuir.saa.entity.Position;
import by.bsuir.saa.repository.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class PositionService {

    private final PositionRepository positionRepository;

    public PositionService(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
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

    @Transactional
    public Position createPosition(String title, Double baseSalary) {
        if (positionRepository.existsByTitle(title)) {
            throw new RuntimeException("Должность с таким названием уже существует: " + title);
        }

        Position position = new Position();
        position.setTitle(title);
        position.setBaseSalary(java.math.BigDecimal.valueOf(baseSalary));

        return positionRepository.save(position);
    }

    @Transactional
    public Position updatePosition(Integer id, String title, Double baseSalary) {
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Должность не найдена"));

        if (!position.getTitle().equals(title) && positionRepository.existsByTitle(title)) {
            throw new RuntimeException("Должность с таким названием уже существует: " + title);
        }

        position.setTitle(title);
        position.setBaseSalary(java.math.BigDecimal.valueOf(baseSalary));

        return positionRepository.save(position);
    }

    @Transactional
    public void deletePosition(Integer id) {
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Должность не найдена"));
        positionRepository.delete(position);
    }
}