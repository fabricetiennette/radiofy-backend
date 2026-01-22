package io.github.fabricetiennette.radiofy.backend.user.services;

import io.github.fabricetiennette.radiofy.backend.user.entities.UserAccount;
import io.github.fabricetiennette.radiofy.backend.user.repositoties.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserAccountRepository repo;
    private final PasswordEncoder encoder;

    @Override
    public void register(String email, String rawPassword) {

        var user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setRole("USER");

        repo.save(user);
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return repo.findByEmail(email);
    }
}
