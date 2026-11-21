import { useEffect, useMemo, useState, type FormEvent } from "react";
import {
  AlertCircle,
  ArrowLeft,
  BadgeCheck,
  Check,
  CheckCircle2,
  ChevronRight,
  ClipboardCheck,
  Clock3,
  FileCheck2,
  LogOut,
  Mail,
  MapPin,
  RefreshCw,
  Search,
  ShieldCheck,
  X,
} from "lucide-react";
import {
  useDriverApplications,
  useReviewPerson,
  useUpdateCompliance,
} from "@/hooks/useAdminData";
import type {
  DriverApplication,
  DriverComplianceCheck,
  DriverComplianceDocumentType,
  DriverComplianceStatus,
} from "@/lib/vhaya-api";
import { useAuth } from "@/lib/auth-context";
import { LegacyRouteNav } from "@/pages/admin-navigation";

const DOCUMENTS: Array<{
  type: DriverComplianceDocumentType;
  label: string;
  short: string;
  description: string;
}> = [
  {
    type: "identity",
    label: "Identity document",
    short: "Identity",
    description: "Confirm the applicant’s identity details match the copy.",
  },
  {
    type: "driver_license",
    label: "Driver licence",
    short: "Licence",
    description: "Check the licence holder, category and expiry date.",
  },
  {
    type: "vehicle_registration",
    label: "Vehicle registration",
    short: "Registration",
    description: "Match the registration details to the vehicle on file.",
  },
  {
    type: "operating_permit",
    label: "Operating permit",
    short: "Operating permit",
    description: "Confirm the permit reference and supporting copy.",
  },
];

const STATUS_LABELS: Record<DriverComplianceStatus, string> = {
  not_reviewed: "Not reviewed",
  verified: "Verified",
  needs_follow_up: "Needs follow-up",
  not_required: "Not required",
};

const APPLICATION_LABELS: Record<string, string> = {
  submitted: "Submitted",
  under_review: "Under review",
  needs_follow_up: "Needs follow-up",
};

function formatDate(value: number | string | null | undefined): string {
  if (value === null || value === undefined || value === "") return "Not provided";
  const date = typeof value === "number" ? new Date(value) : new Date(value);
  if (Number.isNaN(date.getTime())) return "Not provided";
  return new Intl.DateTimeFormat("en-ZA", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(date);
}

function isExpired(value: string | null | undefined): boolean {
  if (!value) return false;
  const expiry = new Date(`${value}T23:59:59`);
  return !Number.isNaN(expiry.getTime()) && expiry.getTime() < Date.now();
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "The request could not be completed.";
}

function Brand() {
  return (
    <div className="review-brand" aria-label="Wayfare staff">
      <div className="review-brand-mark">W</div>
      <div>
        <div className="review-brand-name">wayfare</div>
        <div className="review-brand-subtitle">staff desk</div>
      </div>
    </div>
  );
}

function AuthSurface() {
  const auth = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [formError, setFormError] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");

  if (auth.status === "loading") {
    return (
      <main className="auth-shell">
        <section className="auth-panel">
          <Brand />
          <div className="auth-skeleton" />
          <div className="auth-skeleton short" />
          <div className="auth-skeleton button" />
          <p className="auth-caption">Checking your staff session…</p>
        </section>
      </main>
    );
  }

  if (auth.status === "ready") return <ReviewDesk />;

  const handleSignIn = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFormError("");
    setBusy(true);
    try {
      await auth.signIn(email, password);
    } catch (error) {
      setFormError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  const handleResend = async () => {
    setFormError("");
    setNotice("");
    setBusy(true);
    try {
      await auth.resendVerification();
      setNotice("Verification email sent. Check your inbox, then return here.");
    } catch (error) {
      setFormError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  const handleVerificationCheck = async () => {
    setFormError("");
    setBusy(true);
    try {
      await auth.user?.reload();
      if (auth.user?.emailVerified) {
        window.location.reload();
      } else {
        setFormError("This email is not verified yet. Use the link in your email, then check again.");
      }
    } catch (error) {
      setFormError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="auth-shell">
      <section className="auth-panel">
        <Brand />
        <div className="auth-kicker">PRIVATE OPERATIONS CONSOLE</div>
        {auth.status === "unverified" ? (
          <>
            <h1>Verify your email</h1>
            <p className="auth-description">
              Wayfare staff access is available only to verified accounts. We sent a link to{" "}
              <strong>{auth.user?.email ?? "your email address"}</strong>.
            </p>
            {notice && <div className="notice success">{notice}</div>}
            {(formError || auth.error) && (
              <div className="notice error" role="alert">{formError || auth.error}</div>
            )}
            <button className="desk-button dark full" onClick={handleVerificationCheck} disabled={busy} data-testid="button-check-verification">
              <CheckCircle2 size={15} /> I’ve verified my email
            </button>
            <button className="desk-button plain full" onClick={handleResend} disabled={busy} data-testid="button-resend-verification">
              Resend verification email
            </button>
            <button className="text-action" onClick={() => void auth.signOut()} data-testid="button-auth-signout">
              <LogOut size={14} /> Sign out
            </button>
          </>
        ) : auth.status === "not_admin" ? (
          <>
            <div className="auth-symbol denied"><ShieldCheck size={20} /></div>
            <h1>Staff access required</h1>
            <p className="auth-description">
              This verified account is not provisioned for the Wayfare staff desk. Access is managed by an administrator.
            </p>
            <div className="notice error" role="status">{auth.error}</div>
            <button className="desk-button plain full" onClick={() => void auth.signOut()} data-testid="button-auth-signout">
              <LogOut size={15} /> Sign out
            </button>
          </>
        ) : auth.status === "error" ? (
          <>
            <div className="auth-symbol denied"><AlertCircle size={20} /></div>
            <h1>Session unavailable</h1>
            <p className="auth-description">The staff session could not be confirmed. Your access has not been bypassed.</p>
            <div className="notice error" role="alert">{auth.error || "An unexpected authentication error occurred."}</div>
            <button className="desk-button plain full" onClick={() => window.location.reload()} data-testid="button-retry-auth">
              <RefreshCw size={14} /> Try again
            </button>
            {auth.user && (
              <button className="text-action" onClick={() => void auth.signOut()} data-testid="button-auth-signout">
                <LogOut size={14} /> Sign out
              </button>
            )}
          </>
        ) : (
          <>
            <div className="auth-symbol"><ShieldCheck size={20} /></div>
            <h1>Sign in to review</h1>
            <p className="auth-description">Use your verified Wayfare staff account to open the driver application queue.</p>
            {(formError || auth.error) && <div className="notice error" role="alert">{formError || auth.error}</div>}
            {!auth.configurationReady && (
              <div className="notice error" role="alert">Firebase web configuration is not available. Contact your app administrator.</div>
            )}
            <form className="auth-form" onSubmit={handleSignIn}>
              <label>
                Work email
                <input className="desk-input" type="email" autoComplete="username" required value={email} onChange={(event) => setEmail(event.target.value)} data-testid="input-signin-email" />
              </label>
              <label>
                Password
                <input className="desk-input" type="password" autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} data-testid="input-signin-password" />
              </label>
              <button className="desk-button dark full" type="submit" disabled={busy || !auth.configurationReady} data-testid="button-signin">
                {busy ? "Signing in…" : "Continue to staff desk"} <ChevronRight size={15} />
              </button>
            </form>
            <div className="auth-footnote"><ShieldCheck size={13} /> Private staff access · actions are recorded</div>
          </>
        )}
      </section>
      <aside className="auth-side-note">
        <div className="side-note-line" />
        <p>One queue.<br />Clear decisions.</p>
        <span>Driver application review</span>
      </aside>
    </main>
  );
}

function ReviewDesk() {
  const auth = useAuth();
  const applicationsQuery = useDriverApplications();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [decision, setDecision] = useState<"approved" | "needs_follow_up" | "rejected" | null>(null);
  const [decisionNote, setDecisionNote] = useState("");
  const [decisionError, setDecisionError] = useState("");
  const [feedback, setFeedback] = useState("");
  const reviewPerson = useReviewPerson();

  const applications = applicationsQuery.data ?? [];
  const filtered = useMemo(() => {
    const needle = search.trim().toLowerCase();
    if (!needle) return applications;
    return applications.filter((app) =>
      [app.displayName, app.email ?? "", app.city ?? "", app.registration ?? ""]
        .some((value) => value.toLowerCase().includes(needle)),
    );
  }, [applications, search]);
  const selected = filtered.find((app) => app.userId === selectedId) ?? filtered[0] ?? null;

  useEffect(() => {
    if (!selected && filtered.length > 0) setSelectedId(filtered[0].userId);
    else if (selected && selectedId !== selected.userId) setSelectedId(selected.userId);
  }, [filtered, selected, selectedId]);

  const beginDecision = (outcome: "approved" | "needs_follow_up" | "rejected") => {
    setDecision(outcome);
    setDecisionNote("");
    setDecisionError("");
  };

  const submitDecision = async () => {
    if (!selected || !decision) return;
    const note = decisionNote.trim();
    if (decision !== "approved" && !note) {
      setDecisionError("Add a staff note before recording this decision.");
      return;
    }
    if (decision === "approved" && selected.incompleteComplianceDocuments.length > 0) {
      setDecisionError("Approval is unavailable until every required document check is complete.");
      return;
    }
    setDecisionError("");
    try {
      await reviewPerson.mutateAsync({
        userId: selected.userId,
        input: {
          subjectType: "driver",
          outcome: decision,
          ...(note ? { note } : {}),
          ...(decision === "approved" ? { checksConfirmed: true } : {}),
        },
      });
      setFeedback(`${selected.displayName}: ${decision === "approved" ? "application approved" : decision === "rejected" ? "application rejected" : "follow-up recorded"}.`);
      setDecision(null);
      setDecisionNote("");
    } catch (error) {
      setDecisionError(errorMessage(error));
    }
  };

  const queryError = applicationsQuery.error ? errorMessage(applicationsQuery.error) : null;
  const pendingCount = applications.length;

  return (
    <>
      <LegacyRouteNav />
      <main className="desk-shell">
      <header className="desk-topbar">
        <Brand />
        <div className="topbar-center"><span className="topbar-label">Operations</span><ChevronRight size={13} /><strong>Driver applications</strong></div>
        <div className="topbar-user">
          <span className="secure-indicator"><i /> Secure session</span>
          <span className="topbar-avatar">{(auth.admin?.displayName || "S").slice(0, 1).toUpperCase()}</span>
          <span className="staff-name">{auth.admin?.displayName || "Staff"}</span>
          <button className="icon-quiet" title="Sign out" aria-label="Sign out" onClick={() => void auth.signOut()} data-testid="button-signout"><LogOut size={16} /></button>
        </div>
      </header>

      <div className="desk-heading">
        <div>
          <div className="desk-eyebrow">REVIEW DESK <span>·</span> DRIVER ONBOARDING</div>
          <h1>Application queue</h1>
          <p>Review documents received outside Wayfare, then record each check and decision here.</p>
        </div>
        <button className="desk-button plain refresh-button" onClick={() => void applicationsQuery.refetch()} disabled={applicationsQuery.isFetching} data-testid="button-refresh-applications">
          <RefreshCw size={14} className={applicationsQuery.isFetching ? "spin-once" : ""} /> {applicationsQuery.isFetching ? "Refreshing" : "Refresh"}
        </button>
      </div>

      {feedback && (
        <div className="feedback-bar" role="status" data-testid="status-review-success">
          <CheckCircle2 size={16} /> {feedback}
          <button onClick={() => setFeedback("")} aria-label="Dismiss confirmation"><X size={14} /></button>
        </div>
      )}

      <section className="desk-workspace">
        <aside className="queue-pane">
          <div className="queue-head">
            <div><span className="section-overline">CURRENT WORK</span><h2>Pending reviews <span>{pendingCount}</span></h2></div>
            <span className="queue-mark"><ClipboardCheck size={17} /></span>
          </div>
          <label className="queue-search">
            <Search size={15} />
            <input aria-label="Search pending applications" placeholder="Name, email, city…" value={search} onChange={(event) => setSearch(event.target.value)} data-testid="input-application-search" />
            {search && <button aria-label="Clear search" onClick={() => setSearch("")}><X size={13} /></button>}
          </label>
          <div className="queue-list" aria-label="Pending driver applications">
            {applicationsQuery.isLoading ? (
              <div className="queue-loading" aria-label="Loading applications">
                {[0, 1, 2].map((item) => <div className="queue-skeleton" key={item}><i /><span /><span /></div>)}
              </div>
            ) : queryError ? (
              <div className="queue-error">
                <AlertCircle size={18} />
                <strong>Queue unavailable</strong>
                <span>{queryError}</span>
                <button className="desk-button plain small" onClick={() => void applicationsQuery.refetch()} data-testid="button-retry-applications">Retry</button>
              </div>
            ) : filtered.length === 0 ? (
              <div className="queue-empty">
                {search ? <Search size={19} /> : <CheckCircle2 size={19} />}
                <strong>{search ? "No matching applications" : "Queue is clear"}</strong>
                <span>{search ? "Try another name, email, city or registration." : "There are no pending driver applications right now."}</span>
              </div>
            ) : (
              filtered.map((app) => (
                <button
                  className={`application-row ${selected?.userId === app.userId ? "selected" : ""}`}
                  key={app.userId}
                  onClick={() => { setSelectedId(app.userId); setFeedback(""); }}
                  data-testid={`application-row-${app.userId}`}
                >
                  <span className="application-initial">{app.displayName.slice(0, 1).toUpperCase()}</span>
                  <span className="application-row-copy">
                    <span className="application-row-name">{app.displayName}</span>
                    <span className="application-row-detail">{app.city || "Location not provided"}{app.vehicle ? ` · ${app.vehicle}` : ""}</span>
                    <span className="application-row-status"><i className={`status-dot ${app.applicationStatus}`} /> {APPLICATION_LABELS[app.applicationStatus] ?? app.applicationStatus.replaceAll("_", " ")}</span>
                  </span>
                  <ChevronRight size={15} className="row-chevron" />
                </button>
              ))
            )}
          </div>
          <div className="queue-footer"><span className="tiny-lock"><ShieldCheck size={12} /></span> Only pending driver applications</div>
        </aside>

        <section className="review-pane">
          {applicationsQuery.isLoading ? (
            <div className="review-loading">
              <div className="detail-skeleton wide" /><div className="detail-skeleton" /><div className="detail-skeleton" /><div className="detail-skeleton tall" />
            </div>
          ) : queryError ? (
            <div className="review-empty error"><AlertCircle size={24} /><h2>Application data could not load</h2><p>{queryError}</p><button className="desk-button dark" onClick={() => void applicationsQuery.refetch()} data-testid="button-retry-detail">Retry loading</button></div>
          ) : selected ? (
            <ApplicationReview
              application={selected}
              onDecision={beginDecision}
              decisionPending={reviewPerson.isPending}
              onRefresh={() => void applicationsQuery.refetch()}
            />
          ) : (
            <div className="review-empty">
              <div className="empty-seal"><Check size={22} /></div>
              <span className="section-overline">UP TO DATE</span>
              <h2>No applications to review</h2>
              <p>New submissions will appear here when they’re ready for staff review.</p>
              <button className="desk-button plain" onClick={() => void applicationsQuery.refetch()} data-testid="button-refresh-empty"><RefreshCw size={14} /> Check for applications</button>
            </div>
          )}
        </section>
      </section>

      {decision && selected && (
        <div className="modal-scrim" role="presentation">
          <section className="decision-modal" role="dialog" aria-modal="true" aria-labelledby="decision-title">
            <div className={`decision-icon ${decision}`}><ShieldCheck size={20} /></div>
            <div className="modal-overline">FINAL APPLICATION DECISION</div>
            <h2 id="decision-title">{decision === "approved" ? "Approve this driver?" : decision === "rejected" ? "Reject this application?" : "Request follow-up?"}</h2>
            <p>
              {decision === "approved"
                ? `This records a separate approval for ${selected.displayName}. All required checks must already be complete.`
                : decision === "rejected"
                  ? `This records a rejection for ${selected.displayName}. This decision will be added to the staff review history.`
                  : `This returns ${selected.displayName}’s application for additional information.`}
            </p>
            {decision === "approved" && selected.incompleteComplianceDocuments.length > 0 && (
              <div className="notice error" role="alert">
                Incomplete checks remain: {selected.incompleteComplianceDocuments.map(documentLabel).join(", ")}. Approval is blocked until they are recorded.
              </div>
            )}
            <label className="form-label">
              Staff note {decision !== "approved" && <em>Required</em>}
              <textarea className="desk-textarea" rows={3} maxLength={1000} value={decisionNote} onChange={(event) => setDecisionNote(event.target.value)} placeholder={decision === "approved" ? "Optional context for the review record" : "Explain the reason for this decision"} data-testid="input-decision-note" />
              <span className="character-count">{decisionNote.length}/1000</span>
            </label>
            {decisionError && <div className="notice error" role="alert" data-testid="error-decision">{decisionError}</div>}
            <div className="modal-actions">
              <button className="desk-button plain" onClick={() => setDecision(null)} disabled={reviewPerson.isPending} data-testid="button-cancel-decision">Cancel</button>
              <button
                className={`desk-button ${decision === "rejected" ? "danger" : "dark"}`}
                onClick={() => void submitDecision()}
                disabled={reviewPerson.isPending || (decision === "approved" && selected.incompleteComplianceDocuments.length > 0)}
                data-testid={`button-confirm-${decision}`}
              >
                {reviewPerson.isPending ? "Recording…" : decision === "approved" ? "Confirm approval" : decision === "rejected" ? "Confirm rejection" : "Record follow-up"}
              </button>
            </div>
          </section>
        </div>
      )}
      </main>
    </>
  );
}

function documentLabel(value: string): string {
  return DOCUMENTS.find((document) => document.type === value)?.short ?? value.replaceAll("_", " ");
}

function ApplicationReview({
  application,
  onDecision,
  decisionPending,
  onRefresh,
}: {
  application: DriverApplication;
  onDecision: (outcome: "approved" | "needs_follow_up" | "rejected") => void;
  decisionPending: boolean;
  onRefresh: () => void;
}) {
  const incomplete = application.incompleteComplianceDocuments ?? [];
  const checksComplete = incomplete.length === 0;
  const licenceExpiry =
    application.complianceChecks?.find(
      (check) => check.documentType === "driver_license",
    )?.expiresOn ?? application.licenceExpiry;
  const expiredLicence = isExpired(licenceExpiry);
  const submitted = application.submittedAt ? formatDate(application.submittedAt) : "Not provided";

  return (
    <div className="application-detail">
      <div className="detail-mobile-back"><ArrowLeft size={15} /><span>Application queue</span></div>
      <div className="detail-topline">
        <div>
          <div className="detail-meta"><span className="detail-id">ID {application.userId}</span><span>·</span><span>Received {submitted}</span></div>
          <h2 data-testid={`text-applicant-${application.userId}`}>{application.displayName}</h2>
          <div className="detail-person-line">
            {application.email && <span><Mail size={13} />{application.email}</span>}
            {application.contactPhone && <span><span className="phone-glyph">T</span>{application.contactPhone}</span>}
            {application.city && <span><MapPin size={13} />{application.city}</span>}
          </div>
        </div>
        <span className={`application-badge ${application.applicationStatus}`}>
          <i /> {APPLICATION_LABELS[application.applicationStatus] ?? application.applicationStatus.replaceAll("_", " ")}
        </span>
      </div>

      <div className="summary-strip">
        <div className="summary-cell"><span className="summary-label">VEHICLE</span><strong>{application.vehicle || "Not provided"}</strong><span>{application.vehicleColor || "Colour not provided"}</span></div>
        <div className="summary-cell"><span className="summary-label">REGISTRATION</span><strong className="mono-value">{application.registration || "Not provided"}</strong><span>Operating ref. {application.operatingLicenceReference || "Not provided"}</span></div>
        <div className={`summary-cell ${expiredLicence ? "expired" : ""}`}><span className="summary-label">LICENCE EXPIRY</span><strong>{formatDate(licenceExpiry)}</strong><span>{expiredLicence ? "Expired — follow up required" : "Driver licence record"}</span></div>
        <div className="summary-cell"><span className="summary-label">VERIFICATION</span><strong>{application.verificationStatus.replaceAll("_", " ")}</strong><span>Account verification status</span></div>
      </div>

      {expiredLicence && (
        <div className="alert-strip expired" role="alert" data-testid="alert-expired-license">
          <AlertCircle size={17} />
          <div><strong>Driver licence expired</strong><span>The recorded expiry date has passed. Do not mark this licence verified until current documentation has been reviewed.</span></div>
        </div>
      )}

      <section className="compliance-section">
        <div className="section-heading">
          <div><span className="section-overline">OFF-PLATFORM DOCUMENT REVIEW</span><h3>Compliance checks</h3></div>
          <span className={`check-progress ${checksComplete ? "complete" : ""}`}><span>{checksComplete ? <Check size={12} /> : incomplete.length}</span>{checksComplete ? "Checks complete" : `incomplete ${incomplete.length === 1 ? "check" : "checks"}`}</span>
        </div>
        <div className="external-review-note">
          <FileCheck2 size={17} />
          <p><strong>Review copies outside this app.</strong> Check the emailed copy, record any printed expiry date, and save the result. Only a verified driver licence requires an expiry date. Do not upload or store files here.</p>
        </div>
        <div className="document-list">
          {DOCUMENTS.map((document) => {
            const check = application.complianceChecks?.find((item) => item.documentType === document.type) ?? {
              documentType: document.type,
              status: "not_reviewed" as DriverComplianceStatus,
              expiresOn: null,
              staffNote: null,
              reviewedAt: null,
            };
            return (
              <ComplianceEditor
                key={`${application.userId}-${document.type}`}
                userId={application.userId}
                check={check}
                title={document.label}
                description={document.description}
              />
            );
          })}
        </div>
        {incomplete.length > 0 && (
          <div className="incomplete-warning" role="status" data-testid="status-incomplete-checks">
            <Clock3 size={15} />
            <span>Server reports incomplete: <strong>{incomplete.map(documentLabel).join(", ")}</strong>. Complete each required check before approval.</span>
          </div>
        )}
      </section>

      <section className="decision-section">
        <div className="decision-heading">
          <div><span className="section-overline">SEPARATE FROM DOCUMENT CHECKS</span><h3>Application decision</h3></div>
          {application.reviewedAt && <span className="last-reviewed">Last reviewed {formatDate(application.reviewedAt)}</span>}
        </div>
        <p>Choose the final outcome only after recording all relevant checks. Approval requires the server to report no incomplete documents.</p>
        {!checksComplete && (
          <div className="decision-blocked" data-testid="status-approval-blocked"><ShieldCheck size={15} /> Approval is unavailable while required checks remain incomplete.</div>
        )}
        <div className="decision-actions">
          <button className="desk-button approve" onClick={() => onDecision("approved")} disabled={!checksComplete || decisionPending} data-testid="button-start-approval"><BadgeCheck size={15} /> Approve driver</button>
          <button className="desk-button follow-up" onClick={() => onDecision("needs_follow_up")} disabled={decisionPending} data-testid="button-start-followup">Request follow-up</button>
          <button className="desk-button reject" onClick={() => onDecision("rejected")} disabled={decisionPending} data-testid="button-start-rejection">Reject</button>
          <button className="desk-button plain decision-refresh" onClick={onRefresh} data-testid="button-refresh-detail"><RefreshCw size={14} /> Refresh data</button>
        </div>
      </section>

      {application.staffNote && (
        <section className="existing-note">
          <span className="section-overline">APPLICATION NOTE</span>
          <p>{application.staffNote}</p>
        </section>
      )}
    </div>
  );
}

function ComplianceEditor({
  userId,
  check,
  title,
  description,
}: {
  userId: string;
  check: DriverComplianceCheck;
  title: string;
  description: string;
}) {
  const updateCompliance = useUpdateCompliance();
  const [status, setStatus] = useState<DriverComplianceStatus>(check.status);
  const [expiry, setExpiry] = useState(check.expiresOn ?? "");
  const [note, setNote] = useState(check.staffNote ?? "");
  const [localError, setLocalError] = useState("");
  const [saved, setSaved] = useState(false);
  const license = check.documentType === "driver_license";
  const expiryRequired = license && status === "verified";
  const expired = isExpired(expiry);
  const statusError = updateCompliance.error ? errorMessage(updateCompliance.error) : "";

  useEffect(() => {
    setStatus(check.status);
    setExpiry(check.expiresOn ?? "");
    setNote(check.staffNote ?? "");
    setLocalError("");
  }, [check.status, check.expiresOn, check.staffNote, check.reviewedAt]);

  const saveCheck = async () => {
    setLocalError("");
    setSaved(false);
    if (status === "not_required" && !note.trim()) {
      setLocalError("A staff note is required when a document is not required.");
      return;
    }
    if (expiryRequired && !expiry) {
      setLocalError("Enter the licence expiry date before marking it verified.");
      return;
    }
    if (status === "verified" && expired) {
      setLocalError("An expired document cannot be marked verified.");
      return;
    }
    try {
      await updateCompliance.mutateAsync({
        userId,
        input: {
          documentType: check.documentType,
          status,
          expiresOn: expiry || null,
          ...(note.trim() ? { note: note.trim() } : {}),
        },
      });
      setSaved(true);
    } catch {
      // The mutation error is rendered from the API error below.
    }
  };

  return (
    <article className={`document-card ${check.status !== "not_reviewed" ? "reviewed" : ""}`} data-testid={`check-card-${check.documentType}`}>
      <div className="document-card-heading">
        <span className={`document-icon ${check.status}`}><FileCheck2 size={16} /></span>
        <div className="document-title-wrap"><h4>{title}</h4><p>{description}</p></div>
        <span className={`check-state ${check.status}`}>{check.status === "verified" && <Check size={11} />}{STATUS_LABELS[check.status]}</span>
      </div>
      <div className="document-controls">
        <label className="control-field">
          <span>Review result</span>
          <select value={status} onChange={(event) => { setStatus(event.target.value as DriverComplianceStatus); setSaved(false); }} data-testid={`select-status-${check.documentType}`}>
            <option value="not_reviewed">Not reviewed</option>
            <option value="verified">Verified</option>
            <option value="needs_follow_up">Needs follow-up</option>
            <option value="not_required">Not required</option>
          </select>
        </label>
        <label className="control-field expiry-field">
          <span>{license ? "Licence expiry" : "Expiry date (if shown)"} {expiryRequired && <em>Required</em>}</span>
          <input
            type="date"
            value={expiry}
            onChange={(event) => { setExpiry(event.target.value); setSaved(false); }}
            aria-label={`${title} expiry date`}
            data-testid={license ? "input-license-expiry" : `input-expiry-${check.documentType}`}
          />
        </label>
        <label className="control-field note-field">
          <span>Staff note {status === "not_required" && <em>Required</em>}</span>
          <input type="text" maxLength={500} value={note} onChange={(event) => { setNote(event.target.value); setSaved(false); }} placeholder={status === "not_required" ? "Why is this document not required?" : status === "needs_follow_up" ? "What is missing or unclear?" : "Optional: what did the emailed copy confirm?"} data-testid={`input-note-${check.documentType}`} />
        </label>
        <button className="desk-button save-check" onClick={() => void saveCheck()} disabled={updateCompliance.isPending} data-testid={`button-save-${check.documentType}`}>
          {updateCompliance.isPending ? "Saving…" : saved ? <><Check size={14} /> Saved</> : "Save check"}
        </button>
      </div>
      {expired && (
        <div className="field-alert" role="alert"><AlertCircle size={13} /> Expired on {formatDate(expiry)}. This document cannot be verified.</div>
      )}
      {localError && <div className="field-alert" role="alert">{localError}</div>}
      {statusError && <div className="field-alert api-error" role="alert" data-testid={`error-check-${check.documentType}`}>{statusError}</div>}
      {check.reviewedAt && <div className="reviewed-at">Last recorded {formatDate(check.reviewedAt)}{check.staffNote ? ` · ${check.staffNote}` : ""}</div>}
    </article>
  );
}

export default AuthSurface;
