package com.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.app.request.CreateUserRequest;
import com.app.request.LoginRequest;
import com.app.request.RefreshTokenRequest;
import com.app.request.UserRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class RequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void loginRequiresUsernameAndPassword() {
        assertTrue(validator.validate(new LoginRequest(" ", "")).size() >= 2);
    }

    @Test
    void refreshRequiresNonBlankToken() {
        assertTrue(validator.validate(new RefreshTokenRequest(" ")).size() >= 1);
    }

    @Test
    void createUserValidatesPasswordAndProfileFields() {
        CreateUserRequest request = new CreateUserRequest(
                "short", " ", "not-an-email", "", " ");

        assertTrue(validator.validate(request).size() >= 5);
    }

    @Test
    void userUpdateRequiresValidEmailAndNames() {
        UserRequest request = new UserRequest(" ", "invalid-email", "", " ");

        assertTrue(validator.validate(request).size() >= 4);
    }
}
