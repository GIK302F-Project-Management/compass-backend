# compass-backend

REST API for Compass, an AI study companion that helps students plan their studies, manage learning goals and track progress.
The web page that uses it lives in [compass](https://github.com/GIK302F-Project-Management/compass).

## Tech stack

- Java 21 or newer, Spring Boot 4 (Web, Validation, Security with JWT, Data JPA)
- SQLite file database (no server to install)
- Maven (use the included `./mvnw`)
- Optional: an API key for an OpenAI-compatible provider (OpenRouter by default), to let a language model write the study plans

## Getting started

```bash
./mvnw spring-boot:run     # http://localhost:8080
./mvnw test                # runs the whole test suite
```

On Windows use `.\mvnw.cmd` instead of `./mvnw`.

The database is created on first start in `compass.db`, next to where you start the app. Set `COMPASS_DB_PATH` to use another file.

## Configuration

Everything is optional for local development. Set these as environment variables.

| Variable                | Default                 | Meaning                                                                                                                                       |
| ----------------------- | ----------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `COMPASS_DB_PATH`       | `compass.db`            | SQLite file.                                                                                                                                  |
| `COMPASS_JWT_SECRET`    | random at startup       | Key that signs the access tokens, at least 32 characters. Set it in every real environment; without it, every restart logs everyone out.      |
| `COMPASS_JWT_TTL`       | `PT2H`                  | How long a token lasts (ISO-8601 duration).                                                                                                   |
| `COMPASS_CORS_ORIGINS`  | `http://localhost:5173` | Web page addresses allowed to call the API from a browser, separated by commas.                                                               |
| `LLM_ENABLED`           | `false`                 | `true` makes study plans come from a language model instead of the built-in rules.                                                            |
| `LLM_API_KEY`           | none                    | Key of the AI provider. Required when `LLM_ENABLED` is `true`. `OPENROUTER_API_KEY` works too. Never put it in the code or on GitHub.         |
| `LLM_MODEL`             | none                    | Model to use, for example `openrouter/free`. Required when `LLM_ENABLED` is `true`. `OPENROUTER_MODEL` works too.                              |
| `LLM_BASE_URL`          | OpenRouter              | Address of any OpenAI-compatible API, for example `https://api.openai.com/v1` to use OpenAI.                                                  |

## API

Only `POST /user/register` and `POST /user/login` are public. Every other endpoint needs an access token. A missing, expired or
invalid token gets `401`.

### Accounts and login

| Request              | Body                                   | Answer                                                                    |
| -------------------- | -------------------------------------- | ------------------------------------------------------------------------- |
| `POST /user/register` | `{"name", "email", "password"}`       | `202` with a fixed message, whether or not the email was already taken    |
| `POST /user/login`    | `{"email", "password"}`               | `200` with `{"token", "expiresAt", "user"}`                               |
| `GET /user/me`        | none                                  | `200` with the logged-in user (use it to check a stored token still works) |

Send the token on every other request: `Authorization: Bearer <token>`.

- The email must belong to the university (`@alumni.mondragon.edu`) and the password has 8 to 72 characters. Invalid fields get `400`.
- Registering always answers `202` with the same message, so it cannot be used to find out who has an account. A second registration
  never changes an existing account.
- A wrong password and an unknown email get the same `401` answer, for the same reason.

### Learning goals

A student manages their own goals. Each request only sees and changes the goals of the student the token belongs to.

| Request              | Body                                     | Answer                                       |
| -------------------- | ---------------------------------------- | -------------------------------------------- |
| `GET /goals`         | none                                     | `200` with the student's goals, oldest first |
| `POST /goals`        | `{"title": "...", "description": "..."}` | `201` with the created goal                  |
| `PUT /goals/{id}`    | `{"title": "...", "description": "..."}` | `200` with the updated goal                  |
| `DELETE /goals/{id}` | none                                     | `204`                                        |

A goal is `{"id": 1, "title": "...", "description": "...", "createdAt": "..."}`. The title is required (at most 100 characters);
the description is optional (at most 1000 characters). Invalid fields get `400` with `{"errors": {"title": "..."}}`.

Another student's goal and a goal that does not exist both answer `404`, so goal ids cannot be used to see what other students wrote.

### Collaborative learning (shared plans)

Activities and tasks live in a **plan**. A plan has an owner and members; every member can add, edit, complete and delete
its activities and tasks, and everyone sees the changes the next time they load the plan. A student who has no plan gets a
personal one called "My plan" the first time they call `GET /plans`.

A student only sees the plans they are a member of. Anything in a plan they are not in (the plan, its activities, its tasks,
its progress) answers `404`, as if it did not exist. Owner-only actions answer `403` to the other members.

| Request                                   | Body / who               | Answer                                                                                          |
| ----------------------------------------- | ------------------------ | ----------------------------------------------------------------------------------------------- |
| `GET /plans`                              |                          | `200` with `[{"id", "name", "role", "memberCount", "createdAt"}]`; `role` is `OWNER` or `MEMBER` |
| `POST /plans`                             | `{"name"}`               | `201` with the plan, owned by the student                                                       |
| `GET /plans/{id}`                         | members                  | `200` with the plan and `members: [{"userId", "name", "owner", "you", "joinedAt"}]`             |
| `PUT /plans/{id}`                         | `{"name"}`, owner        | `200` with the renamed plan                                                                     |
| `DELETE /plans/{id}`                      | owner                    | `204`; removes the members, invitations, activities and tasks                                   |
| `DELETE /plans/{id}/members/{userId}`     | self, or the owner       | `204`; use your own id to leave. The owner cannot leave (`409`); members cannot remove others (`403`) |
| `GET /plans/{id}/progress`                | members                  | `200` with the overall progress (below)                                                         |
| `POST /plans/{id}/invitations`            | owner                    | `201` with `{"id", "token", "createdAt", "expiresAt"}`; the link works for 7 days               |
| `GET /plans/{id}/invitations`             | owner                    | `200` with the invitations that still work                                                      |
| `DELETE /plans/{id}/invitations/{invId}`  | owner                    | `204`; the link stops working                                                                   |
| `GET /invitations/{token}`                | any logged-in student    | `200` with `{"planName", "ownerName", "memberCount", "alreadyMember"}`, without joining         |
| `POST /invitations/{token}/accept`        | any logged-in student    | `200` with the plan. Accepting twice is fine; a full plan (50 members) answers `409`            |
| `GET /plans/{id}/activities`              | members                  | `200` with the activities, oldest first                                                         |
| `POST /plans/{id}/activities`             | `{"title", "subject", "dueDate"}` | `201` with the activity                                                                |
| `GET /activities/{id}`                    | members                  | `200` with the activity                                                                         |
| `PUT /activities/{id}`                    | `{"title", "subject", "dueDate"}` | `200` with the updated activity                                                        |
| `PUT /activities/{id}/done`               | `{"done": true}`         | `200`. Completing an activity completes its open tasks; reopening it leaves the tasks alone     |
| `DELETE /activities/{id}`                 | members                  | `204`; removes its tasks                                                                        |
| `GET /plans/{id}/tasks`                   | members                  | `200` with every task of the plan (one request instead of one per activity)                     |
| `GET /activities/{id}/tasks`              | members                  | `200` with the activity's tasks, oldest first                                                   |
| `POST /activities/{id}/tasks`             | `{"title", "details"?, "dueDate"?, "estimatedMinutes"?}` | `201` with the task. Adding a task reopens a completed activity     |
| `PATCH /tasks/{id}`                       | any of `title`, `details`, `dueDate`, `estimatedMinutes`, `done` | `200` with the task                                                     |
| `DELETE /tasks/{id}`                      | members                  | `204`                                                                                           |

- **Invitations.** The owner creates a link and shares it however they like (the web page builds `/join/<token>`). Whoever
  opens it while logged in can see what it is for and accept it. The token is 256 random bits; an unknown, expired or revoked
  token always answers `404`, so the three cases cannot be told apart. The owner can revoke a link at any time.
- **Editing together.** `PATCH /tasks/{id}` only changes the fields in the body, so two members editing different fields of the
  same task do not overwrite each other. `null` clears `dueDate` or `estimatedMinutes`. Invalid or unknown fields answer `400`
  with `{"errors": {"title": "..."}}`. The first member to complete a task is credited with it, and reopening it clears the credit.
- **Progress.** `GET /plans/{id}/progress` answers
  `{"activitiesTotal", "activitiesDone", "tasksTotal", "tasksDone", "percent", "members": [{"userId", "name", "tasksDone", "activitiesDone"}]}`.
  `percent` counts every task, plus every activity that has no tasks, as one step each. It is computed on every request.
- Members are shown to each other by name only, never by email.

### Study plans

`POST /api/study-plans` turns goals and the time a student has into a day-by-day schedule. It does not store anything.

```json
{
  "startDate": "2026-11-10",
  "dailyAvailableMinutes": 90,
  "goals": [{ "id": "g1", "title": "Review genetics", "deadline": "2026-11-20", "estimatedMinutes": 180 }],
  "restDays": ["SUNDAY"],
  "preferences": "Split big tasks across several days"
}
```

`restDays` (days of the week with no study, at most 6) and `preferences` (free text, at most 500 characters) are optional.
Both planners always keep the rest days; only the AI planner reads the preferences, and only as a soft wish.

The answer is `{"source", "status", "sessions", "warnings"}`:

- `source` is `rule-based` (built-in rules, the default) or `llm` (written by the language model).
- `status` is `ready` when every minute was scheduled and `partial` when some did not fit before their deadline; `warnings` says which.
- `sessions` lists `{"goalId", "title", "date", "durationMinutes"}`, sorted by date.

## Study plans written by the AI

By default plans come from the built-in rules and need no setup. To let a language model write them, start the server with the
`LLM_*` settings from the table above. They use OpenRouter by default; set `LLM_BASE_URL` (and a matching key and model) to use another
OpenAI-compatible provider.

macOS / Linux:

```bash
read -rs LLM_API_KEY && export LLM_API_KEY   # paste the key, then press Enter (it is not shown)
export LLM_MODEL=openrouter/free
export LLM_ENABLED=true
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
$credential = Get-Credential -UserName "OpenRouter" -Message "Paste the API key into the password field"
$env:LLM_API_KEY = $credential.GetNetworkCredential().Password.Trim()
Remove-Variable credential
$env:LLM_MODEL = "openrouter/free"
$env:LLM_ENABLED = "true"
.\mvnw.cmd spring-boot:run
```

Wait for `Started CompassBackendApplication` and leave that terminal open. Repeat the setup in every new terminal window.
If the key, the model or the address is missing or invalid the server refuses to start. If the model answers with an invalid or incomplete plan, the
request fails with `502` instead of returning a wrong plan.

### Trying it from the terminal (Windows)

With the server running, open another PowerShell window in this folder and run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\study-plan.ps1
```

The script asks for the email and password of an account you registered, then for the start date (`YYYY-MM-DD`), the hours available
per day and each goal (title, deadline, hours needed). It prints the schedule as a table and the warnings. Check that `Generator: llm`
appears when you expect the model to be used. To get another plan, run the script again; the server does not need a restart.

## Project structure

```
src/main/java/com/compass/backend/
├── config/       # Security (JWT, CORS) and token setup
├── controller/   # HTTP endpoints and the error handlers that turn exceptions into responses
├── dto/          # Request and response shapes
├── exception/    # Business errors (invalid credentials, plan not found, not the owner...)
├── model/        # JPA entities and repositories
├── service/      # Business logic: users, tokens, goals, shared plans, activities, tasks, progress, study-plan generators
└── validation/   # Custom validation rules
src/test/java/    # Controller tests (MockMvc) and service tests
scripts/          # study-plan.ps1, the terminal client for study plans
postman/          # Postman collections for register and login
```

## Workflow

- Create a branch per user story: `feature/US-<n>-short-description`
- Open a Pull Request to `main` and get at least one review before merging
- Commit messages: `US-<n>: short description`
