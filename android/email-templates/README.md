# Vhaya Branded Auth Email Templates

These responsive, inline-CSS HTML templates are styled to match Vhaya's **Cobalt (`#124EE8`) & Emerald (`#0D946B`)** theme across Gmail, Apple Mail, and Outlook.

## Files
- `vhaya-verify-email.html` — Account email verification template (`Verify My Email`).
- `vhaya-reset-password.html` — Password reset template (`Reset My Password`).

## Template Placeholders
Replace these placeholders dynamically when sending through your backend SMTP / transactional mailer (e.g., Resend, Postmark, SendGrid, or Nodemailer) with Firebase Admin SDK link generation (`admin.auth().generateEmailVerificationLink(...)` and `generatePasswordResetLink(...)`):

| Placeholder | Meaning | Firebase Console / SMTP Equivalent |
| --- | --- | --- |
| `{{DISPLAY_NAME}}` | User's display name | `%DISPLAY_NAME%` |
| `{{EMAIL}}` | Target user's email address | `%EMAIL%` |
| `{{ACTION_URL}}` | One-time verification or reset link | `%LINK%` |

## Wiring Notes
1. **Option A — Backend SMTP + Firebase Admin SDK (Full HTML Control)**:
   Firebase's built-in email templates strip full HTML customization in newer projects for anti-phishing reasons. For full custom styling, have your backend call `generateEmailVerificationLink(email)` / `generatePasswordResetLink(email)` via Firebase Admin SDK and send these HTML templates via your transactional mailer (with your custom `support@` or `no-reply@` domain).
2. **Option B — Firebase Password Policy**:
   Also enable **Password Policy Enforcement** in **Firebase Console → Authentication → Settings → Password policy** (`Minimum length: 8`, `Require uppercase`, `Require lowercase`, `Require numeric`, `Require special character`) so backend Auth rules mirror the Android sign-up UI checklist.
