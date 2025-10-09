package by.bsuir.saa.repository;

import by.bsuir.saa.entity.MarkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MarkTypeRepository extends JpaRepository<MarkType, Integer> {

    Optional<MarkType> findByCode(String code);
}