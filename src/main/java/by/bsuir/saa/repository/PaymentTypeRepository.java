package by.bsuir.saa.repository;

import by.bsuir.saa.entity.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTypeRepository extends JpaRepository<PaymentType, Integer> {

    Optional<PaymentType> findByCode(String code);

    List<PaymentType> findByCategory(String category);

    List<PaymentType> findByCategoryIn(List<String> categories);

    boolean existsByCode(String code);
}