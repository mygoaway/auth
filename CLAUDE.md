# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

### Backend (Spring Boot)
```bash
./gradlew build              # Build project
./gradlew build -x test      # Build without tests
./gradlew test               # Run all tests
./gradlew test --tests "com.jay.auth.SomeTest"  # Run single test class
./gradlew bootRun            # Run application (requires MySQL, Redis)
./gradlew clean build        # Clean and rebuild
```

### Frontend (React + Vite)
```bash
cd frontend
npm install                  # Install dependencies
npm run dev                  # Start dev server (default: port 5173)
npx vite --port 3000         # Start on specific port
npm run build                # Production build
npm run lint                 # Run ESLint
```

## Architecture

This is a Spring Boot 3.x authentication service with email and social login (Google, Kakao, Naver, Facebook), plus a React frontend.

### Tech Stack
- **Backend**: Java 17, Spring Boot 3.5.x, Spring Security 6.x
- **Database**: MySQL (JPA/Hibernate), Redis (token/session storage)
- **Security**: JWT (jjwt 0.12.6), BCrypt, AES-256 encryption, TOTP 2FA
- **Frontend**: React 19, Vite, React Router, Axios

### Backend Package Structure
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
├── security/
│   ├── oauth2/      # OAuth2 user services, handlers, custom resolvers
│   └── ...          # JWT filters, UserPrincipal
├── util/            # Encryption, NicknameGenerator
└── exception/       # Custom exceptions
```

### Frontend Structure
```
frontend/src/
├── api/             # Axios client, API functions (auth, user, 2FA, phone, oauth2)
├── contexts/        # AuthContext (login state, token management)
├── pages/           # Login, Signup, Dashboard, OAuth2Callback, ForgotPassword
├── components/      # Reusable components (PasswordStrengthMeter)
└── styles/          # Global CSS
```

### Data Model
- `User` (tb_user): Base user info, 1:0..1 with SignInInfo, 1:N with Channel
- `UserSignInInfo` (tb_user_sign_in_info): Email login credentials (email users only)
- `UserChannel` (tb_user_channel): Login channels (EMAIL, GOOGLE, KAKAO, NAVER, FACEBOOK)
- `UserTwoFactor` (tb_user_two_factor): TOTP 2FA secret and backup codes
- `LoginHistory` (tb_login_history): Login attempts with device/location info
- `PasswordHistory` (tb_password_history): Password reuse prevention
- `EmailVerification` (tb_email_verification): Email verification tokens
- `PhoneVerification` (tb_phone_verification): Phone verification tokens

### Key Design Decisions
- Email fields stored as: `*_enc` (original encrypted) + `*_lower_enc` (lowercase encrypted for search)
- Social login: only `tb_user` + `tb_user_channel` (no SignInInfo)
- Email login: `tb_user` + `tb_user_sign_in_info` + `tb_user_channel`
- One user can link multiple social accounts
- OAuth2 account linking uses cookie (`oauth2_link_state`) to pass state through OAuth2 flow
- Remember Me: localStorage (persistent) vs sessionStorage (session-only)

### Authentication Flows
1. **Email signup**: Send verification code → Verify → Create account (nickname auto-generated)
2. **Social login/signup**: OAuth2 → Auto-create user if new → Issue JWT
3. **Account linking**: Prepare link state (cookie) → OAuth2 → Link channel to existing user
4. **2FA**: Login → If 2FA enabled, verify TOTP code → Complete login

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
