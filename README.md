# compass

Web interface for Compass, an AI study companion that helps students plan their studies, manage learning goals, and track progress.

The backend API lives in [compass-backend](https://github.com/GIK302F-Project-Management/compass-backend).

## Tech stack

- React 19 + TypeScript
- Vite
- React Router

## Getting started

Requirements: Node.js 20+ and npm.

```bash
npm install
npm run dev
```

The app runs at http://localhost:5173.

## Scripts

| Command           | Description                         |
| ----------------- | ----------------------------------- |
| `npm run dev`     | Start the development server        |
| `npm run build`   | Type-check and build for production |
| `npm run lint`    | Run ESLint                          |
| `npm run preview` | Preview the production build        |

## Project structure

```
src/
├── api/          # API clients (auth, activities, tasks, session)
├── components/   # Reusable UI components
├── mocks/        # Mock data used until the backend is connected
├── pages/        # Route pages
├── utils/        # Helpers
└── validation/   # Form validation
```

## Workflow

- Create a branch per user story: `feature/US-<n>-short-description`
- Open a Pull Request to `main` and get at least one review before merging
- Commit messages: `US-<n>: short description`





# Compass Backend — How to Use It


## 1. Enable the AI and start the server

Run this command:
```powershell
$credential = Get-Credential -UserName "OpenRouter" -Message "Paste the API key into the password field"
```

Just paste the key into the **password** field and confirm. Then, run:

```powershell
$env:OPENROUTER_API_KEY = $credential.GetNetworkCredential().Password.Trim()
Remove-Variable credential

$env:OPENROUTER_MODEL = "openrouter/free"
$env:LLM_ENABLED = "true"

.\mvnw.cmd spring-boot:run
```


Wait for the message `Started CompassBackendApplication`.

**Leave this terminal window open.** The AI will be called automatically when you request a plan.

Do not include your key in your code or on GitHub. Repeat the setup when starting from a new terminal window.

## 2. Enter the data and generate the plan

Open **another PowerShell window**, also in the `compass-backend` folder, and run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\study-plan.ps1
```

This command uses the form saved in `scripts/study-plan.ps1`. The file must be included in the project; if it does not exist, add the interactive plan-generation script to it.

Answer the questions and press **Enter** after each answer:

| Question | How to answer |
| --- | --- |
| Email and password | An account you registered; the script signs in before asking for the plan |
| Start date | Use `YYYY-MM-DD` |
| Daily availability | Hours, for example `1.5` |
| Goal title | For example, `Review genetics` |
| Goal deadline | Use `YYYY-MM-DD`, on or after the start date |
| Total time required | Hours to complete this goal, for example `3` |
| Add another goal? | `y` to add or `n` to generate |

The script converts hours to minutes for the API and displays the schedule in a table, showing the date, activity, and hours.

Check to see if **`Generator: llm`** appears. The status `ready` indicates that all minutes have been scheduled; `partial` indicates that some minutes remain unscheduled. Read the displayed warnings.

To generate another plan, run the script command again. You do not need to restart the server or configure the key as long as it continues to run.

## Authentication

Only `POST /user/register` and `POST /user/login` are public. Every other endpoint needs an access token.

1. `POST /user/login` with `{"email": "...", "password": "..."}` returns `{"token": "...", "expiresAt": "...", "user": {...}}`.
2. Send the token on each request: `Authorization: Bearer <token>`.
3. `GET /user/me` returns the logged-in user (useful to check a stored token is still valid).

`POST /user/register` always answers `202` with the same message, whether the account was created or the email was already registered, so it cannot be used to find out who has an account. An existing account is never changed by a second registration.

A missing, expired or invalid token gets `401`. A wrong password and an unknown email get the same `401` answer.

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `COMPASS_JWT_SECRET` | random at startup | Key that signs the tokens, at least 32 characters. Set it in every real environment; without it, a restart logs everyone out. |
| `COMPASS_JWT_TTL` | `PT2H` | How long a token lasts (ISO-8601 duration). |
| `COMPASS_CORS_ORIGINS` | `http://localhost:5173` | Web page addresses allowed to call the API from a browser, separated by commas. |
