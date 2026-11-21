import { useEffect, useState, type ReactNode } from "react";
import { Link, useLocation } from "wouter";
import { VhayaMark } from "@/components/vhaya-mark";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  Activity, AlertCircle, Check, ChevronDown, ChevronLeft, ChevronRight,
  CircleDollarSign, Download, Moon, Printer, RefreshCw, Search, ShieldCheck, Sun, X,
} from "lucide-react";
import {
  useAdminActivity, useAdminOverview, useCancelRide, usePeople, usePerson,
  useUpdatePersonRole, useSuspendPerson, useUnsuspendPerson, useDriverCompliance,
  useUpdateCompliance, useSupportCases, useUpdateSupportCase, useReplyToSupportCase,
  usePayments, useWallets, usePayouts, usePayoutBatches, useRequestRefund,
  useCreatePayout, useReconcilePayout, useRunWeeklyPayouts, useApprovePayoutBatch,
  usePayoutBatch, usePayoutRecipients, usePayoutBanks, useSavePayoutRecipient,
  useStartPayoutBatchTransfers, useVerifyPayoutTransfer,
  useRecordExternalPayoutBatchPayment, useRides,
} from "@/hooks/useAdminData";
import type {
  AdminActivity,
  AdminRide,
  DriverComplianceStatus,
  GetAdminRidesStatus,
  Person,
  Payment,
  Payout,
  PayoutBatch,
  PayoutDriverRecipient,
} from "@/lib/vhaya-api";
import { useAuth } from "@/lib/auth-context";

const links = [
  ["/", "Overview"], ["/rides", "Rides"], ["/drivers-map", "Drivers map"], ["/people", "People"], ["/driver-reviews", "Driver reviews"],
  ["/driver-compliance", "Compliance"], ["/support", "Support"], ["/finance", "Finance"], ["/subscriptions", "Billing"], ["/account-deletions", "Deletions"],
] as const;
const money = (minor: number, currency = "ZAR") => new Intl.NumberFormat("en-ZA", { style: "currency", currency }).format(minor / 100);
const date = (value: number | string | null | undefined) => value ? new Intl.DateTimeFormat("en-ZA", { dateStyle: "medium", timeStyle: "short" }).format(typeof value === "number" ? new Date(value) : new Date(value)) : "Not recorded";
const err = (e: unknown) => e instanceof Error ? e.message : "The operation could not be completed.";
const terminalRide = (s: string) => ["completed", "cancelled", "unfulfilled"].includes(s);
function Feedback({ children, error = false }: { children: ReactNode; error?: boolean }) {
  return <div className={`feedback ${error ? "error" : ""}`} role={error ? "alert" : "status"}>{error ? <AlertCircle size={15}/> : <Check size={15}/>}<span>{children}</span></div>;
}
function Confirm({ title, message, confirm, confirmLabel = "Confirm", onClose, pending = false, danger = false, disabled = false, children }: {
  title: string; message: string; confirm: () => void; confirmLabel?: string; onClose: () => void; pending?: boolean; danger?: boolean; disabled?: boolean; children?: ReactNode;
}) {
  return <div className="modal-backdrop" role="presentation"><section className="modal" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
    <div className="modal-mark"><ShieldCheck size={18}/></div><h2 id="confirm-title">{title}</h2><p>{message}</p>{children}
    <div className="modal-actions"><button className="button" onClick={onClose} disabled={pending} data-testid="button-confirm-cancel">Go back</button>
      <button className={`button ${danger ? "danger" : "primary"}`} onClick={confirm} disabled={pending || disabled} data-testid="button-confirm-action">{pending ? "Working…" : confirmLabel}</button></div>
  </section></div>;
}
export function PageFrame({ title, subtitle, eyebrow, children }: { title: string; subtitle: string; eyebrow: string; children: ReactNode }) {
  const [path] = useLocation(); const auth = useAuth();
  return <div className="vhaya">
    <header className="global-header">
      <Link href="/" className="brand"><VhayaMark className="brand-mark" /><span><b>vhaya</b><small>operations</small></span></Link>
      <nav className="global-nav" aria-label="Operations navigation">{links.map(([href, label]) =>
        <Link href={href} key={href} className={`global-nav-link ${path === href ? "active" : ""}`} data-testid={`nav-${label.toLowerCase().replaceAll(" ", "-")}`}>{label}</Link>)}</nav>
      <div className="global-user"><span className="secure-dot"/><span>{auth.admin?.displayName ?? "Staff"}</span><button className="button small" onClick={() => void auth.signOut()} data-testid="button-signout">Sign out</button></div>
    </header>
    <main className="page admin-page"><header className="page-head"><div><div className="eyebrow">{eyebrow}</div><h1 className="title">{title}</h1><p className="subtitle">{subtitle}</p></div></header>
      <div className="content admin-content">{children}</div>
    </main>
  </div>;
}
function QueryState({ loading, error, empty, retry }: { loading: boolean; error?: unknown; empty?: boolean; retry: () => void }) {
  if (loading) return <div className="state-box"><div className="skeleton" aria-label="Loading data"/></div>;
  if (error) return <div className="state-box error"><strong>Could not load this data.</strong><div>{err(error)}</div><button className="button" onClick={retry} data-testid="button-retry">Try again</button></div>;
  if (empty) return <div className="empty">Nothing to show yet. Try another search or check back later.</div>;
  return null;
}
function Table({ headers, children, min = 760 }: { headers: string[]; children: React.ReactNode; min?: number }) {
  return <div className="table-wrap"><table className="data-table" style={{ minWidth: min }}><thead><tr>{headers.map(h => <th key={h}>{h}</th>)}</tr></thead><tbody>{children}</tbody></table></div>;
}
function Tag({ children }: { children: string }) { return <span className={`tag ${children.toLowerCase().replaceAll(" ", "_")}`}>{children.replaceAll("_", " ")}</span>; }

export function OverviewPage() {
  const [auto, setAuto] = useState(false); const [dark, setDark] = useState(() => typeof localStorage === "undefined" || localStorage.getItem("vhaya-dark") !== "false");
  const [menu, setMenu] = useState(false);
  const overview = useAdminOverview(auto ? 300000 : false); const activity = useAdminActivity(30, auto ? 300000 : false);
  useEffect(() => {
    document.documentElement.classList.toggle("dark", dark);
    localStorage.setItem("vhaya-dark", String(dark));
  }, [dark]);
  const counts = overview.data?.summary;
  const categories = overview.data?.support.byCategory ?? []; const priorities = overview.data?.support.byPriority ?? [];
  const exportCsv = (name: string, rows: Array<Record<string, string | number>>) => {
    const keys = rows.length ? Object.keys(rows[0]) : [];
    const csv = [keys.join(","), ...rows.map(row => keys.map(key => `"${String(row[key] ?? "").replaceAll('"', '""')}"`).join(","))].join("\n");
    const url = URL.createObjectURL(new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" })); const a = document.createElement("a"); a.href = url; a.download = name; a.click(); window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  };
  const loading = overview.isLoading || overview.isFetching || activity.isLoading || activity.isFetching;
  const kpis = [["Users", counts?.users], ["Suspended users", counts?.suspendedUsers], ["Ride requests", counts?.rideRequests], ["Active rides", counts?.activeRides], ["Driver reviews", counts?.pendingDriverReviews], ["Payment issues", counts?.paymentIssues], ["Active support", counts?.activeSupportCases], ["Urgent / unassigned", `${counts?.urgentSupportCases ?? "—"} / ${counts?.unassignedSupportCases ?? "—"}`]] as const;
  const lastRefreshed = overview.dataUpdatedAt ? date(overview.dataUpdatedAt) : null;
  return <PageFrame eyebrow="Operations / live view" title="Good day, team." subtitle="A clear read on what needs attention across Vhaya. All figures are supplied by the live operations API.">
    <div className="dash-toolbar">
      <span className="live"><i/> Live operations</span><span className="refresh-stamp">Last refreshed {lastRefreshed ?? "—"}</span>
      <button className="button" onClick={() => { void overview.refetch(); void activity.refetch(); }} disabled={loading} data-testid="button-dashboard-refresh"><RefreshCw size={13}/> Refresh</button>
      <div className="relative"><button className="button" onClick={() => setMenu(!menu)} data-testid="button-dashboard-refresh-menu"><ChevronDown size={13}/> Auto-refresh {auto ? "on" : "off"}</button>
        {menu && <div className="refresh-menu"><button onClick={() => { setAuto(!auto); setMenu(false); }} data-testid="button-toggle-auto-refresh">{auto ? "Turn auto-refresh off" : "Enable every 5 minutes"}</button><small>Automatic refresh never runs more often than every 5 minutes.</small></div>}</div>
      <button className="button" onClick={() => window.print()} aria-label="Print to PDF" data-testid="button-print-dashboard"><Printer size={14}/></button>
      <button className="button" aria-label="Toggle dark mode" onClick={() => { const next = !dark; setDark(next); localStorage.setItem("vhaya-dark", String(next)); }} data-testid="button-toggle-dark-mode">{dark ? <Sun size={14}/> : <Moon size={14}/>}</button>
    </div>
    {(overview.error || activity.error) && <Feedback error>{err(overview.error || activity.error)}</Feedback>}
    <div className="kpis">{kpis.map(([label, value]) => <article className="kpi" key={label} data-testid={`metric-${label.toLowerCase().replaceAll(" ", "-")}`}><div className="kpi-top">{label}<Activity size={14}/></div>{loading ? <div className="skeleton metric-skeleton"/> : <div className="kpi-value">{value ?? "—"}</div>}</article>)}</div>
    <div className="overview-grid dashboard-breakdown">
      <section className="panel chart-panel"><header className="panel-heading"><div><span className="eyebrow">LAST 30 DAYS</span><h2 className="panel-title">Support by category</h2><p className="helper">{overview.data?.support.last30Days ?? "—"} cases received in the last 30 days.</p></div><button className="button small" disabled={!categories.length} onClick={() => exportCsv("support-by-category.csv", categories.map(x => ({ category: x.category, count: x.count })))} data-testid="button-export-categories"><Download size={13}/> CSV</button></header>
        {overview.isLoading || overview.isFetching ? <div className="skeleton chart-skeleton"/> : categories.length ? <div className="chart-container" role="img" aria-label={`Support cases by category, last 30 days: ${categories.map(x => `${x.category} ${x.count}`).join(", ")}`} data-testid="chart-support-category"><ResponsiveContainer width="100%" height="100%"><BarChart data={categories} layout="vertical" margin={{ top: 4, right: 18, bottom: 4, left: 4 }}>
          <CartesianGrid stroke="#e7eee9" strokeDasharray="3 3" horizontal={false}/>
          <XAxis type="number" allowDecimals={false} tick={{ fontSize: 10, fill: "#7d8d84" }} axisLine={false} tickLine={false}/>
          <YAxis type="category" dataKey="category" width={100} tickFormatter={(value: string) => value.replaceAll("_", " ")} tick={{ fontSize: 10, fill: "#5f7469" }} axisLine={false} tickLine={false}/>
          <Tooltip formatter={(value) => [value, "Cases"]} labelFormatter={(value) => String(value).replaceAll("_", " ")}/>
          <Bar dataKey="count" fill="#21816f" radius={[0, 5, 5, 0]} maxBarSize={20}/>
        </BarChart></ResponsiveContainer></div> : <div className="empty">No category totals reported.</div>}
      </section>
      <section className="panel chart-panel"><header className="panel-heading"><div><span className="eyebrow">LAST 30 DAYS</span><h2 className="panel-title">Support by priority</h2><p className="helper">Aggregates reflect the same 30-day reporting window.</p></div><button className="button small" disabled={!priorities.length} onClick={() => exportCsv("support-by-priority.csv", priorities.map(x => ({ priority: x.priority, count: x.count })))} data-testid="button-export-priorities"><Download size={13}/> CSV</button></header>
        {overview.isLoading || overview.isFetching ? <div className="skeleton chart-skeleton"/> : priorities.length ? <div className="chart-container" role="img" aria-label={`Support cases by priority, last 30 days: ${priorities.map(x => `${x.priority} ${x.count}`).join(", ")}`} data-testid="chart-support-priority"><ResponsiveContainer width="100%" height="100%"><BarChart data={priorities} margin={{ top: 8, right: 14, bottom: 8, left: 4 }}>
          <CartesianGrid stroke="#e7eee9" strokeDasharray="3 3" vertical={false}/>
          <XAxis dataKey="priority" tick={{ fontSize: 10, fill: "#5f7469" }} axisLine={false} tickLine={false}/>
          <YAxis allowDecimals={false} tick={{ fontSize: 10, fill: "#7d8d84" }} axisLine={false} tickLine={false}/>
          <Tooltip formatter={(value) => [value, "Cases"]}/>
          <Bar dataKey="count" fill="#c58b45" radius={[5, 5, 0, 0]} maxBarSize={42}/>
        </BarChart></ResponsiveContainer></div> : <div className="empty">No priority totals reported.</div>}
      </section>
    </div>
    <section className="panel activity-panel"><header className="panel-heading"><div><span className="eyebrow">AUDIT TRAIL</span><h2 className="panel-title">Recent activity</h2></div><span className="tag">{activity.data?.length ?? 0} records</span></header>
      <QueryState loading={activity.isLoading} error={activity.error} empty={!activity.isLoading && !activity.error && !activity.data?.length} retry={() => void activity.refetch()}/>
      {!!activity.data?.length && <div className="activity-list">{activity.data.map((item: AdminActivity) => <div className="activity-row" key={item.id} data-testid={`row-activity-${item.id}`}><time>{date(item.createdAt)}</time><div><strong>{item.summary}</strong><span>{item.actor} · {item.subject}</span></div></div>)}</div>}
    </section>
  </PageFrame>;
}

export function RidesPage() {
  const [q, setQ] = useState(""); const [status, setStatus] = useState<GetAdminRidesStatus | "all">("all"); const [page, setPage] = useState(0);
  const query = useRides({ ...(q.trim().length >= 2 ? { q: q.trim() } : {}), ...(status !== "all" ? { status } : {}), page, limit: 25 });
  const cancel = useCancelRide(); const [selected, setSelected] = useState<AdminRide | null>(null); const [message, setMessage] = useState(""); const [messageError, setMessageError] = useState(false);
  const doCancel = async () => { if (!selected) return; try { await cancel.mutateAsync({ rideId: selected.id }); setMessage(`Ride ${selected.id} cancelled.`); setMessageError(false); setSelected(null); } catch (e) { setMessage(err(e)); setMessageError(true); } };
  const rows = query.data?.rides ?? [];
  const statuses: Array<GetAdminRidesStatus | "all"> = ["all","requested","countered","accepted","arrived","in_progress","completed","cancelled","unfulfilled"];
  return <PageFrame eyebrow="Operations / trips" title="Rides" subtitle="Find a trip, check its route and status, and take safe operational action.">
    <div className="toolbar page-toolbar"><label className="input-with-icon"><Search size={14}/><input placeholder="Search by rider, driver or ride ID" value={q} onChange={e => { setQ(e.target.value); setPage(0); }} data-testid="input-rides-search"/></label>
      <select className="select" value={status} onChange={e => { setStatus(e.target.value as typeof status); setPage(0); }} aria-label="Filter ride status" data-testid="select-ride-status">{statuses.map(s => <option key={s} value={s}>{s === "all" ? "All statuses" : s.replaceAll("_", " ")}</option>)}</select>
      {q.trim().length === 1 && <span className="helper">Enter at least 2 characters to search.</span>}</div>
    <QueryState loading={query.isLoading} error={query.error} empty={!query.isLoading && !query.error && !rows.length} retry={() => void query.refetch()}/>
    {!query.isLoading && !query.error && !!rows.length && <><Table headers={["Ride", "Rider", "Driver", "Route", "Status", "Fare", "Created", ""]}>
      {rows.map(r => <tr key={r.id} data-testid={`row-ride-${r.id}`}><td><strong className="primary-text">{r.id}</strong><div className="secondary-text">{r.category}</div></td><td>{r.riderName}<div className="secondary-text">{r.riderEmail ?? r.riderId}</div></td><td>{r.driverName ?? "Unassigned"}<div className="secondary-text">{r.driverEmail ?? ""}</div></td><td>{r.pickupLabel}<div className="secondary-text">to {r.destinationLabel}</div></td><td><Tag>{r.status}</Tag></td><td>{money(r.fareMinor, r.currency)}</td><td>{date(r.createdAt)}</td><td><button className="button small danger" disabled={terminalRide(r.status)} onClick={() => setSelected(r)} data-testid={`button-cancel-ride-${r.id}`}>Cancel</button></td></tr>)}
    </Table><div className="pagination"><span>Page {page + 1}</span><button className="button small" disabled={page === 0} onClick={() => setPage(page - 1)} data-testid="button-rides-prev"><ChevronLeft size={14}/>Previous</button><button className="button small" disabled={!query.data?.hasMore} onClick={() => setPage(page + 1)} data-testid="button-rides-next">Next<ChevronRight size={14}/></button></div></>}
     {message && <Feedback error={messageError}>{message}</Feedback>}
    {selected && <Confirm title="Cancel this ride?" message={`This will cancel ride ${selected.id} in the operations system. Only non-terminal rides can be cancelled.`} confirm={doCancel} confirmLabel="Confirm cancellation" danger onClose={() => setSelected(null)} pending={cancel.isPending}/>}
  </PageFrame>;
}

export function PeoplePage() {
  const [q, setQ] = useState(""); const [type, setType] = useState<"all"|"requesters"|"drivers">("all"); const [page, setPage] = useState(0);
  const query = usePeople({ ...(q.trim().length >= 2 ? { q: q.trim() } : {}), type, page, limit: 25 });
  const [userId, setUserId] = useState(""); const detail = usePerson(userId);
  const role = useUpdatePersonRole(), suspend = useSuspendPerson(), unsuspend = useUnsuspendPerson();
  const [action, setAction] = useState<{kind:"role"|"suspend"|"unsuspend"; person:Person; role?:string; reason?:string}|null>(null);
  const [reason, setReason] = useState(""); const [toast, setToast] = useState(""); const [toastError, setToastError] = useState(false);
  const people = query.data?.people ?? [];
  const apply = async () => { if (!action) return; try {
    if (action.kind === "role") await role.mutateAsync({userId:action.person.userId, role:action.role as "rider"|"driver"|"admin"});
    if (action.kind === "suspend") await suspend.mutateAsync({userId:action.person.userId, reason:reason.trim()});
    if (action.kind === "unsuspend") await unsuspend.mutateAsync({userId:action.person.userId});
    setToast("Account update recorded."); setToastError(false); setAction(null); setReason("");
  } catch (e) { setToast(err(e)); setToastError(true); } };
  return <PageFrame eyebrow="Operations / accounts" title="People" subtitle="Riders, drivers and staff accounts. Account changes are confirmed before submission.">
    <div className="toolbar page-toolbar"><label className="input-with-icon"><Search size={14}/><input placeholder="Search name, email or user ID" value={q} onChange={e=>{setQ(e.target.value);setPage(0)}} data-testid="input-people-search"/></label>
      <select className="select" value={type} onChange={e=>{setType(e.target.value as typeof type);setPage(0)}} aria-label="Filter people" data-testid="select-people-type"><option value="all">All people</option><option value="requesters">Riders</option><option value="drivers">Drivers</option></select>
      {q.trim().length===1 && <span className="helper">Enter at least 2 characters to search.</span>}</div>
     {toast && <Feedback error={toastError}>{toast}</Feedback>}
    <QueryState loading={query.isLoading} error={query.error} empty={!query.isLoading&&!query.error&&!people.length} retry={()=>void query.refetch()}/>
    {!!people.length && <Table headers={["Person", "Role", "Rides", "Last ride", "Account", "Role change", "Actions"]}>
      {people.map(p=><tr key={p.userId} data-testid={`row-person-${p.userId}`}><td><button className="text-link" onClick={()=>setUserId(p.userId)} data-testid={`button-person-details-${p.userId}`}>{p.displayName}</button><div className="secondary-text">{p.email || p.userId}</div></td><td><Tag>{p.role}</Tag></td><td>{p.completedRideCount} / {p.rideCount} completed</td><td>{date(p.lastRideAt)}</td><td>{p.isSuspended?<Tag>suspended</Tag>:<Tag>active</Tag>}</td>
      <td><select className="select compact" aria-label={`Change ${p.displayName} role`} value={p.role} onChange={e=>setAction({kind:"role",person:p,role:e.target.value})} data-testid={`select-role-${p.userId}`}><option value="rider">Rider</option><option value="driver">Driver</option><option value="admin">Admin</option></select></td>
      <td>{p.isSuspended?<button className="button small" onClick={()=>setAction({kind:"unsuspend",person:p})} data-testid={`button-unsuspend-${p.userId}`}>Unsuspend</button>:<button className="button small danger" onClick={()=>{setReason("");setAction({kind:"suspend",person:p})}} data-testid={`button-suspend-${p.userId}`}>Suspend</button>}</td></tr>)}
    </Table>}
    {!!people.length && <div className="pagination"><span>Page {page+1}</span><button className="button small" disabled={!page} onClick={()=>setPage(page-1)} data-testid="button-people-prev">Previous</button><button className="button small" disabled={!query.data?.hasMore} onClick={()=>setPage(page+1)} data-testid="button-people-next">Next</button></div>}
    {userId && <div className="modal-backdrop" role="presentation"><section className="modal person-modal" role="dialog" aria-modal="true"><button className="modal-close" onClick={()=>setUserId("")} aria-label="Close details" data-testid="button-close-person"><X size={16}/></button><span className="eyebrow">ACCOUNT HISTORY</span><h2>{detail.data?.person.displayName ?? "Person details"}</h2>
      <QueryState loading={detail.isLoading} error={detail.error} retry={()=>void detail.refetch()}/>
      {detail.data && <><p>{detail.data.person.email} · {detail.data.person.role}</p><div className="detail-section"><h3>Recent rides</h3>{detail.data.rides.length?detail.data.rides.slice(0,8).map(r=><div className="detail-row" key={r.id}><span>{r.pickupLabel} → {r.destinationLabel}</span><Tag>{r.status}</Tag></div>):<p>No ride history.</p>}</div><div className="detail-section"><h3>Review history</h3>{detail.data.reviews.length?detail.data.reviews.map((r,i)=><div className="detail-row" key={`${r.createdAt}-${i}`}><span>{r.outcome} · {r.actor}</span><time>{date(r.createdAt)}</time></div>):<p>No reviews recorded.</p>}</div></>}
      </section></div>}
    {action && <Confirm title={action.kind==="role"?"Change account role?":action.kind==="suspend"?"Suspend this account?":"Restore account access?"}
      message={action.kind==="role"?`Change ${action.person.displayName} to ${action.role}? This affects their account permissions.`:action.kind==="suspend"?`Suspend ${action.person.displayName}? They will no longer be able to use the service.`:`Unsuspend ${action.person.displayName} and restore account access?`}
       confirm={apply} confirmLabel={action.kind==="role"?"Confirm role change":action.kind==="suspend"?"Confirm suspension":"Restore access"} onClose={()=>setAction(null)} pending={role.isPending||suspend.isPending||unsuspend.isPending} danger={action.kind==="suspend"} disabled={action.kind==="suspend"&&!reason.trim()}>
       {action.kind==="suspend" && <label className="form-label">Reason for suspension<textarea className="textarea" value={reason} onChange={e=>setReason(e.target.value)} maxLength={500} required data-testid="input-suspension-reason"/></label>}
    </Confirm>}
  </PageFrame>;
}

const docTypes = ["identity", "driver_license", "vehicle_registration", "operating_permit"] as const;
export function CompliancePage() {
  const query = useDriverCompliance(); const update = useUpdateCompliance();
  const [feedback, setFeedback] = useState(""); const [feedbackError, setFeedbackError] = useState(false);
  const records = query.data ?? [];
  const save = async (userId: string, documentType: typeof docTypes[number], status: DriverComplianceStatus, expiresOn: string, note: string) => {
    if (documentType === "driver_license" && status === "verified" && (!expiresOn || new Date(`${expiresOn}T23:59:59`).getTime() < Date.now())) {
      setFeedback("A verified driver licence needs a current expiry date."); setFeedbackError(true); return;
    }
    if (status === "not_required" && !note.trim()) {
      setFeedback("Add a staff note before marking a document not required."); setFeedbackError(true); return;
    }
    try { await update.mutateAsync({ userId, input: { documentType, status, expiresOn: expiresOn || null, note } }); setFeedback("Document check saved."); setFeedbackError(false); }
    catch (e) { setFeedback(err(e)); setFeedbackError(true); }
  };
  return <PageFrame eyebrow="Operations / driver standards" title="Driver compliance" subtitle="Review document checks and record expiry dates. Licence verification requires a current expiry.">
    {feedback && <Feedback error={feedbackError}>{feedback}</Feedback>}
    <QueryState loading={query.isLoading} error={query.error} empty={!query.isLoading&&!query.error&&!records.length} retry={()=>void query.refetch()}/>
    <div className="compliance-records">{records.map(record => <section className="panel compliance-card" key={record.userId} data-testid={`compliance-driver-${record.userId}`}>
      <header className="panel-heading"><div><h2 className="panel-title">{record.displayName}</h2><p className="helper">{record.email} · ID {record.userId}</p></div><span className="tag">{record.incompleteDocuments?.length ?? 0} outstanding</span></header>
      <div className="compliance-documents">{docTypes.map(type => {
        const found = (record.documents ?? []).find((item: Record<string, unknown>) => item.documentType === type) as Record<string, unknown> | undefined;
        const status = String(found?.status ?? "not_reviewed") as DriverComplianceStatus; const expires = String(found?.expiresOn ?? ""); const note = String(found?.staffNote ?? "");
        return <ComplianceRow key={type} userId={record.userId} type={type} status={status} expires={expires} note={note} onSave={save} pending={update.isPending}/>;
      })}</div>
    </section>)}</div>
  </PageFrame>;
}
function ComplianceRow({ userId, type, status, expires, note, onSave, pending }: {
  userId: string; type: typeof docTypes[number]; status: DriverComplianceStatus; expires: string; note: string;
  onSave: (userId: string, type: typeof docTypes[number], status: DriverComplianceStatus, expires: string, note: string) => void; pending: boolean;
}) {
  const [s, setS] = useState<DriverComplianceStatus>(status); const [expiry, setExpiry] = useState(expires); const [memo, setMemo] = useState(note);
  useEffect(() => { setS(status); setExpiry(expires); setMemo(note); }, [status, expires, note]);
  return <div className="compliance-row"><div><strong>{type.replaceAll("_", " ")}</strong><span className="secondary-text">{type === "driver_license" ? "Expiry required when verified" : "Expiry is optional"}</span></div>
    <select className="select compact" aria-label={`${type} status`} value={s} onChange={e=>setS(e.target.value as DriverComplianceStatus)} data-testid={`select-compliance-${userId}-${type}`}>
      {["not_reviewed","verified","needs_follow_up","not_required"].map(v=><option key={v} value={v}>{v.replaceAll("_"," ")}</option>)}</select>
    <label className="form-label">Expiry<input type="date" className="field" value={expiry} onChange={e=>setExpiry(e.target.value)} data-testid={`input-expiry-${userId}-${type}`}/></label>
    <label className="form-label">Staff note<input className="field" value={memo} maxLength={500} onChange={e=>setMemo(e.target.value)} data-testid={`input-compliance-note-${userId}-${type}`}/></label>
    <button className="button small primary" disabled={pending} onClick={()=>onSave(userId,type,s,expiry,memo)} data-testid={`button-save-compliance-${userId}-${type}`}>Save check</button>
  </div>;
}

const supportStatuses = ["open","in_progress","resolved"] as const;
const supportPriorities = ["normal","high","urgent"] as const;
const supportCategories = ["safety","payment","driver","rider","lost_item","fare_review","other"] as const;
export function SupportPage() {
  const query = useSupportCases(); const update = useUpdateSupportCase(); const replyMutation = useReplyToSupportCase();
  const [selectedId, setSelectedId] = useState(""); const [statusFilter, setStatusFilter] = useState("all"); const [reply, setReply] = useState(""); const [note, setNote] = useState("");
  const [feedback, setFeedback] = useState(""); const [feedbackError, setFeedbackError] = useState(false);
  const cases = query.data ?? []; const visible = cases.filter(c => statusFilter === "all" || c.status === statusFilter);
  const selected = visible.find(c=>c.id===selectedId) ?? visible[0] ?? null;
  const save = async (input: Parameters<typeof update.mutateAsync>[0]["input"]) => {
    if (!selected) return;
    try { await update.mutateAsync({caseId:selected.id,input}); setFeedback("Case updated."); setFeedbackError(false); }
    catch (e) { setFeedback(err(e)); setFeedbackError(true); }
  };
  const sendReply = async () => { if(!selected || !reply.trim()) return; try { await replyMutation.mutateAsync({caseId:selected.id,message:reply.trim()}); setReply(""); setFeedback("Reply sent."); setFeedbackError(false); } catch(e) {setFeedback(err(e)); setFeedbackError(true);} };
  return <PageFrame eyebrow="Operations / service desk" title="Support cases" subtitle="Triage incoming reports, keep ownership clear and maintain a useful response history.">
     {feedback && <Feedback error={feedbackError}>{feedback}</Feedback>}
    <QueryState loading={query.isLoading} error={query.error} retry={()=>void query.refetch()}/>
    {!query.isLoading&&!query.error&&<section className="support-workspace">
      <aside className="support-queue"><div className="toolbar"><select className="select" value={statusFilter} onChange={e=>setStatusFilter(e.target.value)} aria-label="Filter support cases" data-testid="select-support-status"><option value="all">All statuses</option>{supportStatuses.map(x=><option key={x} value={x}>{x.replaceAll("_"," ")}</option>)}</select><span className="tag">{visible.length}</span></div>
        <div className="support-case-list">{visible.map(c=><button className={`support-case-item ${selected?.id===c.id?"selected":""}`} key={c.id} onClick={()=>{setSelectedId(c.id);setNote(c.staffNote??"");setFeedback("")}} data-testid={`button-support-case-${c.id}`}>
          <span className="case-line"><strong>{c.reporter.displayName ?? c.reporter.email ?? "Unidentified reporter"}</strong><Tag>{c.priority}</Tag></span><span className="case-subject">{c.category.replaceAll("_"," ")} · {c.rideSummary?.id ?? "No linked ride"}</span><span className="case-preview">{c.originalMessage}</span><span className="case-meta"><Tag>{c.status}</Tag><span>{date(c.createdAt)}</span></span>
        </button>)}</div>{!visible.length&&<div className="empty">No cases in this queue.</div>}</aside>
      {selected?<article className="support-detail" key={selected.id}><header className="panel-heading"><div><span className="eyebrow">CASE {selected.id}</span><h2 className="panel-title">{selected.reporter.displayName ?? "Support request"}</h2><p className="helper">{selected.reporter.email ?? "No reporter email"} · {date(selected.createdAt)}</p></div><Tag>{selected.status}</Tag></header>
        <div className="original-message"><span className="eyebrow">ORIGINAL MESSAGE</span><p>{selected.originalMessage}</p></div>
        {selected.rideSummary&&<div className="ride-card"><strong>Linked ride · {selected.rideSummary.id}</strong><p>{selected.rideSummary.pickupLabel} → {selected.rideSummary.destinationLabel}</p><span>{selected.rideSummary.status} · {selected.rideSummary.fareMinor !== undefined ? money(selected.rideSummary.fareMinor, selected.rideSummary.currency) : ""}</span></div>}
        <div className="support-edit-grid">
          <label className="form-label">Status<select className="select" value={selected.status} onChange={e=>void save({status:e.target.value as never})} data-testid="select-case-status">{supportStatuses.map(x=><option key={x} value={x}>{x.replaceAll("_"," ")}</option>)}</select></label>
          <label className="form-label">Category<select className="select" value={selected.category} onChange={e=>void save({category:e.target.value as never})} data-testid="select-case-category">{supportCategories.map(x=><option key={x} value={x}>{x.replaceAll("_"," ")}</option>)}</select></label>
          <label className="form-label">Priority<select className="select" value={selected.priority} onChange={e=>void save({priority:e.target.value as never})} data-testid="select-case-priority">{supportPriorities.map(x=><option key={x} value={x}>{x}</option>)}</select></label>
          <label className="form-label">Assignment<select className="select" value={selected.assignedTo?"me":"unassigned"} onChange={e=>void save({assignedTo:e.target.value==="me"?"me":null})} data-testid="select-case-assignment"><option value="unassigned">Unassigned</option><option value="me">Assign to me</option></select></label>
        </div>
        <label className="form-label">Staff note<textarea className="textarea" rows={3} value={note} onChange={e=>setNote(e.target.value)} data-testid="input-case-note"/></label>
        <button className="button" onClick={()=>void save({staffNote:note.trim()||null})} disabled={update.isPending} data-testid="button-save-case-note">Save staff note</button>
        <div className="reply-thread"><h3>Replies</h3>{selected.replies.map(r=><div className={`reply-row ${r.channel==="staff"?"staff":""}`} key={r.id}><p>{r.message}</p><span>{r.sender} · {date(r.createdAt)}</span></div>)}{!selected.replies.length&&<p className="helper">No replies recorded.</p>}</div>
        <div className="reply-composer"><label className="form-label">Send a reply<textarea className="textarea" rows={3} value={reply} onChange={e=>setReply(e.target.value)} maxLength={3000} data-testid="input-support-reply"/></label><button className="button primary" onClick={()=>void sendReply()} disabled={!reply.trim()||replyMutation.isPending} data-testid="button-send-support-reply">{replyMutation.isPending?"Sending…":"Send reply"}</button></div>
      </article>:<div className="support-detail empty">Select a case to review its history and update it.</div>}
    </section>}
  </PageFrame>;
}

type PayoutProvider = "paystack" | "bank_transfer" | "cash" | "other";
type PayoutForm = { driverId: string; provider: PayoutProvider; reference: string; amount: string; currency: string; note: string };
const emptyPayoutForm = (): PayoutForm => ({ driverId: "", provider: "bank_transfer", reference: "", amount: "", currency: "ZAR", note: "" });
type FinanceTab = "payments" | "wallets" | "payouts" | "batches" | "recipients";
type FinanceDialog = "refund" | "record" | "weekly" | "external-paid" | null;
const periodDate = (value: number) =>
  new Intl.DateTimeFormat("en-ZA", { dateStyle: "medium" }).format(new Date(value));

export function FinancePage() {
  const paymentsQ = usePayments();
  const walletsQ = useWallets();
  const payoutsQ = usePayouts();
  const batchesQ = usePayoutBatches();
  const recipientsQ = usePayoutRecipients();
  const refund = useRequestRefund();
  const createPayout = useCreatePayout();
  const reconcile = useReconcilePayout();
  const run = useRunWeeklyPayouts();
  const approve = useApprovePayoutBatch();
  const saveRecipient = useSavePayoutRecipient();
  const startTransfers = useStartPayoutBatchTransfers();
  const verifyTransfer = useVerifyPayoutTransfer();
  const recordExternalPayment = useRecordExternalPayoutBatchPayment();

  const [tab, setTab] = useState<FinanceTab>("payments");
  const [dialog, setDialog] = useState<FinanceDialog>(null);
  const [payment, setPayment] = useState<Payment | null>(null);
  const [payout, setPayout] = useState<Payout | null>(null);
  const [selectedBatch, setSelectedBatch] = useState<PayoutBatch | null>(null);
  const [externalBatch, setExternalBatch] = useState<PayoutBatch | null>(null);
  const [approvalBatch, setApprovalBatch] = useState<PayoutBatch | null>(null);
  const [recipientDriver, setRecipientDriver] = useState<PayoutDriverRecipient | null>(null);
  const [recipientName, setRecipientName] = useState("");
  const [accountNumber, setAccountNumber] = useState("");
  const [bankCode, setBankCode] = useState("");
  const [reason, setReason] = useState("");
  const [form, setForm] = useState<PayoutForm>(emptyPayoutForm);
  const [range, setRange] = useState({ start: "", end: "" });
  const [externalConfirmed, setExternalConfirmed] = useState(false);
  const [transferConfirmed, setTransferConfirmed] = useState(false);
  const [retryConfirmed, setRetryConfirmed] = useState(false);
  const [feedback, setFeedback] = useState("");
  const [feedbackError, setFeedbackError] = useState(false);
  const [reconciliationStatus, setReconciliationStatus] =
    useState<"matched" | "exception">("matched");
  const [reconciliationNote, setReconciliationNote] = useState("");

  const banksQ = usePayoutBanks(Boolean(recipientDriver));
  const batchDetailQ = usePayoutBatch(selectedBatch?.id ?? "", Boolean(selectedBatch));
  const payments = paymentsQ.data ?? [];
  const wallets = walletsQ.data ?? [];
  const payouts = payoutsQ.data ?? [];
  const batches = batchesQ.data ?? [];
  const payoutDrivers = recipientsQ.data ?? [];
  const selectedQuery =
    tab === "payments"
      ? paymentsQ
      : tab === "wallets"
        ? walletsQ
        : tab === "payouts"
          ? payoutsQ
          : tab === "batches"
            ? batchesQ
            : recipientsQ;
  const actionError = (error: unknown) => {
    setFeedback(err(error));
    setFeedbackError(true);
  };
  const setSuccess = (message: string) => {
    setFeedback(message);
    setFeedbackError(false);
  };

  const confirmDialog = async () => {
    try {
      if (dialog === "refund" && payment) {
        await refund.mutateAsync({ paymentId: payment.id, reason: reason.trim() });
        setSuccess("Refund request recorded. This does not issue a Paystack refund.");
      }
      if (
        dialog === "record" &&
        externalConfirmed &&
        form.driverId.trim() &&
        form.reference.trim() &&
        Number(form.amount) > 0
      ) {
        await createPayout.mutateAsync({
          driverId: form.driverId.trim(),
          provider: form.provider,
          externalReference: form.reference.trim(),
          amountMinor: Math.round(Number(form.amount) * 100),
          currency: form.currency,
          note: form.note,
          confirmedExternalTransfer: true,
        });
        setSuccess("Externally completed payout recorded.");
      }
      if (dialog === "weekly" && range.start && range.end) {
        await run.mutateAsync({ periodStart: range.start, periodEnd: range.end });
        setSuccess("Weekly batch calculated. No money has been transferred.");
        setTab("batches");
      }
      setDialog(null);
      setExternalConfirmed(false);
    } catch (error) {
      actionError(error);
    }
  };

  const confirmApproval = async () => {
    if (!approvalBatch) return;
    try {
      await approve.mutateAsync({ batchId: approvalBatch.id });
      setSuccess("Batch approved. Review the masked bank details and confirm separately before sending.");
      setApprovalBatch(null);
    } catch (error) {
      actionError(error);
    }
  };

  const savePayoutRecipient = async () => {
    if (!recipientDriver || !recipientName.trim() || !accountNumber.trim() || !bankCode) return;
    try {
      await saveRecipient.mutateAsync({
        driverId: recipientDriver.driverId,
        name: recipientName.trim(),
        accountNumber: accountNumber.trim(),
        bankCode,
      });
      setSuccess(`Paystack bank recipient verified for ${recipientDriver.driverName}.`);
      setRecipientDriver(null);
      setAccountNumber("");
      setBankCode("");
    } catch (error) {
      actionError(error);
    }
  };

  const submitTransfers = async (retryFailed: boolean) => {
    if (!selectedBatch || !batchDetailQ.data?.reviewToken) return;
    try {
      await startTransfers.mutateAsync({
        batchId: selectedBatch.id,
        retryFailed,
        reviewToken: batchDetailQ.data.reviewToken,
      });
      setSuccess("Paystack transfer results were refreshed. Check each driver’s status before closing this review.");
      setTransferConfirmed(false);
      setRetryConfirmed(false);
    } catch (error) {
      actionError(error);
    }
  };

  const verifyPayout = async (payoutId: string) => {
    try {
      await verifyTransfer.mutateAsync({ payoutId });
      setSuccess("Paystack status checked and saved.");
    } catch (error) {
      actionError(error);
    }
  };

  const saveExternalPayment = async () => {
    if (!externalBatch || !externalConfirmed) return;
    try {
      await recordExternalPayment.mutateAsync({ batchId: externalBatch.id });
      setSuccess("External payment recorded. Paystack transfers were not started.");
      setExternalBatch(null);
      setExternalConfirmed(false);
    } catch (error) {
      actionError(error);
    }
  };

  const saveReconciliation = async () => {
    if (!payout) return;
    try {
      await reconcile.mutateAsync({
        payoutId: payout.id,
        reconciliationStatus,
        note: reconciliationNote.trim() || undefined,
      });
      setSuccess("Payout reconciliation saved.");
      setPayout(null);
    } catch (error) {
      actionError(error);
    }
  };

  const openRecipient = (driver: PayoutDriverRecipient) => {
    setRecipientDriver(driver);
    setRecipientName(driver.recipient?.recipientName ?? driver.driverName);
    setAccountNumber("");
    setBankCode("");
  };
  const reviewDrivers = batchDetailQ.data?.drivers ?? [];
  const missingRecipient = reviewDrivers.some(
    (driver) =>
      !driver.recipientName ||
      !driver.bankName ||
      !driver.accountLast4 ||
      driver.currency !== "ZAR",
  );
  const retryCount = reviewDrivers.filter((driver) => driver.transferStatus === "failed").length;
  const newTransferCount = reviewDrivers.filter(
    (driver) => driver.transferStatus === "not_started",
  ).length;

  return <PageFrame
    eyebrow="Operations / money movement"
    title="Finance"
    subtitle="Review driver earnings, verify payout accounts, and track every disbursement."
  >
    <div className="finance-notice">
      <CircleDollarSign size={17}/>
      <span>
        <strong>Paystack transfers require two separate confirmations.</strong>
        {" "}Staff approve a batch first, then review every amount and masked bank account before sending.
        Live transfers stay disabled until explicitly configured.
      </span>
    </div>
    {feedback && <Feedback error={feedbackError}>{feedback}</Feedback>}
    <div className="finance-tabs" role="tablist" aria-label="Finance sections">
      {(["payments", "wallets", "payouts", "batches", "recipients"] as const).map((item) =>
        <button
          role="tab"
          aria-selected={tab === item}
          className={tab === item ? "active" : ""}
          key={item}
          onClick={() => setTab(item)}
          data-testid={`tab-finance-${item}`}
        >{item}</button>,
      )}
    </div>
    <QueryState
      loading={selectedQuery.isLoading}
      error={selectedQuery.error}
      retry={() => void selectedQuery.refetch()}
    />

    {tab === "payments" && !paymentsQ.isLoading && !paymentsQ.error &&
      <Table headers={["Payment", "Rider", "Provider / method", "Amount", "Captured", "Status", "Paid", "Action"]}>
        {payments.map((item) =>
          <tr key={item.id} data-testid={`row-payment-${item.id}`}>
            <td><strong className="primary-text">{item.reference}</strong><div className="secondary-text">Trip {item.tripId}</div></td>
            <td>{item.riderName}<div className="secondary-text">{item.riderEmail}</div></td>
            <td>{item.provider}<div className="secondary-text">{item.paymentMethod}</div></td>
            <td>{money(item.amountMinor, item.currency)}</td>
            <td>{money(item.capturedAmountMinor, item.currency)}</td>
            <td><Tag>{item.status}</Tag></td>
            <td>{date(item.paidAt)}</td>
            <td><button className="button small" onClick={() => { setPayment(item); setReason(""); setDialog("refund"); }} data-testid={`button-request-refund-${item.id}`}>Request refund</button></td>
          </tr>,
        )}
      </Table>}

    {tab === "wallets" && !walletsQ.isLoading && !walletsQ.error &&
      <Table headers={["Owner", "Role", "Balance", "Wallet updated", "Account"]}>
        {wallets.map((wallet) =>
          <tr key={wallet.walletId} data-testid={`row-wallet-${wallet.walletId}`}>
            <td>{wallet.displayName}<div className="secondary-text">{wallet.email}</div></td>
            <td><Tag>{wallet.role}</Tag></td>
            <td>{money(wallet.balanceMinor, wallet.currency)}</td>
            <td>{date(wallet.updatedAt)}</td>
            <td className="mono">{wallet.walletId}</td>
          </tr>,
        )}
      </Table>}

    {tab === "recipients" && !recipientsQ.isLoading && !recipientsQ.error &&
      <div className="finance-section">
        <div className="finance-section-head">
          <div>
            <h2>Driver payout accounts</h2>
            <p>Paystack verifies each bank account. Vhaya stores only the recipient reference and masked details.</p>
          </div>
          <span className="finance-count">{payoutDrivers.length} active drivers</span>
        </div>
        <Table headers={["Driver", "Verified account", "Recipient name", "Last verified", "Action"]}>
          {payoutDrivers.map((driver) =>
            <tr key={driver.driverId} data-testid={`row-payout-recipient-${driver.driverId}`}>
              <td><strong className="primary-text">{driver.driverName}</strong><div className="secondary-text mono">{driver.driverId}</div></td>
              <td>{driver.recipient ? <>{driver.recipient.bankName}<div className="secondary-text">•••• {driver.recipient.accountLast4}</div></> : <Tag>Not set</Tag>}</td>
              <td>{driver.recipient?.recipientName ?? "—"}</td>
              <td>{driver.recipient ? date(driver.recipient.updatedAt) : "—"}</td>
              <td><button className="button small" onClick={() => openRecipient(driver)} data-testid={`button-edit-payout-recipient-${driver.driverId}`}>{driver.recipient ? "Replace account" : "Add account"}</button></td>
            </tr>,
          )}
        </Table>
      </div>}

    {tab === "payouts" && !payoutsQ.isLoading && !payoutsQ.error &&
      <>
        <div className="toolbar page-toolbar">
          <button className="button primary" onClick={() => { setForm(emptyPayoutForm()); setDialog("record"); }} data-testid="button-record-payout">Record completed transfer</button>
          <button className="button" onClick={() => { setRange({ start: "", end: "" }); setDialog("weekly"); }} data-testid="button-run-weekly-payouts">Calculate weekly batch</button>
        </div>
        <Table headers={["Driver", "Provider / reference", "Amount", "Transfer status", "Reconciliation", "Note", "Action"]}>
          {payouts.map((item) =>
            <tr key={item.id} data-testid={`row-payout-${item.id}`}>
              <td>{item.driverName ?? item.driverId}</td>
              <td>{item.provider}<div className="secondary-text">{item.externalReference}</div></td>
              <td>{money(item.amountMinor, item.currency)}</td>
              <td><Tag>{item.transferStatus.replaceAll("_", " ")}</Tag>{item.transferError && <div className="secondary-text">{item.transferError}</div>}</td>
              <td><Tag>{item.reconciliationStatus}</Tag></td>
              <td>{item.note ?? "—"}</td>
              <td>
                <div className="toolbar">
                  {item.transferReference && item.transferStatus !== "not_started" &&
                    <button className="button small" disabled={verifyTransfer.isPending} onClick={() => void verifyPayout(String(item.id))} data-testid={`button-verify-payout-${item.id}`}>Check Paystack</button>}
                  <button className="button small" onClick={() => { setPayout(item); setReconciliationStatus(item.reconciliationStatus === "exception" ? "exception" : "matched"); setReconciliationNote(item.note ?? ""); }} data-testid={`button-reconcile-${item.id}`}>Reconcile</button>
                </div>
              </td>
            </tr>,
          )}
        </Table>
      </>}

    {tab === "batches" && !batchesQ.isLoading && !batchesQ.error &&
      <>
        <div className="toolbar page-toolbar">
          <button className="button primary" onClick={() => { setRange({ start: "", end: "" }); setDialog("weekly"); }} data-testid="button-create-batch">Calculate weekly batch</button>
        </div>
        <Table headers={["Batch", "Period", "Drivers", "Total", "Status", "Settlement", "Created by", "Action"]}>
          {batches.map((item) =>
            <tr key={item.id} data-testid={`row-payout-batch-${item.id}`}>
              <td className="mono">{item.id}</td>
              <td>{periodDate(item.periodStart)} – {periodDate(item.periodEnd)}</td>
              <td>{item.driverCount}</td>
              <td>{money(item.totalAmountMinor)}</td>
              <td><Tag>{item.status.replaceAll("_", " ")}</Tag></td>
              <td>{item.settlementMethod ?? "Not sent"}</td>
              <td>{item.createdBy ?? "—"}</td>
              <td>
                <div className="toolbar">
                  <button className="button small" onClick={() => { setSelectedBatch(item); setTransferConfirmed(false); setRetryConfirmed(false); }} data-testid={`button-review-batch-${item.id}`}>Review</button>
                  {item.status === "open" &&
                    <button className="button small" onClick={() => setApprovalBatch(item)} data-testid={`button-approve-batch-${item.id}`}>Approve</button>}
                  {item.status === "submitted" &&
                    <button className="button small" onClick={() => { setExternalBatch(item); setExternalConfirmed(false); }} data-testid={`button-record-external-payment-${item.id}`}>Record external payment</button>}
                </div>
              </td>
            </tr>,
          )}
        </Table>
      </>}

    {!selectedQuery.isLoading && !selectedQuery.error &&
      ((tab === "payments" && !payments.length) ||
        (tab === "wallets" && !wallets.length) ||
        (tab === "payouts" && !payouts.length) ||
        (tab === "batches" && !batches.length) ||
        (tab === "recipients" && !payoutDrivers.length)) &&
      <div className="empty">No {tab} records available.</div>}

    {dialog &&
      <div className="modal-backdrop">
        <section className="modal" role="dialog" aria-modal="true">
          <button className="modal-close" onClick={() => setDialog(null)} aria-label="Close" data-testid="button-close-finance-dialog"><X size={16}/></button>
          {dialog === "refund" &&
            <>
              <h2>Request a refund</h2>
              <p>This records a refund request for {payment?.reference}. It does not issue a Paystack refund.</p>
              <label className="form-label">Reason<textarea className="textarea" value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} data-testid="input-refund-reason"/></label>
              <div className="modal-actions"><button className="button" onClick={() => setDialog(null)}>Cancel</button><button className="button primary" disabled={!reason.trim() || refund.isPending} onClick={() => void confirmDialog()} data-testid="button-confirm-refund">Record request</button></div>
            </>}
          {dialog === "record" &&
            <>
              <h2>Record completed payout</h2>
              <p>Only record a transfer that has already completed outside Vhaya.</p>
              <div className="form-grid">
                <label className="form-label">Driver user ID<input className="field" value={form.driverId} onChange={(event) => setForm({ ...form, driverId: event.target.value })} data-testid="input-payout-driver"/></label>
                <label className="form-label">Provider<select className="select" value={form.provider} onChange={(event) => setForm({ ...form, provider: event.target.value as PayoutProvider })} data-testid="select-payout-provider"><option value="bank_transfer">Bank transfer</option><option value="paystack">Paystack</option><option value="cash">Cash</option><option value="other">Other</option></select></label>
                <label className="form-label">External reference<input className="field" value={form.reference} onChange={(event) => setForm({ ...form, reference: event.target.value })} data-testid="input-payout-reference"/></label>
                <label className="form-label">Amount (ZAR)<input className="field" type="number" min="0.01" step="0.01" value={form.amount} onChange={(event) => setForm({ ...form, amount: event.target.value })} data-testid="input-payout-amount"/></label>
              </div>
              <label className="form-label payout-note-label">Staff note<input className="field" value={form.note} maxLength={500} onChange={(event) => setForm({ ...form, note: event.target.value })} data-testid="input-payout-note"/></label>
              <label className="deletion-check"><input type="checkbox" checked={externalConfirmed} onChange={(event) => setExternalConfirmed(event.target.checked)} data-testid="checkbox-external-transfer"/><span className="deletion-check-box"><Check size={12}/></span><span><strong>I confirm this external transfer is complete.</strong></span></label>
              <div className="modal-actions"><button className="button" onClick={() => setDialog(null)}>Cancel</button><button className="button primary" disabled={!externalConfirmed || !form.driverId.trim() || !form.reference.trim() || Number(form.amount) <= 0 || createPayout.isPending} onClick={() => void confirmDialog()} data-testid="button-confirm-record-payout">Record transfer</button></div>
            </>}
          {dialog === "weekly" &&
            <>
              <h2>Calculate weekly payout batch</h2>
              <p>This calculates eligible payouts and creates a batch. It does not transfer money.</p>
              <div className="form-grid">
                <label className="form-label">Period start<input type="date" className="field" value={range.start} onChange={(event) => setRange({ ...range, start: event.target.value })} data-testid="input-batch-start"/></label>
                <label className="form-label">Period end<input type="date" className="field" value={range.end} onChange={(event) => setRange({ ...range, end: event.target.value })} data-testid="input-batch-end"/></label>
              </div>
              <div className="modal-actions"><button className="button" onClick={() => setDialog(null)}>Cancel</button><button className="button primary" disabled={!range.start || !range.end || range.end < range.start || run.isPending} onClick={() => void confirmDialog()} data-testid="button-confirm-run-weekly">Calculate batch</button></div>
            </>}
        </section>
      </div>}

    {recipientDriver &&
      <div className="modal-backdrop">
        <section className="modal finance-recipient-modal" role="dialog" aria-modal="true" aria-labelledby="payout-recipient-title">
          <button className="modal-close" onClick={() => { setRecipientDriver(null); setAccountNumber(""); }} aria-label="Close payout account form"><X size={16}/></button>
          <div className="modal-mark"><ShieldCheck size={18}/></div>
          <h2 id="payout-recipient-title">{recipientDriver.recipient ? "Replace payout account" : "Verify payout account"}</h2>
          <p>Paystack checks this South African bank account. Vhaya saves only the verified reference and last four digits.</p>
          {recipientDriver.recipient &&
            <div className="finance-recipient-current">
              Current: <strong>{recipientDriver.recipient.bankName}</strong> ···· {recipientDriver.recipient.accountLast4}
            </div>}
          <label className="form-label">Account holder name<input className="field" value={recipientName} onChange={(event) => setRecipientName(event.target.value)} maxLength={120} autoComplete="off" data-testid="input-payout-recipient-name"/></label>
          <label className="form-label">Bank<select className="select" value={bankCode} onChange={(event) => setBankCode(event.target.value)} disabled={banksQ.isLoading || !banksQ.data?.length} data-testid="select-payout-recipient-bank">
            <option value="">{banksQ.isLoading ? "Loading Paystack banks…" : "Choose a bank"}</option>
            {(banksQ.data ?? []).map((bank) => <option key={bank.code} value={bank.code}>{bank.name}</option>)}
          </select></label>
          {banksQ.error &&
            <div className="state-box error finance-bank-error">
              Paystack bank details are unavailable. Add a safe Paystack key in Replit Secrets, then retry.
              <button className="button small" onClick={() => void banksQ.refetch()}>Retry</button>
            </div>}
          <label className="form-label">Bank account number<input className="field" value={accountNumber} onChange={(event) => setAccountNumber(event.target.value.replace(/\D/g, "").slice(0, 30))} inputMode="numeric" autoComplete="off" maxLength={30} data-testid="input-payout-recipient-account"/></label>
          <div className="modal-actions">
            <button className="button" onClick={() => { setRecipientDriver(null); setAccountNumber(""); }}>Cancel</button>
            <button className="button primary" disabled={!recipientName.trim() || !accountNumber.trim() || !bankCode || banksQ.isLoading || saveRecipient.isPending} onClick={() => void savePayoutRecipient()} data-testid="button-save-payout-recipient">{saveRecipient.isPending ? "Verifying…" : "Verify and save"}</button>
          </div>
        </section>
      </div>}

    {selectedBatch &&
      <div className="modal-backdrop">
        <section className="modal finance-review-modal" role="dialog" aria-modal="true" aria-labelledby="payout-review-title">
          <button className="modal-close" onClick={() => { setSelectedBatch(null); setTransferConfirmed(false); setRetryConfirmed(false); }} aria-label="Close payout review"><X size={16}/></button>
          <div className="modal-mark"><ShieldCheck size={18}/></div>
          <h2 id="payout-review-title">Review driver payouts</h2>
          <p>{periodDate(selectedBatch.periodStart)} – {periodDate(selectedBatch.periodEnd)} · {selectedBatch.id}</p>
          <div className="finance-review-summary">
            <span><small>Drivers</small><strong>{batchDetailQ.data?.drivers.length ?? selectedBatch.driverCount}</strong></span>
            <span><small>Total to review</small><strong>{money(selectedBatch.totalAmountMinor)}</strong></span>
            <span><small>Batch status</small><strong>{selectedBatch.status.replaceAll("_", " ")}</strong></span>
          </div>
          {batchDetailQ.isLoading && <div className="skeleton finance-review-skeleton"/>}
          {batchDetailQ.error &&
            <QueryState loading={false} error={batchDetailQ.error} retry={() => void batchDetailQ.refetch()}/>}
          {batchDetailQ.data &&
            <>
              <div className="finance-review-list">
                <Table headers={["Driver / account holder", "Amount", "Verified bank account", "Transfer status", ""]}>
                  {reviewDrivers.map((driver) =>
                    <tr key={driver.payoutId} data-testid={`row-payout-review-${driver.payoutId}`}>
                      <td><strong className="primary-text">{driver.driverName}</strong><div className="secondary-text">{driver.recipientName ?? "Account not set"}</div></td>
                      <td>{money(driver.amountMinor, driver.currency)}</td>
                      <td>{driver.bankName ?? "Not verified"}<div className="secondary-text">{driver.accountLast4 ? `•••• ${driver.accountLast4}` : "No account on file"}</div></td>
                      <td><Tag>{driver.transferStatus.replaceAll("_", " ")}</Tag>{driver.transferError && <div className="secondary-text">{driver.transferError}</div>}</td>
                      <td>{reviewDrivers.length > 0 && driver.transferStatus !== "not_started" &&
                        <button className="button small" disabled={verifyTransfer.isPending} onClick={() => void verifyPayout(String(driver.payoutId))} data-testid={`button-check-review-payout-${driver.payoutId}`}>Check</button>}</td>
                    </tr>,
                  )}
                </Table>
              </div>
              {selectedBatch.status === "open" &&
                <div className="finance-review-note">Approve this batch first. Approval does not send money.</div>}
              {missingRecipient &&
                <div className="finance-review-note error">Add a verified ZAR recipient for every driver before sending.</div>}
              {!missingRecipient && newTransferCount > 0 &&
                <label className="deletion-check finance-transfer-confirm">
                  <input type="checkbox" checked={transferConfirmed} onChange={(event) => setTransferConfirmed(event.target.checked)} data-testid="checkbox-review-payout-batch"/>
                  <span className="deletion-check-box"><Check size={12}/></span>
                  <span><strong>I reviewed every driver, amount, and masked bank account and authorize these Paystack transfers.</strong></span>
                </label>}
              {!missingRecipient && retryCount > 0 &&
                <label className="deletion-check finance-transfer-confirm">
                  <input type="checkbox" checked={retryConfirmed} onChange={(event) => setRetryConfirmed(event.target.checked)} data-testid="checkbox-retry-failed-payouts"/>
                  <span className="deletion-check-box"><Check size={12}/></span>
                  <span><strong>I checked the failed statuses and want to retry using the same transfer references.</strong></span>
                </label>}
              <div className="modal-actions">
                <button className="button" onClick={() => { setSelectedBatch(null); setTransferConfirmed(false); setRetryConfirmed(false); }}>Close review</button>
                {newTransferCount > 0 &&
                  <button className="button primary" disabled={!transferConfirmed || missingRecipient || selectedBatch.status === "open" || startTransfers.isPending} onClick={() => void submitTransfers(false)} data-testid="button-send-payout-batch">{startTransfers.isPending ? "Sending…" : `Send ${newTransferCount} payout${newTransferCount === 1 ? "" : "s"}`}</button>}
                {retryCount > 0 &&
                  <button className="button danger" disabled={!retryConfirmed || missingRecipient || selectedBatch.status === "open" || startTransfers.isPending} onClick={() => void submitTransfers(true)} data-testid="button-retry-failed-payouts">{startTransfers.isPending ? "Working…" : `Retry ${retryCount} failed`}</button>}
              </div>
            </>}
        </section>
      </div>}

    {externalBatch &&
      <div className="modal-backdrop">
        <section className="modal" role="dialog" aria-modal="true" aria-labelledby="external-batch-title">
          <button className="modal-close" onClick={() => { setExternalBatch(null); setExternalConfirmed(false); }} aria-label="Close external payment confirmation"><X size={16}/></button>
          <div className="modal-mark"><ShieldCheck size={18}/></div>
          <h2 id="external-batch-title">Record an external payment?</h2>
          <p>This records batch {externalBatch.id} as paid outside Paystack. It is only allowed before any Paystack transfer has started.</p>
          <label className="deletion-check"><input type="checkbox" checked={externalConfirmed} onChange={(event) => setExternalConfirmed(event.target.checked)} data-testid="checkbox-batch-transfer-confirmed"/><span className="deletion-check-box"><Check size={12}/></span><span><strong>I confirm the entire batch was paid externally.</strong></span></label>
          <div className="modal-actions"><button className="button" onClick={() => { setExternalBatch(null); setExternalConfirmed(false); }}>Go back</button><button className="button primary" disabled={!externalConfirmed || recordExternalPayment.isPending} onClick={() => void saveExternalPayment()} data-testid="button-confirm-batch-paid">{recordExternalPayment.isPending ? "Saving…" : "Record external payment"}</button></div>
        </section>
      </div>}

    {payout &&
      <div className="modal-backdrop">
        <section className="modal" role="dialog" aria-modal="true" aria-labelledby="reconcile-title">
          <button className="modal-close" onClick={() => setPayout(null)} aria-label="Close reconciliation" data-testid="button-close-reconcile"><X size={16}/></button>
          <h2 id="reconcile-title">Reconcile payout</h2>
          <p>{payout.driverName ?? payout.driverId} · {money(payout.amountMinor, payout.currency)} · {payout.externalReference}</p>
          <label className="form-label">Result<select className="select" value={reconciliationStatus} onChange={(event) => setReconciliationStatus(event.target.value as "matched" | "exception")} data-testid="select-reconciliation-result"><option value="matched">Matched</option><option value="exception">Exception</option></select></label>
          <label className="form-label">Reconciliation note<input className="field" value={reconciliationNote} maxLength={500} onChange={(event) => setReconciliationNote(event.target.value)} data-testid="input-reconciliation-note"/></label>
          <div className="modal-actions"><button className="button" onClick={() => setPayout(null)}>Cancel</button><button className="button primary" disabled={reconcile.isPending} onClick={() => void saveReconciliation()} data-testid="button-save-reconciliation">Save reconciliation</button></div>
        </section>
      </div>}

    {dialog === "record" &&
      <div className="modal-backdrop">
        <section className="modal" role="dialog" aria-modal="true" aria-labelledby="record-payout-title">
          <button className="modal-close" onClick={() => setDialog(null)} aria-label="Close payout form" data-testid="button-close-finance-dialog"><X size={16}/></button>
          <h2 id="record-payout-title">Record completed payout</h2>
          <p>Only record a transfer that has already completed outside Vhaya.</p>
          <div className="form-grid">
            <label className="form-label">Driver user ID<input className="field" value={form.driverId} onChange={(event) => setForm({ ...form, driverId: event.target.value })} data-testid="input-payout-driver"/></label>
            <label className="form-label">Provider<select className="select" value={form.provider} onChange={(event) => setForm({ ...form, provider: event.target.value as PayoutProvider })} data-testid="select-payout-provider"><option value="bank_transfer">Bank transfer</option><option value="paystack">Paystack</option><option value="cash">Cash</option><option value="other">Other</option></select></label>
            <label className="form-label">External reference<input className="field" value={form.reference} onChange={(event) => setForm({ ...form, reference: event.target.value })} data-testid="input-payout-reference"/></label>
            <label className="form-label">Amount (ZAR)<input className="field" type="number" min="0.01" step="0.01" value={form.amount} onChange={(event) => setForm({ ...form, amount: event.target.value })} data-testid="input-payout-amount"/></label>
          </div>
          <label className="form-label payout-note-label">Staff note<input className="field" value={form.note} maxLength={500} onChange={(event) => setForm({ ...form, note: event.target.value })} data-testid="input-payout-note"/></label>
          <label className="deletion-check"><input type="checkbox" checked={externalConfirmed} onChange={(event) => setExternalConfirmed(event.target.checked)} data-testid="checkbox-external-transfer"/><span className="deletion-check-box"><Check size={12}/></span><span><strong>I confirm this external transfer is complete.</strong></span></label>
          <div className="modal-actions"><button className="button" onClick={() => setDialog(null)}>Cancel</button><button className="button primary" disabled={!externalConfirmed || !form.driverId.trim() || !form.reference.trim() || Number(form.amount) <= 0 || createPayout.isPending} onClick={() => void confirmDialog()} data-testid="button-confirm-record-payout">Record transfer</button></div>
        </section>
      </div>}

    {dialog === "refund" &&
      <div className="modal-backdrop">
        <section className="modal" role="dialog" aria-modal="true" aria-labelledby="refund-title">
          <button className="modal-close" onClick={() => setDialog(null)} aria-label="Close refund request" data-testid="button-close-finance-dialog"><X size={16}/></button>
          <h2 id="refund-title">Request a refund</h2>
          <p>This records a refund request for {payment?.reference}. It does not issue a Paystack refund.</p>
          <label className="form-label">Reason<textarea className="textarea" value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} data-testid="input-refund-reason"/></label>
          <div className="modal-actions"><button className="button" onClick={() => setDialog(null)}>Cancel</button><button className="button primary" disabled={!reason.trim() || refund.isPending} onClick={() => void confirmDialog()} data-testid="button-confirm-refund">Record request</button></div>
        </section>
      </div>}

    {dialog === "weekly" &&
      <div className="modal-backdrop">
        <section className="modal" role="dialog" aria-modal="true" aria-labelledby="weekly-payout-title">
          <button className="modal-close" onClick={() => setDialog(null)} aria-label="Close weekly batch form" data-testid="button-close-finance-dialog"><X size={16}/></button>
          <h2 id="weekly-payout-title">Calculate weekly payout batch</h2>
          <p>This calculates eligible payouts and creates a batch. It does not transfer money.</p>
          <div className="form-grid">
            <label className="form-label">Period start<input type="date" className="field" value={range.start} onChange={(event) => setRange({ ...range, start: event.target.value })} data-testid="input-batch-start"/></label>
            <label className="form-label">Period end<input type="date" className="field" value={range.end} onChange={(event) => setRange({ ...range, end: event.target.value })} data-testid="input-batch-end"/></label>
          </div>
          <div className="modal-actions"><button className="button" onClick={() => setDialog(null)}>Cancel</button><button className="button primary" disabled={!range.start || !range.end || range.end < range.start || run.isPending} onClick={() => void confirmDialog()} data-testid="button-confirm-run-weekly">Calculate batch</button></div>
        </section>
      </div>}

    {approvalBatch &&
      <Confirm
        title="Approve this payout batch?"
        message={`Batch ${approvalBatch.id} will be prepared for review. Approval does not transfer money.`}
        confirm={confirmApproval}
        confirmLabel="Approve batch"
        onClose={() => setApprovalBatch(null)}
        pending={approve.isPending}
      />}
  </PageFrame>;
}
