# hckcapital-be

Java/Spring Boot backend for **flxBubble** — the LINE Flex Message card platform. Serves the
[hckcapital-mobile-fe](https://github.com/WanChang-hckCapital/hckcapital-mobile-fe) React
Native app (and shares its MongoDB database with the older Next.js reference project,
`hckcapital`).

## Stack

- Java 21, Spring Boot 3.5.3
- Spring Web, Spring Security (stateless, JWT-based), Spring Data MongoDB
- MongoDB (Atlas) — same database as the Next.js reference app
- Google Cloud Storage — image/video uploads
- Maven (via the `mvnw` wrapper, no local Maven install required)

## Getting started

1. **Copy the env template and fill in real values:**
   ```bash
   cp .env.example .env
   ```
   See [Environment variables](#environment-variables) below for what each key is for.
   `.env` is loaded automatically at startup via `spring-dotenv` — nothing else to configure.

2. **Run it:**
   ```bash
   ./mvnw spring-boot:run
   ```
   The server starts on `http://localhost:8080`. `MONGODB_URL` and `JWT_SECRET` have no
   fallback default — startup fails immediately (with a clear error) if either is unset,
   rather than silently running against the wrong database or an insecure default secret.

3. **Build only:**
   ```bash
   ./mvnw compile      # compile
   ./mvnw test         # run tests (also needs MONGODB_URL/JWT_SECRET set — see below)
   ./mvnw package       # build a runnable jar into target/
   ```

## Environment variables

All of these live in `.env` (gitignored — never commit it). See `.env.example` for the full
list with no values filled in.

| Variable | Purpose |
|---|---|
| `MONGODB_URL` | MongoDB connection string. Same database as the Next.js reference app. |
| `JWT_SECRET` | Signs/verifies auth JWTs (see `JwtUtil`/`JwtAuthenticationFilter`). |
| `SERVICE_ACCOUNT_*_BUCKET` (11 fields) | Google Cloud Storage service-account credentials, used for image/video uploads (see `ImageController`/`VideoController`/`ImageUploadService`). Same fields the Next.js reference project already uses for its own GCS bucket. |
| `NEXT_PUBLIC_PUBLIC_BUCKET_NAME` | GCS bucket name uploads go to. |
| `NEXT_PUBLIC_PROJECT_ENVIRONMENT` | Selects the upload path prefix (e.g. `staging`/`production`). |

`MONGODB_DB` is also read (`spring.data.mongodb.database`) but defaults to `test` if unset.

## Auth

Stateless JWT auth (`SecurityConfig`/`JwtAuthenticationFilter`) — every request except
`/api/v1/auth/**`, `/api/v1/health`, and `/actuator/**` requires a valid
`Authorization: Bearer <token>` header. Get a token via `POST /api/v1/auth/login`.

## API overview

Base path for everything below: `/api/v1`.

| Method | Path | What it does |
|---|---|---|
| `POST` | `/auth/login` | Sign in, returns a JWT. |
| `GET` | `/health` | Liveness check (no auth). |
| `GET` | `/cards` | Paginated feed of published cards. |
| `GET` | `/cards/{cardId}` | A single card (full detail, including its editable JSON). |
| `POST` | `/cards` | Create/update a card (upsert — presence of `cardId` decides which). |
| `GET` | `/cards/{cardId}/likes` | Who liked a card. |
| `POST` | `/cards/{cardId}/like` | Toggle like/unlike. |
| `GET` \| `POST` | `/cards/{cardId}/comments` | List / add comments. |
| `GET` | `/cards/templates` | Paginated template cards for a category (card editor's "Choose a Template"). |
| `GET` | `/cards/templates/counts` | Per-category template counts. |
| `GET` | `/profile`, `/profile/followers`, `/profile/following` | Profile info + social graph. |
| `GET` | `/profile/collections`, `/profile/collections/{id}/cards` | A profile's collections. |
| `GET` | `/profile/cards/published`, `/profile/cards/drafts` | A profile's own cards. |
| `GET` \| `POST` | `/friends`, `/friends/following`, `/friends/requests/sent`, `/friends/follow`, `/friends/unfollow`, `/friends/request` | Follow/friend graph. |
| `POST` | `/images` | Upload an image (multipart) to GCS. |
| `POST` | `/videos` | Upload a video (multipart, 100MB cap) to GCS. |

## Project layout

```
src/main/java/com/hckcapital/be/
  controller/   REST endpoints
  service/      business logic
  model/        MongoDB documents
  dto/          request/response shapes
  config/       security, JWT, CORS
```

## Notes for CI

The default `HckcapitalBeApplicationTests` boots the real Spring context, which needs
`MONGODB_URL`/`JWT_SECRET` set to pass — if you wire up GitHub Actions (or any CI), provide
those as CI secrets or the build will fail at the test step.
