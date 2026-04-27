package io.github.fabricetiennette.radiofy.backend.user.repositoties;

import io.github.fabricetiennette.radiofy.backend.user.entities.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);

    Optional<UserAccount> findByAppleSubject(String appleSubject);
    boolean existsByAppleSubject(String appleSubject);
}
