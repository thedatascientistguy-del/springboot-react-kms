# KeyManagementSystem (backend skeleton)

This is a generated skeleton for a Key Management System backend (Spring Boot).
It contains starter classes: models, repositories, crypto service, controllers, DTOs.

Run:
  mvn -f pom.xml spring-boot:run

Notes:
 - This skeleton uses H2 in-memory DB for development
 - CryptoService uses X25519 + AES-GCM primitives; ensure your JDK supports X25519 or add a provider (BouncyCastle)
