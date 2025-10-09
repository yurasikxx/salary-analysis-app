package by.bsuir.saa.repository;

import by.bsuir.saa.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Integer> {

    Optional<UserRole> findByName(String name);
}