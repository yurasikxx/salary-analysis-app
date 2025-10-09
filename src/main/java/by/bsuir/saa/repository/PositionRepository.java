package by.bsuir.saa.repository;

import by.bsuir.saa.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Integer> {

    Optional<Position> findByTitle(String title);

    boolean existsByTitle(String title);
}