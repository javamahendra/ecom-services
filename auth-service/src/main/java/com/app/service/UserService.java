package com.app.service;

import com.app.entity.UserProfile;
import com.app.event.UserEvent;
import com.app.repository.UserProfileRepository;
import com.app.request.CreateUserRequest;
import com.app.request.AdminCreateUserRequest;
import com.app.request.UserRequest;
import com.app.response.AdminUserResponse;
import com.app.response.UserResponse;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private static final Set<String> MANAGED_ROLES = Set.of("admin", "manager", "user");
    private final UserProfileRepository profiles;
    private final UsersResource keycloakUsers;
    private final RealmResource keycloakRealm;
    private final KafkaTemplate<Object, Object> userEvents;

    public UserService(
            UserProfileRepository profiles,
            Keycloak keycloak,
            KafkaTemplate<Object, Object> userEvents,
            @Value("${keycloak.realm}") String realm) {
        this.profiles = profiles;
        this.keycloakRealm = keycloak.realm(realm);
        this.keycloakUsers = keycloakRealm.users();
        this.userEvents = userEvents;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        return createWithRole(request, "user");
    }

    @Transactional
    public AdminUserResponse createByAdmin(AdminCreateUserRequest request) {
        UserResponse user = createWithRole(new CreateUserRequest(
                request.password(), request.profile().username(), request.profile().email(),
                request.profile().firstName(), request.profile().lastName(), request.profile().phone()),
                request.role());
        return findAdminUser(user.id());
    }

    private UserResponse createWithRole(CreateUserRequest request, String roleName) {
        RoleRepresentation role = findManagedRole(roleName);
        UserRepresentation user = representation(request.profile());
        user.setCredentials(List.of(passwordCredential(request.password())));

        String id;
        try (Response response = keycloakUsers.create(user)) {
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                throw statusException(response.getStatus(), "Keycloak could not create the user");
            }
            URI location = response.getLocation();
            if (location == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak did not return the created user ID");
            }
            String path = location.getPath();
            id = path.substring(path.lastIndexOf('/') + 1);
            if (id.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak returned an invalid user ID");
            }
        } catch (WebApplicationException exception) {
            throw statusException(exception.getResponse().getStatus(), "Keycloak could not create the user");
        } catch (ProcessingException exception) {
            throw keycloakConnectionException(exception);
        }

        try {
            keycloakUsers.get(id).roles().realmLevel().add(List.of(role));
        } catch (WebApplicationException | ProcessingException exception) {
            try {
                deleteKeycloakUser(id, "Could not roll back user after role assignment failed");
            } catch (ResponseStatusException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            if (exception instanceof WebApplicationException webException) {
                throw statusException(webException.getResponse().getStatus(), "Could not assign the user's role");
            }
            throw keycloakConnectionException((ProcessingException) exception);
        }

        UserRequest profile = request.profile();
        try {
            UserResponse created = UserResponse.from(profiles.save(new UserProfile(
                    id, profile.username(), profile.email(), profile.firstName(), profile.lastName(), profile.phone())));
            publish(created, "USER_CREATED");
            logger.info("User account and profile created");
            return created;
        } catch (DataAccessException exception) {
            logger.error("Could not save user profile to MySQL; rolling back Keycloak user");
            try {
                deleteKeycloakUser(id, "Could not roll back user creation in Keycloak");
            } catch (ResponseStatusException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            if (exception instanceof DataIntegrityViolationException) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "The user profile conflicts with an existing MySQL profile", exception);
            }
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "The user profile could not be saved in MySQL", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        logger.debug("Listing user profiles");
        return profiles.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> findAllWithRoles() {
        return profiles.findAll().stream().map(profile -> findAdminUser(profile.getId())).toList();
    }

    @Transactional(readOnly = true)
    public long count() {
        return profiles.count();
    }

    @Transactional
    public AdminUserResponse updateRole(String id, String roleName) {
        RoleRepresentation newRole = findManagedRole(roleName);
        findProfile(id);
        try {
            var roleMappings = keycloakUsers.get(id).roles().realmLevel();
            List<RoleRepresentation> managedRoles = roleMappings.listAll().stream()
                    .filter(role -> MANAGED_ROLES.contains(role.getName()))
                    .toList();
            if (!managedRoles.isEmpty()) roleMappings.remove(managedRoles);
            roleMappings.add(List.of(newRole));
            logger.info("User role updated: userId={}, role={}", id, roleName);
            return findAdminUser(id);
        } catch (WebApplicationException exception) {
            throw statusException(exception.getResponse().getStatus(), "Could not update the user's role");
        } catch (ProcessingException exception) {
            throw keycloakConnectionException(exception);
        }
    }

    @Transactional(readOnly = true)
    public UserResponse findById(String id) {
        logger.debug("Fetching user profile");
        return UserResponse.from(findProfile(id));
    }

    @Transactional
    public UserResponse update(String id, UserRequest request) {
        UserProfile profile = findProfile(id);
        UserRepresentation user;
        try {
            user = keycloakUsers.get(id).toRepresentation();
        } catch (WebApplicationException exception) {
            throw statusException(exception.getResponse().getStatus(), "Keycloak could not find the user");
        } catch (ProcessingException exception) {
            throw keycloakConnectionException(exception);
        }
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        try {
            keycloakUsers.get(id).update(user);
            profile.update(request.username(), request.email(), request.firstName(), request.lastName(), request.phone());
            UserResponse updated = UserResponse.from(profiles.save(profile));
            publish(updated, "USER_UPDATED");
            logger.info("User account and profile updated");
            return updated;
        } catch (WebApplicationException exception) {
            logger.warn("Keycloak user update failed with HTTP status {}", exception.getResponse().getStatus());
            throw statusException(exception.getResponse().getStatus(), "Keycloak could not update the user");
        } catch (ProcessingException exception) {
            throw keycloakConnectionException(exception);
        }
    }

    @Transactional
    public void delete(String id) {
        UserResponse deleted = UserResponse.from(findProfile(id));
        deleteKeycloakUser(id, "Keycloak could not delete the user");
        profiles.deleteById(id);
        publish(deleted, "USER_DELETED");
        logger.info("User account and profile deleted");
    }

    public void forgotPassword(String email) {
        profiles.findByEmailIgnoreCase(email).ifPresent(profile -> {
            try {
                keycloakUsers.get(profile.getId()).executeActionsEmail(List.of("UPDATE_PASSWORD"));
                UserResponse user = UserResponse.from(profile);
                publish(user, "FORGOT_PASSWORD");
                logger.info("Password reset requested for userId={}", user.id());
            } catch (WebApplicationException exception) {
                throw statusException(exception.getResponse().getStatus(),
                        "Could not request password reset");
            } catch (ProcessingException exception) {
                throw keycloakConnectionException(exception);
            }
        });
    }

    public void resetPassword(String id, String newPassword) {
        UserProfile profile = findProfile(id);
        try {
            keycloakUsers.get(id).resetPassword(passwordCredential(newPassword));
            publish(UserResponse.from(profile), "PASSWORD_RESET");
            logger.info("Password reset completed for userId={}", id);
        } catch (WebApplicationException exception) {
            throw statusException(exception.getResponse().getStatus(), "Could not reset password");
        } catch (ProcessingException exception) {
            throw keycloakConnectionException(exception);
        }
    }

    private void publish(UserResponse user, String eventType) {
        Map<String, String> data = Map.of(
                "username", user.username(),
                "firstName", user.firstName(),
                "lastName", user.lastName(),
                "name", user.firstName() + " " + user.lastName());
        String eventId = UUID.randomUUID().toString();
        userEvents.send("commerce.user-events", user.id(), new UserEvent(
                eventId, eventType, user.id(), user.email(), user.phone(), data, Instant.now()))
                .whenComplete((result, error) -> {
                    if (error != null) logger.error("Could not publish user lifecycle event: userId={}, type={}",
                            user.id(), eventType, error);
                });
    }

    private UserProfile findProfile(String id) {
        return profiles.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private AdminUserResponse findAdminUser(String id) {
        UserResponse user = UserResponse.from(findProfile(id));
        try {
            List<String> roles = keycloakUsers.get(id).roles().realmLevel().listAll().stream()
                    .map(RoleRepresentation::getName)
                    .filter(MANAGED_ROLES::contains)
                    .sorted()
                    .collect(Collectors.toList());
            if (roles.isEmpty()) roles = List.of("user");
            boolean enabled = Boolean.TRUE.equals(keycloakUsers.get(id).toRepresentation().isEnabled());
            return new AdminUserResponse(user.id(), user.username(), user.email(), user.firstName(),
                    user.lastName(), user.phone(), roles, enabled);
        } catch (WebApplicationException exception) {
            throw statusException(exception.getResponse().getStatus(), "Could not read the user's roles");
        } catch (ProcessingException exception) {
            throw keycloakConnectionException(exception);
        }
    }

    @Transactional
    public AdminUserResponse updateStatus(String id, boolean enabled) {
        findProfile(id);
        try {
            var userResource = keycloakUsers.get(id);
            UserRepresentation user = userResource.toRepresentation();
            user.setEnabled(enabled);
            userResource.update(user);
            logger.info("User account status updated: userId={}, enabled={}", id, enabled);
            return findAdminUser(id);
        } catch (WebApplicationException exception) {
            throw statusException(exception.getResponse().getStatus(), "Could not update the user's status");
        } catch (ProcessingException exception) {
            throw keycloakConnectionException(exception);
        }
    }

    private RoleRepresentation findManagedRole(String roleName) {
        if (!MANAGED_ROLES.contains(roleName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be admin, manager, or user");
        }
        try {
            RoleRepresentation role = keycloakRealm.roles().get(roleName).toRepresentation();
            if (role == null) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "The " + roleName + " realm role is not configured in Keycloak");
            }
            return role;
        } catch (WebApplicationException exception) {
            if (exception.getResponse().getStatus() == 404) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "The " + roleName + " realm role is not configured in Keycloak", exception);
            }
            throw statusException(exception.getResponse().getStatus(), "Could not load the requested role");
        } catch (ProcessingException exception) {
            throw keycloakConnectionException(exception);
        }
    }

    private static UserRepresentation representation(UserRequest request) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(true);
        user.setEmailVerified(false);
        return user;
    }

    private static CredentialRepresentation passwordCredential(String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        return credential;
    }

    private void deleteKeycloakUser(String id, String message) {
        try (Response response = keycloakUsers.delete(id)) {
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw statusException(response.getStatus(), message);
            }
        } catch (WebApplicationException exception) {
            throw statusException(exception.getResponse().getStatus(), message);
        } catch (ProcessingException exception) {
            throw keycloakConnectionException(exception);
        }
    }

    private static ResponseStatusException keycloakConnectionException(ProcessingException exception) {
        logger.error(
                "Keycloak admin request failed; verify KEYCLOAK_SERVER_URL, KEYCLOAK_REALM, and service-account client settings",
                exception);
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Could not reach the Keycloak admin API. Verify KEYCLOAK_SERVER_URL, KEYCLOAK_REALM, and the service-account client configuration",
                exception);
    }

    private static ResponseStatusException statusException(int status, String message) {
        HttpStatus httpStatus = HttpStatus.resolve(status);
        if (httpStatus == null || httpStatus.is5xxServerError()) {
            httpStatus = HttpStatus.BAD_GATEWAY;
        }
        return new ResponseStatusException(httpStatus, message);
    }
}
