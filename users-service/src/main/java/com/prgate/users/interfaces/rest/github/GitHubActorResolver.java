package com.prgate.users.interfaces.rest.github;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
public interface GitHubActorResolver { UUID resolve(HttpServletRequest request); }
