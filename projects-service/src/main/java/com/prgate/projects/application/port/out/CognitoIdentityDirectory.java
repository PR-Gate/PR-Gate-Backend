package com.prgate.projects.application.port.out;

import com.prgate.projects.application.model.UserAccount;
import java.util.Optional;

public interface CognitoIdentityDirectory {
    Optional<UserAccount> findByCognitoSub(String cognitoSub);
}
