import { ArrowRight, LockKeyhole, Navigation } from "lucide-react";
import { useState, type FormEvent } from "react";
import "./_group.css";
import "./Current.css";

type AuthMode = "sign-in" | "create-account" | "reset-password";

export function Current() {
  const [mode, setMode] = useState<AuthMode>("sign-in");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [notice, setNotice] = useState("");

  function changeMode(nextMode: AuthMode) {
    setMode(nextMode);
    setPassword("");
    setNotice("");
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setNotice(
      mode === "reset-password"
        ? "Password reset is part of the secure sign-in flow."
        : "This is a visual preview of the current Wayfare sign-in.",
    );
  }

  const title =
    mode === "create-account"
      ? "Create your account"
      : mode === "reset-password"
        ? "Reset your password"
        : "Welcome back";

  return (
    <main className="auth-current-screen">
      <div className="auth-current-content">
        <header className="auth-current-brand">
          <span className="auth-current-brand-mark">
            <Navigation aria-hidden="true" size={19} strokeWidth={2.2} />
          </span>
          <span>
            <strong>Wayfare</strong>
            <small>Made for the way there</small>
          </span>
        </header>

        <section aria-labelledby="current-auth-title" className="auth-current-card">
          <span className="auth-current-status">
            <i aria-hidden="true" />
            WAYFARE ACCOUNT
          </span>
          <h1 id="current-auth-title">{title}</h1>
          <p className="auth-current-description">
            {mode === "sign-in"
              ? "Sign in securely to request rides and manage your trips."
              : mode === "create-account"
                ? "Create a Wayfare account to get started."
                : "Enter your email and we’ll send reset instructions if an account exists."}
          </p>

          <form className="auth-current-form" onSubmit={handleSubmit}>
            {mode === "create-account" ? (
              <Field
                autoComplete="name"
                label="Name"
                onChange={setName}
                placeholder="Name"
                value={name}
              />
            ) : null}
            {mode !== "sign-in" ? (
              <Field
                autoComplete="email"
                label="Email"
                onChange={setEmail}
                placeholder="Email"
                type="email"
                value={email}
              />
            ) : (
              <Field
                autoComplete="email"
                label="Email"
                onChange={setEmail}
                placeholder="Email"
                type="email"
                value={email}
              />
            )}
            {mode !== "reset-password" ? (
              <Field
                autoComplete={mode === "sign-in" ? "current-password" : "new-password"}
                label="Password"
                onChange={setPassword}
                placeholder="Password"
                type="password"
                value={password}
              />
            ) : null}

            {notice ? (
              <p aria-live="polite" className="auth-current-notice">
                {notice}
              </p>
            ) : null}

            <button className="auth-current-submit" type="submit">
              <span>
                {mode === "sign-in"
                  ? "Sign in"
                  : mode === "create-account"
                    ? "Create account"
                    : "Send reset instructions"}
              </span>
              <ArrowRight aria-hidden="true" size={17} />
            </button>
          </form>

          {mode === "sign-in" ? (
            <>
              <button
                className="auth-current-link auth-current-forgot"
                onClick={() => changeMode("reset-password")}
                type="button"
              >
                Forgot password?
              </button>
              <div className="auth-current-switch">
                <span>New to Wayfare?</span>
                <button onClick={() => changeMode("create-account")} type="button">
                  Create an account
                </button>
              </div>
            </>
          ) : (
            <button
              className="auth-current-link auth-current-back"
              onClick={() => changeMode("sign-in")}
              type="button"
            >
              Back to sign in
            </button>
          )}
        </section>

        <footer className="auth-current-footer">
          <LockKeyhole aria-hidden="true" size={13} />
          Your account details are protected by Firebase Authentication.
        </footer>
      </div>
    </main>
  );
}

function Field({
  label,
  value,
  onChange,
  placeholder,
  type = "text",
  autoComplete,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  type?: string;
  autoComplete: string;
}) {
  const id = `current-${label.toLowerCase().replaceAll(" ", "-")}`;
  return (
    <label className="auth-current-field" htmlFor={id}>
      <span>{label}</span>
      <input
        autoComplete={autoComplete}
        id={id}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        type={type}
        value={value}
      />
    </label>
  );
}
