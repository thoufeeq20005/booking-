# Google Sign-In

BookEasy uses **Google Identity Services** (the "Sign in with Google" button). The browser receives a signed ID
token and sends it to `POST /api/v1/auth/google`. The backend verifies it. No client secret is needed.

## Setup

1. Google Cloud Console → *APIs & Services* → *OAuth consent screen*: configure the app name (BookEasy), support
   email, logo, privacy policy and terms URLs. Scopes: `openid`, `email`, `profile` only.
2. *Credentials* → *Create credentials* → *OAuth client ID* → **Web application**.
   - Authorised JavaScript origins: `http://localhost:5173` (dev) and your production domain(s).
   - No redirect URIs are needed for the button flow.
3. Put the client ID in **both**:
   ```
   GOOGLE_OAUTH_CLIENT_ID=xxxx.apps.googleusercontent.com        # backend – audience check
   VITE_GOOGLE_OAUTH_CLIENT_ID=xxxx.apps.googleusercontent.com   # frontend – renders the button
   ```
   Without the frontend value the button is hidden. Without the backend value the API answers
   `503 GOOGLE_NOT_CONFIGURED`.
4. Publish the consent screen (move it out of *Testing*) before launch.

## Account linking rules

- A Google account (`sub`) already linked → sign in to that BookEasy account.
- Not linked, and Google reports a **verified** email that matches an existing account → link and sign in.
- Not linked, with no matching account → create a `CUSTOMER` with the Google name and a verified email.
- Unverified Google email → refused (`GOOGLE_TOKEN_INVALID`), so nobody can take over an account by email alone.
- If the account has 2FA on, Google sign-in still asks for the authenticator code.
