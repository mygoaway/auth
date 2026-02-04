# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew build              # Build project
./gradlew build -x test      # Build without tests
./gradlew test               # Run all tests
./gradlew test --tests "com.jay.auth.SomeTest"  # Run single test class
./gradlew bootRun            # Run application (requires MySQL, Redis)
./gradlew clean build        # Clean and rebuild
```

## Architecture

This is a Spring Boot 3.x authentication service with email and social login (Google, Kakao, Naver, Facebook).

### Tech Stack
- Java 17, Spring Boot 3.5.x, Spring Security 6.x
- MySQL (JPA/Hibernate), Redis (token storage)
- JWT (jjwt 0.12.6), BCrypt (password hashing)

### Package Structure
```
com.jay.auth
├── config/          # Spring configurations (Security, Redis, JPA, Swagger)
├── controller/      # REST API controllers
├── service/         # Business logic
├── repository/      # JPA repositories
├── domain/
│   ├── entity/      # JPA entities
│   └── enums/       # UserStatus, ChannelCode, VerificationType
├── dto/
│   ├── request/     # Request DTOs
│   └── response/    # Response DTOs
├── security/        # JWT filters, authentication
├── util/            # Encryption utilities
└── exception/       # Custom exceptions
```

### Data Model
- `User` (tb_user): Base user info, 1:0..1 with SignInInfo, 1:N with Channel
- `UserSignInInfo` (tb_user_sign_in_info): Email login credentials (email users only)
- `UserChannel` (tb_user_channel): Login channels (EMAIL, GOOGLE, KAKAO, NAVER, FACEBOOK)
- `EmailVerification` (tb_email_verification): Email verification tokens

### Key Design Decisions
- Email fields stored as: `*_enc` (original encrypted) + `*_lower_enc` (lowercase encrypted for search)
- Social login: only `tb_user` + `tb_user_channel` (no SignInInfo)
- Email login: `tb_user` + `tb_user_sign_in_info` + `tb_user_channel`
- One user can link multiple social accounts

## Environment Variables

```bash
DB_USERNAME, DB_PASSWORD      # MySQL credentials
REDIS_HOST, REDIS_PORT, REDIS_PASSWORD
JWT_SECRET                    # Min 32 chars for HS256
ENCRYPTION_SECRET_KEY         # AES-256 key (32 chars)
```

## Profiles
- `local`: DDL auto create-drop, SQL logging enabled
- `dev`: DDL validate, minimal logging
- `prod`: DDL none, Swagger disabled
