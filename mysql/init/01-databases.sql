CREATE DATABASE IF NOT EXISTS keycloak;
CREATE DATABASE IF NOT EXISTS auth_service;
CREATE DATABASE IF NOT EXISTS product_service;

GRANT ALL PRIVILEGES ON keycloak.* TO 'auth_service'@'%';
GRANT ALL PRIVILEGES ON auth_service.* TO 'auth_service'@'%';
GRANT ALL PRIVILEGES ON product_service.* TO 'auth_service'@'%';
