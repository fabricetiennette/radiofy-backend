package io.github.fabricetiennette.radiofy.backend.user.repositoties;

import io.github.fabricetiennette.radiofy.backend.user.entities.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);
//    boolean existsByEmail(String email);
}
