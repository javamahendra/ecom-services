# Getting Started

## Auth service

The service stores profile fields in MySQL and manages credentials in Keycloak. Configure a Keycloak realm and a confidential client named `auth-service` with standard authorization-code flow enabled and a service account granted the `manage-users` realm role. Add the Spring Security callback URI (`http://localhost:8080/login/oauth2/code/keycloak` by default) to the client's valid redirect URIs.

Create the MySQL database `auth_service`, then set these environment variables before starting the service:

* `MYSQL_URL` (default `jdbc:mysql://localhost:3306/auth_service`)
* `MYSQL_USER` and `MYSQL_PASSWORD`
* `KEYCLOAK_SERVER_URL` (default `http://localhost:8080`)
* `KEYCLOAK_REALM` (default `app`; this realm must exist in Keycloak)
* `KEYCLOAK_CLIENT_ID` and `KEYCLOAK_CLIENT_SECRET`
* `KEYCLOAK_ISSUER_URI` (default `http://localhost:8080/realms/app`)
* `KEYCLOAK_POST_LOGOUT_REDIRECT_URI` (default `http://localhost:3000`)
* `APP_CORS_ALLOWED_ORIGIN` (default `http://localhost:3000`)

Start browser login at `GET /api/auth/login`; Spring Security completes the OpenID Connect authorization-code flow. End the local and Keycloak sessions with `POST /api/auth/logout` (include the session CSRF token when using cookie authentication). User-management routes under `/api/users` require a Keycloak access token containing the `admin` realm role. The API supports `POST /api/users`, `GET /api/users`, `GET /api/users/{id}`, `PUT /api/users/{id}`, and `DELETE /api/users/{id}`. New-user passwords must be at least 12 characters and are sent only to Keycloak, never stored in MySQL.

The Keycloak admin client uses client-credentials authentication. Enable client authentication and service accounts on the configured client, set its secret, and grant its service account the required `realm-management` roles. `KEYCLOAK_SERVER_URL` must be the Keycloak base URL only (for example `http://localhost:8080` or, from another Compose container, `http://keycloak:8080`), not a realm or token URL. If user creation fails with a Keycloak 404, verify that `KEYCLOAK_REALM` exists at that server URL.

`spring.jpa.hibernate.ddl-auto=update` creates/updates the profile table. Use a migration tool instead of automatic schema updates for production deployments.

### Health, metrics, and logs

The Actuator endpoints exposed by the service are:

* `GET /actuator/health` (also available at `/actuator/health/liveness` and `/actuator/health/readiness`) for health checks; details are hidden.
* `GET /actuator/info` for basic service information.
* `GET /actuator/metrics` for metrics; this endpoint and its subpaths require a Keycloak access token with the `admin` realm role.

Application logs are written to standard output and to `logs/auth-service.log` by default. Set `LOG_FILE` to change the file path. Logging is enabled for the `com.app` package and excludes passwords, tokens, and request bodies.

### Reference Documentation

For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.10/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.0.10/maven-plugin/build-image.html)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/4.0.10/reference/actuator/index.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.0.10/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Java Mail Sender](https://docs.spring.io/spring-boot/4.0.10/reference/io/email.html)
* [Validation](https://docs.spring.io/spring-boot/4.0.10/reference/io/validation.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.0.10/reference/web/servlet.html)

### Guides

The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Accessing data with MySQL](https://spring.io/guides/gs/accessing-data-mysql/)
* [Validation](https://spring.io/guides/gs/validating-form-input/)
* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the
parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.
