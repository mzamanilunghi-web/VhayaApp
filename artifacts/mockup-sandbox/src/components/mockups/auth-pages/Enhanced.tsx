import { useState, type FormEvent } from "react";
import {
  Apple,
  ArrowRight,
  Github,
  LockKeyhole,
  MapPin,
  Navigation,
  ShieldCheck,
} from "lucide-react";
import "./_group.css";
import "./Enhanced.css";

type PreviewMode = "sign-in" | "create-account";
type Provider = "Google" | "GitHub" | "Apple" | "X";

export function Enhanced() {
  const [mode, setMode] = useState<PreviewMode>("sign-in");
  const [notice, setNotice] = useState("");
  const [passwordVisible, setPasswordVisible] = useState(false);
  const isSignIn = mode === "sign-in";

  function switchMode(nextMode: PreviewMode) {
    setMode(nextMode);
    setNotice("");
    setPasswordVisible(false);
  }

  function previewSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setNotice(
      isSignIn
        ? "This is a visual preview. Your details haven’t been sent or saved."
        : "This is a visual preview. Account creation isn’t available here.",
    );
  }

  function showProviderNotice(provider: Provider) {
    setNotice(`${provider} sign-in is shown for preview only. No account was connected.`);
  }

  return (
    <main className="wayfare-enhanced enhanced-preview">
      <div aria-hidden="true" className="enhanced-preview-grain" />
      <div className="enhanced-preview-wrap">
        <header className="enhanced-preview-header">
          <a aria-label="Vhaya home" className="enhanced-preview-brand" href="#vhaya">
            <span className="enhanced-preview-mark">
              <Navigation aria-hidden="true" size={19} strokeWidth={2.2} />
            </span>
            <span className="enhanced-preview-brand-copy">
              <strong>Vhaya</strong>
              <span>POLOKWANE · SOUTH AFRICA</span>
            </span>
          </a>
          <div className="enhanced-preview-local">
            <span className="enhanced-preview-live-dot" />
            Made for the way there
          </div>
        </header>

        <div className="enhanced-preview-layout">
          <section aria-labelledby="enhanced-welcome-title" className="enhanced-preview-story">
            <div className="enhanced-preview-story-copy">
              <p className="enhanced-preview-eyebrow"><span /> EVERYDAY RIDES, A LITTLE CLOSER</p>
              <h1 id="enhanced-welcome-title">
                {isSignIn ? <>Good to have<br />you back.</> : <>Let’s get<br />you moving.</>}
              </h1>
              <p className="enhanced-preview-intro">
                {isSignIn
                  ? "Your next trip is never far away. Sign in to pick up where you left off."
                  : "A dependable ride starts with a few simple details."}
              </p>
            </div>

            <div className="enhanced-preview-map" aria-hidden="true">
              <div className="enhanced-preview-map-orbit enhanced-preview-map-orbit-a" />
              <div className="enhanced-preview-map-orbit enhanced-preview-map-orbit-b" />
              <div className="enhanced-preview-map-grid" />
              <div className="enhanced-preview-map-road" />
              <span className="enhanced-preview-map-origin" />
              <span className="enhanced-preview-map-destination"><MapPin size={17} /></span>
              <span className="enhanced-preview-map-label">POLOKWANE, ON YOUR WAY</span>
            </div>

            <div className="enhanced-preview-promise">
              <span className="enhanced-preview-route-icon">
                <span />
                <span />
              </span>
              <p>Local service. Real people.<br /><strong>South Africa, moving together.</strong></p>
            </div>
          </section>

          <section aria-labelledby="enhanced-form-title" className="enhanced-preview-card">
            <div className="enhanced-preview-card-top">
              <span className="enhanced-preview-secure-tag"><i /> {isSignIn ? "SECURE SIGN-IN" : "RIDE WITH VHAYA"}</span>
              <span className="enhanced-preview-step">01 <b>/</b> 02</span>
            </div>
            <div className="enhanced-preview-heading">
              <h2 id="enhanced-form-title">{isSignIn ? "Welcome back" : "Create your account"}</h2>
              <p>{isSignIn ? "Sign in to request rides and manage your Vhaya account." : "Your everyday rides, all in one place."}</p>
            </div>

            <form className="enhanced-preview-form" onSubmit={previewSubmit}>
              {!isSignIn ? (
                <label className="enhanced-preview-field">
                  <span>Name</span>
                  <input autoComplete="name" name="display-name" placeholder="Your name" />
                </label>
              ) : null}
              <label className="enhanced-preview-field">
                <span>Email address</span>
                <input autoComplete="email" inputMode="email" name="email" placeholder="you@example.com" type="email" />
              </label>
              <label className="enhanced-preview-field">
                <span>Password</span>
                <span className="enhanced-preview-password">
                  <input
                    autoComplete={isSignIn ? "current-password" : "new-password"}
                    name="password"
                    placeholder={isSignIn ? "Enter your password" : "Choose a password"}
                    type={passwordVisible ? "text" : "password"}
                  />
                  <button
                    aria-label={passwordVisible ? "Hide password" : "Show password"}
                    aria-pressed={passwordVisible}
                    className="enhanced-preview-reveal"
                    onClick={() => setPasswordVisible((visible) => !visible)}
                    type="button"
                  >
                    {passwordVisible ? "Hide" : "Show"}
                  </button>
                </span>
              </label>

              {isSignIn ? (
                <button
                  className="enhanced-preview-forgot"
                  onClick={() => setNotice("Password recovery is part of the secure sign-in flow. This preview won’t send a reset email.")}
                  type="button"
                >
                  Forgot password?
                </button>
              ) : null}

              {notice ? <p aria-live="polite" className="enhanced-preview-notice">{notice}</p> : null}

              <button className="enhanced-preview-submit" type="submit">
                <span>{isSignIn ? "Sign in" : "Create account"}</span>
                <ArrowRight aria-hidden="true" size={17} />
              </button>
            </form>

            <div className="enhanced-preview-divider"><span /> <b>OR CONTINUE WITH</b> <span /></div>

            <div aria-label="Preview sign-in options" className="enhanced-preview-socials">
              <button aria-label="Continue with Google (preview)" onClick={() => showProviderNotice("Google")} type="button">
                <GoogleMark /> <span>Google</span>
              </button>
              <button aria-label="Continue with GitHub (preview)" onClick={() => showProviderNotice("GitHub")} type="button">
                <Github aria-hidden="true" size={17} strokeWidth={2} /> <span>GitHub</span>
              </button>
              <button aria-label="Continue with Apple (preview)" onClick={() => showProviderNotice("Apple")} type="button">
                <Apple aria-hidden="true" size={17} fill="currentColor" strokeWidth={1.8} /> <span>Apple</span>
              </button>
              <button aria-label="Continue with X (preview)" onClick={() => showProviderNotice("X")} type="button">
                <span aria-hidden="true" className="enhanced-preview-x">𝕏</span><span>X</span>
              </button>
            </div>

            <div className="enhanced-preview-switch">
              <span>{isSignIn ? "New to Vhaya?" : "Already have an account?"}</span>
              <button onClick={() => switchMode(isSignIn ? "create-account" : "sign-in")} type="button">
                {isSignIn ? "Create an account" : "Sign in"}
              </button>
            </div>

            <footer className="enhanced-preview-card-foot">
              <ShieldCheck aria-hidden="true" size={14} strokeWidth={1.8} />
              <span>Your session is protected and stored securely on this device.</span>
            </footer>
          </section>
        </div>

        <footer className="enhanced-preview-footer">
          <span><LockKeyhole aria-hidden="true" size={12} /> A little closer to wherever you’re going.</span>
          <span>VHAYA <i>·</i> POLOKWANE</span>
        </footer>
      </div>
    </main>
  );
}

function GoogleMark() {
  return (
    <svg aria-hidden="true" className="enhanced-preview-google" viewBox="0 0 18 18">
      <path fill="#4285F4" d="M17.64 9.205c0-.638-.057-1.252-.164-1.841H9v3.482h4.844a4.14 4.14 0 0 1-1.797 2.715v2.258h2.909c1.702-1.566 2.684-3.875 2.684-6.614Z" />
      <path fill="#34A853" d="M9 18c2.43 0 4.468-.806 5.956-2.181l-2.909-2.258c-.806.54-1.835.86-3.047.86-2.343 0-4.328-1.582-5.036-3.71H.957v2.331A9 9 0 0 0 9 18Z" />
      <path fill="#FBBC05" d="M3.964 10.711A5.41 5.41 0 0 1 3.682 9c0-.594.102-1.17.282-1.711V4.958H.957A9 9 0 0 0 0 9c0 1.452.348 2.827.957 4.042l3.007-2.331Z" />
      <path fill="#EA4335" d="M9 3.579c1.322 0 2.508.454 3.442 1.346l2.581-2.581C13.464.892 11.427 0 9 0A9 9 0 0 0 .957 4.958l3.007 2.331C4.672 5.161 6.657 3.579 9 3.579Z" />
    </svg>
  );
}
