import { useState, type FormEvent } from "react";
import { ArrowLeft, ArrowRight, Check, Eye, EyeOff, LockKeyhole, MapPin, Navigation, RotateCw } from "lucide-react";
import "./LinuxAppleBlend.css";

type AuthMode = "sign-in" | "create-account" | "reset-password" | "verify";

export function LinuxAppleBlend() {
  const [mode, setMode] = useState<AuthMode>("sign-in");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const changeMode = (next: AuthMode) => {
    setError("");
    setNotice("");
    setPassword("");
    setMode(next);
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    setNotice("");
    if (!email.trim()) {
      setError("Enter your email address.");
      return;
    }
    if (mode === "create-account" && !name.trim()) {
      setError("Enter your name.");
      return;
    }
    if (mode !== "reset-password" && password.length < 6) {
      setError("Choose a password with at least six characters.");
      return;
    }
    setBusy(true);
    window.setTimeout(() => {
      setBusy(false);
      if (mode === "create-account") {
        setNotice("Account creation is shown in this local preview. Your verification email would be sent next.");
        setMode("verify");
      } else if (mode === "reset-password") {
        setNotice("If an account uses that email, password reset instructions would be sent.");
      } else {
        setNotice("Sign-in is shown in this local preview. No account details were sent.");
      }
    }, 650);
  };

  const title = mode === "verify"
    ? "Check your inbox"
    : mode === "sign-in"
      ? "Welcome back"
      : mode === "create-account"
        ? "Make it yours"
        : "Reset your password";

  const description = mode === "verify"
    ? `We sent a verification link to ${email || "your email address"}. Verify your email before using Wayfare.`
    : mode === "sign-in"
      ? "Sign in to arrange local trips and keep your journeys together."
      : mode === "create-account"
        ? "A little setup now makes every trip easier to manage."
        : "Enter your email and we’ll send reset instructions if an account exists.";

  return (
    <main className="wayfare-auth">
      <header className="auth-topbar">
        <a className="brand-lockup" href="#home" aria-label="Wayfare home">
          <span className="brand-symbol"><Navigation size={19} strokeWidth={2.4} /></span>
          <span className="brand-copy">
            <strong>Wayfare</strong>
            <small>Made for the way there</small>
          </span>
        </a>
        <div className="topbar-note"><span className="signal-dot" /> South Africa, close to home</div>
      </header>

      <div className="auth-layout">
        <section className="auth-story" aria-label="Wayfare journey">
          <div className="story-kicker"><span>01</span> YOUR NEXT TRIP, IN GOOD HANDS</div>
          <h1>Getting there<br />should feel <em>easy.</em></h1>
          <p className="story-copy">Local rides, thoughtfully arranged. Pick up where you left off and keep every journey in view.</p>

          <div className="route-card" aria-label="Illustration of a route through Cape Town">
            <div className="route-card-head">
              <span className="route-label">A familiar way through</span>
              <span className="route-region">CAPE TOWN · ZA</span>
            </div>
            <svg className="route-map" viewBox="0 0 540 246" role="img" aria-label="A dotted route from Gardens to the Waterfront">
              <path className="map-road road-one" d="M-12 65 C74 89 90 28 170 48 S262 110 323 87 S420 36 552 61" />
              <path className="map-road road-two" d="M24 250 C77 196 70 148 144 137 S226 160 278 128 S365 95 390 4" />
              <path className="map-road road-three" d="M88 -10 C118 48 200 70 205 115 S158 198 229 257" />
              <path className="map-road road-four" d="M323 247 C302 198 342 173 414 157 S502 113 555 130" />
              <path className="map-route" d="M103 179 C147 156 165 131 207 134 S270 162 310 127 S369 78 428 75" />
              <circle className="map-start-halo" cx="103" cy="179" r="12" />
              <circle className="map-start" cx="103" cy="179" r="5" />
              <circle className="map-end-halo" cx="428" cy="75" r="12" />
              <circle className="map-end" cx="428" cy="75" r="5" />
              <text className="map-place" x="75" y="211">Gardens</text>
              <text className="map-place" x="398" y="48">Waterfront</text>
              <text className="map-water" x="312" y="224">TABLE BAY</text>
            </svg>
            <div className="route-caption">
              <span><MapPin size={14} /> Your city, your pace</span>
              <span className="route-caption-end">A simpler way there <ArrowRight size={14} /></span>
            </div>
          </div>

          <div className="trust-line"><span className="trust-icon"><LockKeyhole size={15} /></span><span><strong>Your details stay yours.</strong> Secure sign-in, built for everyday journeys.</span></div>
        </section>

        <section className="auth-panel" aria-labelledby="auth-title">
          <div className="panel-inner">
            <div className="eyebrow"><span className="eyebrow-mark" /> {mode === "verify" ? "EMAIL VERIFICATION" : "WAYFARE ACCOUNT"}</div>
            <h2 id="auth-title">{title}</h2>
            <p className="panel-description">{description}</p>

            {mode === "verify" ? (
              <div className="verification-block">
                <div className="verify-icon"><Check size={22} /></div>
                {error && <div className="feedback error-feedback" role="alert">{error}</div>}
                {notice && <div className="feedback notice-feedback" role="status">{notice}</div>}
                <button className="submit-button" type="button" disabled={busy} onClick={() => { setError(""); setNotice("Your email is not verified yet. Open the link in your inbox, then check again."); setBusy(true); window.setTimeout(() => setBusy(false), 550); }}>
                  <span>{busy ? "Checking…" : "I’ve verified my email"}</span>{busy ? <RotateCw className="spin-icon" size={17} /> : <ArrowRight size={18} />}
                </button>
                <button className="text-button verify-resend" type="button" disabled={busy} onClick={() => { setError(""); setNotice("A new verification email would be sent in the full app."); }}>
                  Send a new verification email
                </button>
              </div>
            ) : (
              <form onSubmit={handleSubmit} noValidate>
                {mode === "create-account" && (
                  <div className="field">
                    <label htmlFor="auth-name">Name</label>
                    <input id="auth-name" name="name" autoComplete="name" autoCapitalize="words" placeholder="Your name" value={name} onChange={(event) => setName(event.target.value)} disabled={busy} />
                  </div>
                )}
                <div className="field">
                  <label htmlFor="auth-email">Email address</label>
                  <input id="auth-email" name="email" type="email" inputMode="email" autoComplete="email" autoCapitalize="none" placeholder="you@example.com" value={email} onChange={(event) => setEmail(event.target.value)} disabled={busy} />
                </div>
                {mode !== "reset-password" && (
                  <div className="field">
                    <div className="label-row"><label htmlFor="auth-password">Password</label>{mode === "sign-in" && <button className="text-button forgot-link" type="button" onClick={() => changeMode("reset-password")}>Forgot password?</button>}</div>
                    <div className="password-wrap">
                      <input id="auth-password" name="password" type={showPassword ? "text" : "password"} autoComplete={mode === "sign-in" ? "current-password" : "new-password"} placeholder="At least 6 characters" value={password} onChange={(event) => setPassword(event.target.value)} disabled={busy} />
                      <button className="visibility-button" type="button" onClick={() => setShowPassword((visible) => !visible)} aria-label={showPassword ? "Hide password" : "Show password"} aria-pressed={showPassword}>
                        {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
                      </button>
                    </div>
                  </div>
                )}
                {error && <div className="feedback error-feedback" role="alert">{error}</div>}
                {notice && <div className="feedback notice-feedback" role="status">{notice}</div>}
                <button className="submit-button" type="submit" disabled={busy}>
                  <span>{busy ? "One moment…" : mode === "sign-in" ? "Sign in" : mode === "create-account" ? "Create account" : "Send reset instructions"}</span>
                  {busy ? <RotateCw className="spin-icon" size={17} /> : <ArrowRight size={18} />}
                </button>
                {mode === "sign-in" ? (
                  <div className="account-switch"><span>New to Wayfare?</span><button className="text-button" type="button" onClick={() => changeMode("create-account")}>Create an account</button></div>
                ) : (
                  <button className="text-button back-link" type="button" onClick={() => changeMode("sign-in")}><ArrowLeft size={14} /> Back to sign in</button>
                )}
              </form>
            )}

            <div className="panel-foot"><span className="foot-lock"><LockKeyhole size={13} /></span>Protected account access <span className="foot-separator">·</span> No third-party sign-in</div>
          </div>
          <div className="panel-bottom"><span>WAYFARE / ACCOUNT ACCESS</span><span>LOCAL TRIPS, MADE SIMPLE</span></div>
        </section>
      </div>
      <footer className="auth-footer"><span>Wayfare</span><span>Made for the way there.</span><span className="footer-region">A service for South Africa</span></footer>
    </main>
  );
}

export default LinuxAppleBlend;
