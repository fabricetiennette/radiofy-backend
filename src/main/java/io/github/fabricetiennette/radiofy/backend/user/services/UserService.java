package io.github.fabricetiennette.radiofy.backend.user.services;

import io.github.fabricetiennette.radiofy.backend.user.entities.UserAccount;

import java.util.Optional;

public interface UserService {
    void register(String email, String rawPassword);
    void markEmailVerified(String email);
//    void archiveAndDeleteByEmail(String email);
    Optional<UserAccount> findByEmail(String email);
//    Optional<UserAccount>  delete(UserAccount userAccount);
}
