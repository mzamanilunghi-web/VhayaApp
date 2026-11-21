import { useMemo, useState, type FormEvent } from "react";
import { VhayaMark } from "@/components/vhaya-mark";
import {
  AlertCircle,
  ArrowLeft,
  Check,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Database,
  FileCheck2,
  LogOut,
  Mail,
  RefreshCw,
  Search,
  ShieldCheck,
  Trash2,
  UserRound,
  X,
} from "lucide-react";
import { Link } from "wouter";
import {
  useAccountDeletionRequests,
  useCompleteAccountDeletion,
  useRejectAccountDeletion,
} from "@/hooks/useAdminData";
import type { AccountDeletionRequest, AccountDeletionResult } from "@/lib/vhaya-api";
import { useAuth } from "@/lib/auth-context";
import { LegacyRouteNav } from "@/pages/admin-navigation";

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "The request could not be completed.";
}

function formatDate(value: string | null | undefined): string {
  if (!value) return "Not provided";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Not provided";
  return new Intl.DateTimeFormat("en-ZA", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function initials(name: string): string {
  return name.trim().split(/\s+/).slice(0, 2).map((part) => part[0] ?? "").join("").toUpperCase() || "V";
}

function pseudonymizedState(request: AccountDeletionRequest): boolean | null {
  const fields = request as AccountDeletionRequest & {
    dataPseudonymized?: unknown;
    pseudonymized?: unknown;
    isPseudonymized?: unknown;
    alreadyPseudonymized?: unknown;
  };
  for (const value of [
    fields.dataPseudonymized,
    fields.pseudonymized,
    fields.isPseudonymized,
    fields.alreadyPseudonymized,
  ]) {
    if (typeof value === "boolean") return value;
  }
  return null;
}

function Brand() {
  return (
    <div className="review-brand" aria-label="Vhaya staff">
      <VhayaMark className="review-brand-mark" />
      <div>
        <div className="review-brand-name">Vhaya</div>
        <div className="review-brand-subtitle">staff desk</div>
      </div>
    </div>
  );
}

function AccountDeletionAuth() {
  const auth = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const signIn = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      await auth.signIn(email, password);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  if (auth.status === "loading") {
    return (
      <main className="account-auth-shell">
        <section className="account-auth-card" aria-label="Checking staff session">
          <Brand />
          <div className="account-auth-skeleton" />
          <div className="account-auth-skeleton short" />
          <div className="account-auth-skeleton button" />
          <p>Checking your staff session…</p>
        </section>
      </main>
    );
  }

  if (auth.status === "ready") return null;

  const unverified = auth.status === "unverified";
  const denied = auth.status === "not_admin";
  const sessionError = auth.status === "error";
  return (
    <main className="account-auth-shell">
      <section className="account-auth-card">
        <Brand />
        <span className="section-overline">PRIVATE OPERATIONS CONSOLE</span>
        <div className="account-auth-icon"><ShieldCheck size={19} /></div>
        <h1>{denied ? "Staff access required" : unverified ? "Verify your email" : sessionError ? "Session unavailable" : "Sign in to review"}</h1>
        <p>
          {denied
            ? "This verified account is not provisioned for the Vhaya staff desk."
            : unverified
              ? `Staff access requires a verified account. Check ${auth.user?.email ?? "your inbox"} for a verification link.`
              : sessionError
                ? "The staff session could not be confirmed. Access has not been bypassed."
                : "Use your verified Vhaya staff account to review account deletion requests."}
        </p>
        {(error || auth.error) && <div className="account-auth-error" role="alert">{error || auth.error}</div>}
        {auth.status === "signed_out" && (
          <form className="account-auth-form" onSubmit={signIn}>
            <label>
              Work email
              <input type="email" autoComplete="username" required value={email} onChange={(event) => setEmail(event.target.value)} data-testid="input-deletion-auth-email" />
            </label>
            <label>
              Password
              <input type="password" autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} data-testid="input-deletion-auth-password" />
            </label>
            <button className="desk-button dark full" type="submit" disabled={busy || !auth.configurationReady} data-testid="button-deletion-auth-signin">
              {busy ? "Signing in…" : "Continue to staff desk"} <ChevronRight size={15} />
            </button>
          </form>
        )}
        {auth.user && (
          <button className="desk-button plain full account-auth-signout" onClick={() => void auth.signOut()} data-testid="button-deletion-auth-signout">
            <LogOut size={14} /> Sign out
          </button>
        )}
      </section>
    </main>
  );
}

function RequestStatus({ status }: { status: AccountDeletionRequest["status"] }) {
  const Icon = status === "completed" ? CheckCircle2 : status === "processing" ? Clock3 : status === "rejected" ? X : Clock3;
  return (
    <span className={`deletion-status ${status}`} data-testid={`status-deletion-${status}`}>
      <Icon size={12} /> {status.replaceAll("_", " ")}
    </span>
  );
}

function RequestDetails({ request }: { request: AccountDeletionRequest }) {
  const pseudonymized = pseudonymizedState(request);
  return (
    <>
      <div className="deletion-identity">
        <div className="deletion-avatar">{initials(request.displayName)}</div>
        <div className="deletion-identity-copy">
          <span className="section-overline">REQUEST SUBJECT</span>
          <h2 data-testid={`text-deletion-name-${request.id}`}>{request.displayName}</h2>
          <span className="deletion-userid" data-testid={`text-deletion-userid-${request.id}`}>Account ID · {request.userId}</span>
        </div>
        <RequestStatus status={request.status} />
      </div>
      <div className="deletion-facts">
        <div className="deletion-fact">
          <span>ROLE</span>
          <strong data-testid={`text-deletion-role-${request.id}`}><UserRound size={13} /> {request.role}</strong>
        </div>
        <div className="deletion-fact">
          <span>REQUESTED</span>
          <strong data-testid={`text-deletion-date-${request.id}`}>{formatDate(request.requestedAt)}</strong>
        </div>
        <div className="deletion-fact">
          <span>DATA PSEUDONYMIZED</span>
          <strong className={pseudonymized === true ? "fact-good" : ""} data-testid={`status-pseudonymized-${request.id}`}>
            <Database size={13} /> {pseudonymized === null ? "Not recorded" : pseudonymized ? "Yes" : "No"}
          </strong>
        </div>
        <div className="deletion-fact">
          <span>EMAIL</span>
          <strong className="deletion-email" data-testid={`text-deletion-email-${request.id}`}>
            <Mail size={13} /> {request.email || "No email on record"}
          </strong>
        </div>
      </div>
      <section className="deletion-reason-card">
        <span className="section-overline">RIDER / DRIVER NOTE</span>
        <p data-testid={`text-deletion-reason-${request.id}`}>{request.reason?.trim() || "No reason was included with this request."}</p>
      </section>
    </>
  );
}

function AccountDeletionsDesk() {
  const auth = useAuth();
  const query = useAccountDeletionRequests();
  const complete = useCompleteAccountDeletion();
  const reject = useRejectAccountDeletion();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [identityConfirmed, setIdentityConfirmed] = useState(false);
  const [subscriptionConfirmed, setSubscriptionConfirmed] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [actionError, setActionError] = useState("");
  const [feedback, setFeedback] = useState("");
  const [lastResult, setLastResult] = useState<{ id: string; result: AccountDeletionResult } | null>(null);

  const requests = query.data ?? [];
  const filteredRequests = useMemo(() => {
    const needle = search.trim().toLowerCase();
    if (!needle) return requests;
    return requests.filter((request) =>
      [request.displayName, request.email ?? "", request.userId, request.role, request.status]
        .some((value) => value.toLowerCase().includes(needle)),
    );
  }, [requests, search]);
  const selected = filteredRequests.find((request) => request.id === selectedId) ?? filteredRequests[0] ?? null;
  const isPending = complete.isPending || reject.isPending;
  const needsCompletion = selected?.status === "requested" || selected?.status === "processing";
  const completionReady = identityConfirmed && subscriptionConfirmed;
  const resultForSelected = selected && lastResult?.id === selected.id ? lastResult.result : null;

  const selectRequest = (requestId: string) => {
    setSelectedId(requestId);
    setIdentityConfirmed(false);
    setSubscriptionConfirmed(false);
    setRejectReason("");
    setActionError("");
    setFeedback("");
    setLastResult(null);
  };

  const submitCompletion = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selected || !needsCompletion || !completionReady || isPending) return;
    setActionError("");
    setFeedback("");
    try {
      const result = await complete.mutateAsync({ requestId: selected.id });
      setLastResult({ id: selected.id, result });
      setFeedback(result.status === "processing"
        ? "Deletion workflow is processing. Check the request status before retrying."
        : "Deletion request completed.");
      setIdentityConfirmed(false);
      setSubscriptionConfirmed(false);
    } catch (caught) {
      setActionError(errorMessage(caught));
    }
  };

  const submitRejection = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selected || selected.status !== "requested" || isPending) return;
    const reason = rejectReason.trim();
    if (!reason) {
      setActionError("A reason is required to reject an unprocessed request.");
      return;
    }
    setActionError("");
    setFeedback("");
    try {
      await reject.mutateAsync({ requestId: selected.id, reason });
      setFeedback("Request rejected. The reason has been recorded.");
      setRejectReason("");
    } catch (caught) {
      setActionError(errorMessage(caught));
    }
  };

  const queryError = query.error ? errorMessage(query.error) : null;
  const requestedCount = requests.filter((request) => request.status === "requested").length;
  const processingCount = requests.filter((request) => request.status === "processing").length;

  return (
    <main className="desk-shell account-deletions-shell">
      <header className="desk-topbar">
        <Brand />
        <div className="topbar-center"><span className="topbar-label">Operations</span><ChevronRight size={13} /><strong>Account deletions</strong></div>
        <div className="topbar-user">
          <span className="secure-indicator"><i /> Secure session</span>
          <span className="topbar-avatar">{(auth.admin?.displayName || "S").slice(0, 1).toUpperCase()}</span>
          <span className="staff-name">{auth.admin?.displayName || "Staff"}</span>
          <button className="icon-quiet" title="Sign out" aria-label="Sign out" onClick={() => void auth.signOut()} data-testid="button-deletions-signout"><LogOut size={16} /></button>
        </div>
      </header>

      <div className="desk-heading deletion-page-heading">
        <div>
          <div className="desk-eyebrow">REVIEW DESK <span>·</span> ACCOUNT PRIVACY</div>
          <h1>Account deletion requests</h1>
          <p>Confirm identity and subscription status before carrying out an account deletion.</p>
        </div>
        <div className="deletion-heading-actions">
          <Link href="/driver-reviews" className="desk-button plain" data-testid="link-driver-review"><ArrowLeft size={14} /> Driver reviews</Link>
          <button className="desk-button plain refresh-button" onClick={() => void query.refetch()} disabled={query.isFetching} data-testid="button-refresh-deletions">
            <RefreshCw size={14} className={query.isFetching ? "spin-once" : ""} /> {query.isFetching ? "Refreshing" : "Refresh"}
          </button>
        </div>
      </div>

      <section className="deletion-overview" aria-label="Request totals">
        <div className="deletion-overview-label"><span className="section-overline">QUEUE OVERVIEW</span><strong>Privacy requests</strong></div>
        <div><span>Awaiting review</span><strong data-testid="count-deletion-requested">{requestedCount}</strong></div>
        <div><span>Processing</span><strong data-testid="count-deletion-processing">{processingCount}</strong></div>
        <div><span>Total records</span><strong data-testid="count-deletion-total">{requests.length}</strong></div>
      </section>

      {(feedback || actionError) && (
        <div className={`feedback-bar deletion-feedback ${actionError ? "error" : ""}`} role={actionError ? "alert" : "status"} data-testid={actionError ? "error-deletion-action" : "status-deletion-success"}>
          {actionError ? <AlertCircle size={16} /> : <CheckCircle2 size={16} />}
          <span>{actionError || feedback}</span>
          <button onClick={() => { setFeedback(""); setActionError(""); }} aria-label="Dismiss message" data-testid="button-dismiss-deletion-message"><X size={14} /></button>
        </div>
      )}

      <section className="deletion-workspace" aria-label="Account deletion review">
        <aside className="deletion-queue-pane">
          <div className="deletion-queue-heading">
            <div><span className="section-overline">CURRENT WORK</span><h2>All requests <span>{requests.length}</span></h2></div>
            <span className="deletion-queue-icon"><Trash2 size={16} /></span>
          </div>
          <label className="deletion-search">
            <Search size={15} />
            <input aria-label="Search account deletion requests" placeholder="Name, email, account ID…" value={search} onChange={(event) => setSearch(event.target.value)} data-testid="input-deletion-search" />
            {search && <button type="button" aria-label="Clear search" onClick={() => setSearch("")} data-testid="button-clear-deletion-search"><X size={13} /></button>}
          </label>
          <div className="deletion-queue-list" aria-label="Deletion request list">
            {query.isLoading ? (
              <div className="deletion-loading" aria-label="Loading account deletion requests" data-testid="loading-deletion-requests">
                {[0, 1, 2, 3].map((item) => <div className="deletion-skeleton" key={item}><i /><span /><span /></div>)}
              </div>
            ) : queryError ? (
              <div className="deletion-queue-state error" role="alert" data-testid="error-deletion-requests">
                <AlertCircle size={20} />
                <strong>Requests unavailable</strong>
                <span>{queryError}</span>
                <button className="desk-button plain small" onClick={() => void query.refetch()} disabled={query.isFetching} data-testid="button-retry-deletions">
                  <RefreshCw size={13} /> {query.isFetching ? "Retrying" : "Retry"}
                </button>
              </div>
            ) : filteredRequests.length === 0 ? (
              <div className="deletion-queue-state" data-testid="empty-deletion-requests">
                {search ? <Search size={20} /> : <CheckCircle2 size={20} />}
                <strong>{search ? "No matching requests" : "No deletion requests"}</strong>
                <span>{search ? "Try another name, email address or account ID." : "New rider and driver requests will appear here."}</span>
              </div>
            ) : (
              filteredRequests.map((request) => (
                <button
                  className={`deletion-queue-row ${selected?.id === request.id ? "selected" : ""}`}
                  key={request.id}
                  onClick={() => selectRequest(request.id)}
                  data-testid={`row-deletion-request-${request.id}`}
                  aria-pressed={selected?.id === request.id}
                >
                  <span className="deletion-row-avatar">{initials(request.displayName)}</span>
                  <span className="deletion-row-copy">
                    <span className="deletion-row-name">{request.displayName}</span>
                    <span className="deletion-row-detail">{request.role} · {formatDate(request.requestedAt)}</span>
                    <RequestStatus status={request.status} />
                  </span>
                  <ChevronRight size={15} className="deletion-row-chevron" />
                </button>
              ))
            )}
          </div>
          <div className="deletion-queue-footer"><ShieldCheck size={13} /> Staff actions are recorded</div>
        </aside>

        <section className="deletion-detail-pane">
          {selected ? (
            <article className="deletion-detail" key={selected.id}>
              <div className="deletion-detail-kicker"><span className="section-overline">REQUEST REVIEW</span><span className="deletion-id">REQ · {selected.id}</span></div>
              <RequestDetails request={selected} />

              {resultForSelected && (
                <section className={`deletion-result ${resultForSelected.status}`} role="status" data-testid="status-deletion-result">
                  {resultForSelected.status === "processing" ? <Clock3 size={17} /> : <CheckCircle2 size={17} />}
                  <div>
                    <strong>{resultForSelected.status === "processing" ? "Deletion is processing" : resultForSelected.deleted ? "Account deletion complete" : "Request completed"}</strong>
                    <span>{resultForSelected.deleted ? "The account was deleted." : resultForSelected.status === "processing" ? "The workflow has started and may need time to finish." : "The request was completed without an account deletion."}</span>
                    {resultForSelected.retainedData.length > 0 && <span>Retained data: {resultForSelected.retainedData.join(", ")}</span>}
                  </div>
                </section>
              )}

              {selected.status === "processing" && (
                <div className="deletion-processing-note" role="status" data-testid="status-deletion-processing">
                  <Clock3 size={15} />
                  <span>This request is still processing. If the operation stalled, verify both checks below and retry completion.</span>
                </div>
              )}

              {needsCompletion ? (
                <div className="deletion-actions-grid">
                  <section className="deletion-action-card">
                    <div className="deletion-action-title">
                      <span className="deletion-action-icon"><ShieldCheck size={16} /></span>
                      <div><span className="section-overline">{selected.status === "processing" ? "RETRY WORKFLOW" : "REQUIRED BEFORE COMPLETION"}</span><h3>{selected.status === "processing" ? "Continue deletion" : "Confirm safeguards"}</h3></div>
                    </div>
                    <p>Both confirmations are required before the account deletion can be submitted.</p>
                    <form onSubmit={(event) => void submitCompletion(event)} data-testid="form-complete-deletion">
                      <label className="deletion-check">
                        <input type="checkbox" checked={identityConfirmed} onChange={(event) => setIdentityConfirmed(event.target.checked)} data-testid="checkbox-confirm-identity" />
                        <span className="deletion-check-box"><Check size={12} /></span>
                        <span><strong>Account ownership confirmed</strong><small>I verified the requester controls this account.</small></span>
                      </label>
                      <label className="deletion-check">
                        <input type="checkbox" checked={subscriptionConfirmed} onChange={(event) => setSubscriptionConfirmed(event.target.checked)} data-testid="checkbox-confirm-subscription" />
                        <span className="deletion-check-box"><Check size={12} /></span>
                        <span><strong>External subscription cancelled or none exists</strong><small>I confirmed no active external subscription will be left behind.</small></span>
                      </label>
                      <button className="desk-button dark full deletion-submit" type="submit" disabled={!completionReady || isPending} data-testid="button-complete-deletion">
                        {complete.isPending ? <><RefreshCw size={14} className="spin-once" /> Processing request…</> : selected.status === "processing" ? <><RefreshCw size={14} /> Retry completion</> : <><Trash2 size={14} /> Complete account deletion</>}
                      </button>
                      {complete.isError && <div className="deletion-inline-error" role="alert" data-testid="error-complete-deletion">{errorMessage(complete.error)}</div>}
                    </form>
                  </section>

                  {selected.status === "requested" && (
                    <section className="deletion-action-card reject-card">
                      <div className="deletion-action-title">
                        <span className="deletion-action-icon reject"><X size={16} /></span>
                        <div><span className="section-overline">DECLINE REQUEST</span><h3>Reject with a reason</h3></div>
                      </div>
                      <p>Only requests that have not entered processing can be rejected.</p>
                      <form onSubmit={(event) => void submitRejection(event)} data-testid="form-reject-deletion">
                        <label className="deletion-reason-label" htmlFor="deletion-reject-reason">Reason for rejection</label>
                        <textarea id="deletion-reject-reason" value={rejectReason} onChange={(event) => setRejectReason(event.target.value)} maxLength={1000} required placeholder="Explain what prevented this request from being processed." data-testid="textarea-rejection-reason" />
                        <div className="deletion-form-foot"><span>{rejectReason.length}/1000</span>
                          <button className="desk-button danger full" type="submit" disabled={!rejectReason.trim() || isPending} data-testid="button-reject-deletion">
                            {reject.isPending ? "Recording rejection…" : "Reject request"}
                          </button>
                        </div>
                        {reject.isError && <div className="deletion-inline-error" role="alert" data-testid="error-reject-deletion">{errorMessage(reject.error)}</div>}
                      </form>
                    </section>
                  )}
                </div>
              ) : (
                <section className="deletion-terminal-state" data-testid="status-deletion-terminal">
                  {selected.status === "completed" ? <CheckCircle2 size={18} /> : <X size={18} />}
                  <div><strong>Request {selected.status}</strong><span>{selected.status === "completed" ? "No further action is available for this completed request." : "This request has already been rejected and cannot be processed."}</span></div>
                </section>
              )}

              <div className="deletion-audit-note"><FileCheck2 size={14} /><span>Confirmations and decisions are submitted through the Vhaya staff API.</span></div>
            </article>
          ) : query.isLoading ? (
            <div className="deletion-detail-loading" aria-hidden="true"><div /><div /><div /><div /></div>
          ) : queryError ? (
            <div className="deletion-detail-empty error"><AlertCircle size={21} /><strong>Review is unavailable</strong><span>Retry the request queue to load a case.</span><button className="desk-button plain small" onClick={() => void query.refetch()} data-testid="button-retry-deletion-detail">Retry loading</button></div>
          ) : (
            <div className="deletion-detail-empty" data-testid="empty-deletion-detail">
              <div className="deletion-empty-mark"><Trash2 size={19} /></div>
              <span className="section-overline">PRIVACY REQUESTS</span>
              <strong>{search ? "No request matches this search" : "The queue is clear"}</strong>
              <span>{search ? "Adjust the search to find another rider or driver." : "There are no account deletion requests to review right now."}</span>
            </div>
          )}
        </section>
      </section>
    </main>
  );
}

export default function AccountDeletions() {
  const auth = useAuth();
  if (auth.status !== "ready") return <AccountDeletionAuth />;
  return <><LegacyRouteNav /><AccountDeletionsDesk /></>;
}
