package com.qiniu.challenge.user;

import java.util.Optional;

public interface UserRepository {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    User save(CreateUserCommand command);

    Optional<User> findById(long id);
}
