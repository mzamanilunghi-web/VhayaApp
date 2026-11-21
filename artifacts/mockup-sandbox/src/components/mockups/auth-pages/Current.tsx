import { ArrowLeft, ArrowRight, LockKeyhole, MapPin, Navigation } from "lucide-react";
import { useState, type FormEvent } from "react";
import "./_group.css";
import "./Current.css";

type AuthView = "sign-in" | "sign-up" | "verify";

export function Current() {
  const [mode, setMode] = useState<AuthView>("sign-in");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [message, setMessage] = useState("");
  const isSignIn = mode === "sign-in";
  const isVerification = mode === "verify";

  function switchMode(nextMode: "sign-in" | "sign-up") {
    setMode(nextMode);
    setPassword("");
    setCode("");
    setMessage("");
  }

  function previewSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage("");
    if (mode === "sign-up") {
      setMode("verify");
      setMessage(`A verification code was sent to ${email || "your email"}.`);
    } else {
      setMessage("This is a visual preview of the current Vhaya sign-in.");
    }
  }

  return (
    <main className="auth-current-screen">
      <div aria-hidden="true" className="auth-current-atmosphere">
        <div className="auth-current-orbit auth-current-orbit-outer" />
        <div className="auth-current-orbit auth-current-orbit-inner" />
        <div className="auth-current-route-line" />
        <span className="auth-current-route-point" />
        <MapPin className="auth-current-map-pin" size={19} strokeWidth={1.8} />
        <span className="auth-current-atmosphere-label">LOCAL, EVERY DAY</span>
      </div>

      <section aria-labelledby="current-auth-title" className="auth-current-panel">
        <header className="auth-current-brand">
          <span className="auth-current-brand-icon">
            <Navigation aria-hidden="true" size={18} strokeWidth={2.2} />
          </span>
          <span className="auth-current-brand-copy">
            <strong>Vhaya</strong>
            <span>POLOKWANE · SOUTH AFRICA</span>
          </span>
        </header>

        <span className="auth-current-status">
          <i />
          {isVerification ? "EMAIL CONFIRMATION" : isSignIn ? "SECURE SIGN-IN" : "RIDE WITH VHAYA"}
        </span>
        <h1 className="auth-current-title" id="current-auth-title">
          {isVerification ? "Verify your email" : isSignIn ? "Welcome back" : "Create your account"}
        </h1>
        <p className="auth-current-subtitle">
          {isVerification
            ? `Enter the code sent to ${email || "your email address"}.`
            : isSignIn
              ? "Sign in to request rides and manage your Vhaya account."
              : "Create a secure account to request rides and keep your trip history."}
        </p>

        <form className="auth-current-form" noValidate onSubmit={previewSubmit}>
          {!isVerification && !isSignIn ? (
            <Field
              autoComplete="name"
              label="Name"
              onChange={setName}
              placeholder="Your name"
              value={name}
            />
          ) : null}
          {isVerification ? (
            <Field
              autoComplete="one-time-code"
              label="Verification code"
              onChange={setCode}
              placeholder="Enter your code"
              value={code}
            />
          ) : (
            <>
              <Field
                autoComplete="email"
                label="Email"
                onChange={setEmail}
                placeholder="you@example.com"
                type="email"
                value={email}
              />
              <Field
                autoComplete={isSignIn ? "current-password" : "new-password"}
                label={isSignIn ? "Password" : "Create password"}
                onChange={setPassword}
                placeholder={isSignIn ? "Enter your password" : "Choose a password"}
                type="password"
                value={password}
              />
              {isSignIn ? (
                <button
                  className="auth-current-forgot"
                  onClick={() => setMessage("Password recovery is part of the secure sign-in flow.")}
                  type="button"
                >
                  Forgot password?
                </button>
              ) : null}
            </>
          )}

          {message ? <p aria-live="polite" className="auth-current-message">{message}</p> : null}

          <button className="auth-current-submit" type="submit">
            <span>{isVerification ? "Verify email" : isSignIn ? "Sign in" : "Create account"}</span>
            <ArrowRight aria-hidden="true" size={17} />
          </button>

          {isVerification ? (
            <>
              <button
                className="auth-current-quiet-action"
                onClick={() => setMessage(`A new code was sent to ${email || "your email"}.`)}
                type="button"
              >
                Didn’t get a code? <strong>Send a new one</strong>
              </button>
              <button className="auth-current-back" onClick={() => switchMode("sign-up")} type="button">
                <ArrowLeft aria-hidden="true" size={14} />
                Change email address
              </button>
            </>
          ) : (
            <div className="auth-current-switch">
              <span>{isSignIn ? "New to Vhaya?" : "Already have an account?"}</span>
              <button onClick={() => switchMode(isSignIn ? "sign-up" : "sign-in")} type="button">
                {isSignIn ? "Create an account" : "Sign in"}
              </button>
            </div>
          )}
        </form>

        <footer className="auth-current-security">
          <LockKeyhole aria-hidden="true" size={12} />
          Your session is protected and stored securely on this device.
        </footer>
      </section>
    </main>
  );
}

function Field({
  label,
  type = "text",
  placeholder,
  value,
  onChange,
  autoComplete,
}: {
  label: string;
  type?: string;
  placeholder: string;
  value: string;
  onChange: (value: string) => void;
  autoComplete: string;
}) {
  const id = `current-${label.toLowerCase().replaceAll(" ", "-")}`;
  return (
    <label className="auth-current-field" htmlFor={id}>
      <span className="auth-current-label">{label}</span>
      <input
        autoComplete={autoComplete}
        className="auth-current-input"
        id={id}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        type={type}
        value={value}
      />
    </label>
  );
}
