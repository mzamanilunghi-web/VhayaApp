import { useMemo, useState, type FormEvent, type ReactNode } from "react";
import { Link } from "wouter";
import { VhayaMark } from "@/components/vhaya-mark";
import {
  AlertCircle, BadgePercent, CalendarDays, Check, ChevronLeft, ChevronRight, CircleDollarSign,
  CreditCard, Edit3, Plus, Search, ShieldCheck, ToggleLeft, ToggleRight, Users, X,
} from "lucide-react";
import {
  useAdminDriverSubscriptions, useCreateDriverPromotion, useCreateDriverSubscriptionPlan,
  useDriverPromotions, useDriverSubscriptionPlans, useUpdateAdminDriverSubscription,
  useUpdateDriverPromotion, useUpdateDriverSubscriptionPlan,
} from "@/hooks/useAdminData";
import type {
  AdminDriverSubscriptionRow, DriverPromotion, DriverPromotionInput, DriverSubscriptionPlan,
  DriverSubscriptionPlanInput, DriverSubscriptionStatus, SubscriptionBillingInterval,
  SubscriptionRenewalMode,
} from "@/lib/vhaya-api";
import { useAuth } from "@/lib/auth-context";

const nav = [
  ["/", "Overview"], ["/rides", "Rides"], ["/people", "People"], ["/driver-reviews", "Driver reviews"],
  ["/driver-compliance", "Compliance"], ["/support", "Support"], ["/finance", "Finance"],
  ["/subscriptions", "Billing"], ["/account-deletions", "Deletions"],
] as const;
const intervals: SubscriptionBillingInterval[] = ["daily", "weekly", "monthly", "quarterly", "biannually", "annually"];
const statuses: DriverSubscriptionStatus[] = ["pending", "active", "expired", "cancelled"];
const money = (minor: number, currency = "ZAR") =>
  new Intl.NumberFormat("en-ZA", { style: "currency", currency }).format(minor / 100);
const formatDate = (value?: string | number | null) =>
  value ? new Intl.DateTimeFormat("en-ZA", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) : "Not recorded";
const dateInput = (value?: number | string | null) => {
  if (!value) return "";
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? "" : `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}-${String(d.getUTCDate()).padStart(2, "0")}`;
};
const isoDate = (value: string) => value ? new Date(`${value}T23:59:59.000Z`).toISOString() : "";
const errText = (error: unknown) => error instanceof Error ? error.message : "The change could not be saved. Try again.";
const pretty = (value: string) => value.replaceAll("_", " ");
const statusTone = (status: string) => status === "active" || status === "good" ? "active" : status === "cancelled" || status === "expired" ? "danger" : status === "pending" ? "warning" : "neutral";

function Feedback({ children, error = false }: { children: ReactNode; error?: boolean }) {
  return <div className={`billing-feedback ${error ? "error" : ""}`} role={error ? "alert" : "status"}>
    {error ? <AlertCircle size={15} /> : <Check size={15} />}<span>{children}</span>
  </div>;
}

function Modal({ title, subtitle, close, children, narrow = false }: {
  title: string; subtitle?: string; close: () => void; children: ReactNode; narrow?: boolean;
}) {
  return <div className="billing-modal-backdrop" role="presentation" onMouseDown={e => { if (e.target === e.currentTarget) close(); }}>
    <section className={`billing-modal ${narrow ? "narrow" : ""}`} role="dialog" aria-modal="true" aria-labelledby="billing-modal-title">
      <header className="billing-modal-head"><div><h2 id="billing-modal-title">{title}</h2>{subtitle && <p>{subtitle}</p>}</div>
        <button type="button" className="billing-close" aria-label="Close dialog" onClick={close} data-testid="button-close-billing-modal"><X size={15} /></button>
      </header>{children}
    </section>
  </div>;
}

function Field({ label, children, full = false }: { label: string; children: ReactNode; full?: boolean }) {
  return <label className={`form-label ${full ? "full" : ""}`}>{label}{children}</label>;
}

function PageState({ loading, error, retry, emptyLabel }: { loading: boolean; error?: unknown; retry: () => void; emptyLabel?: string }) {
  if (loading) return <div className="billing-state" role="status" aria-label="Loading billing data"><div className="skeleton" style={{ width: "min(480px,100%)", height: 70 }} /><span>Loading billing records…</span></div>;
  if (error) return <div className="billing-state error" role="alert"><AlertCircle size={20} /><strong>Billing records did not load</strong><span>{errText(error)}</span><button className="button" onClick={retry} data-testid="button-retry-billing">Try again</button></div>;
  if (emptyLabel) return <div className="billing-state"><CircleDollarSign size={21} /><strong>{emptyLabel}</strong><span>Records will appear here when they are available.</span></div>;
  return null;
}

type PlanDraft = { name: string; description: string; amount: string; billingInterval: SubscriptionBillingInterval; renewalMode: SubscriptionRenewalMode };
const blankPlan: PlanDraft = { name: "", description: "", amount: "", billingInterval: "monthly", renewalMode: "manual" };

function PlanEditor({ plan, close, saved }: { plan: DriverSubscriptionPlan | null; close: () => void; saved: () => void }) {
  const create = useCreateDriverSubscriptionPlan();
  const update = useUpdateDriverSubscriptionPlan();
  const [draft, setDraft] = useState<PlanDraft>(plan ? {
    name: plan.name, description: plan.description ?? "", amount: (plan.amountMinor / 100).toFixed(2),
    billingInterval: plan.billingInterval, renewalMode: plan.renewalMode,
  } : blankPlan);
  const [error, setError] = useState("");
  const pending = create.isPending || update.isPending;
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setError("");
    const amountMinor = Math.round(Number(draft.amount) * 100);
    if (!draft.name.trim() || !Number.isFinite(amountMinor) || amountMinor < 1) { setError("Enter a plan name and a valid price greater than zero."); return; }
    const input: DriverSubscriptionPlanInput = {
      name: draft.name.trim(), description: draft.description.trim() || null, amountMinor, currency: "ZAR",
      billingInterval: draft.billingInterval, renewalMode: draft.renewalMode, isActive: plan?.isActive ?? true,
    };
    try { if (plan) await update.mutateAsync({ planId: plan.id, input }); else await create.mutateAsync(input); saved(); }
    catch (e) { setError(errText(e)); }
  };
  return <Modal title={plan ? "Edit subscription plan" : "Create subscription plan"} subtitle="Prices are entered in rand and saved as integer cents." close={close}>
    <form className="billing-form" onSubmit={submit}>
      <div className="billing-form-grid">
        <Field label="Plan name"><input className="field" required maxLength={100} value={draft.name} onChange={e => setDraft({ ...draft, name: e.target.value })} data-testid="input-plan-name" /></Field>
        <Field label="Price (ZAR)"><input className="field" type="number" min="0.01" step="0.01" required value={draft.amount} onChange={e => setDraft({ ...draft, amount: e.target.value })} data-testid="input-plan-amount" /></Field>
        <Field label="Billing interval"><select className="select" value={draft.billingInterval} onChange={e => setDraft({ ...draft, billingInterval: e.target.value as SubscriptionBillingInterval })} data-testid="select-plan-interval">{intervals.map(i => <option key={i} value={i}>{pretty(i)}</option>)}</select></Field>
        <Field label="Renewal mode"><select className="select" value={draft.renewalMode} onChange={e => setDraft({ ...draft, renewalMode: e.target.value as SubscriptionRenewalMode })} data-testid="select-plan-renewal"><option value="manual">Manual renewal</option><option value="automatic">Automatic renewal</option></select></Field>
        <Field label="Description" full><textarea className="textarea" maxLength={500} value={draft.description} onChange={e => setDraft({ ...draft, description: e.target.value })} data-testid="input-plan-description" /></Field>
      </div>
      {draft.renewalMode === "automatic" && <div className="billing-note"><strong>Consent safeguard.</strong> Automatic plans only renew after the driver explicitly consents at checkout. Creating or editing a plan does not enroll drivers or create a charge.</div>}
      {error && <Feedback error>{error}</Feedback>}
      <div className="billing-form-actions"><button type="button" className="button" onClick={close}>Cancel</button><button className="button primary" disabled={pending} data-testid="button-save-plan">{pending ? "Saving…" : plan ? "Save plan" : "Create plan"}</button></div>
    </form>
  </Modal>;
}

function DriverEditor({ row, plans, close, saved }: { row: AdminDriverSubscriptionRow; plans: DriverSubscriptionPlan[]; close: () => void; saved: () => void }) {
  const update = useUpdateAdminDriverSubscription();
  const current = row.subscription;
  const [planId, setPlanId] = useState(current?.planId ?? "");
  const [status, setStatus] = useState<DriverSubscriptionStatus>(current?.status ?? "pending");
  const [expiresAt, setExpiresAt] = useState(dateInput(current?.expiresAt));
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [confirmation, setConfirmation] = useState<{ planId?: string; status?: DriverSubscriptionStatus; expiresAt?: string | null; reason: string } | null>(null);
  const plan = plans.find(p => p.id === planId);
  const allowedPlans = plans.filter(p => p.renewalMode === "manual" || p.id === current?.planId);
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setError("");
    if (!reason.trim()) { setError("Add a reason so the subscription change is auditable."); return; }
    setConfirmation({
        ...(planId && planId !== current?.planId ? { planId } : {}),
        ...(status !== current?.status ? { status } : {}),
        ...(expiresAt !== dateInput(current?.expiresAt) ? { expiresAt: expiresAt ? isoDate(expiresAt) : null } : {}),
        reason: reason.trim(),
    });
  };
  const apply = async () => {
    if (!confirmation) return;
    try { await update.mutateAsync({ driverId: row.driverId, input: confirmation }); saved(); }
    catch (e) { setError(errText(e)); setConfirmation(null); }
  };
  if (confirmation) return <Modal title="Confirm driver access update" subtitle={`Review the change for ${row.displayName} before it is applied.`} close={() => setConfirmation(null)} narrow>
    <div className="billing-note"><strong>This update may affect driver access and recurring service.</strong> The selected plan, status, and end date will be updated. No charge will be created, payment history will not change, and this admin action will not enroll the driver into automatic renewal or record consent.</div>
    {error && <Feedback error>{error}</Feedback>}
    <div className="billing-form-actions" style={{ marginTop: 18 }}>
      <button className="button" onClick={() => setConfirmation(null)} disabled={update.isPending}>Review fields</button>
      <button className="button primary" onClick={() => void apply()} disabled={update.isPending} data-testid={`button-confirm-driver-subscription-${row.driverId}`}>{update.isPending ? "Saving…" : "Confirm access update"}</button>
    </div>
  </Modal>;
  return <Modal title="Manage driver access" subtitle={`${row.displayName} · ${row.email ?? row.driverId}`} close={close}>
    <form className="billing-form" onSubmit={submit}>
      <div className="billing-driver-detail">
        <div><span>Current plan</span><strong>{current?.planName ?? "No plan assigned"}</strong></div>
        <div><span>Current status</span><strong>{current?.status ? pretty(current.status) : "No subscription"}</strong></div>
        <div><span>Renewal / consent</span><strong>{current?.renewalMode ? pretty(current.renewalMode) : "—"} · {current?.autoRenewConsentAt ? `consent ${formatDate(current.autoRenewConsentAt)}` : "no consent recorded"}</strong></div>
        <div><span>Payment history</span><strong>{row.lastPayment ? `${money(row.lastPayment.amountMinor, row.lastPayment.currency)} · ${pretty(row.lastPayment.status)} · ${formatDate(row.lastPayment.startedAt)}` : "No payment recorded"}</strong></div>
      </div>
      {current?.autoRenewActive && <div className="billing-note"><strong>Automatic renewal is active.</strong> A change may affect the driver's current access or renewal. This tool does not enroll anyone, alter consent, or change payment history.</div>}
      <div className="billing-form-grid">
        <Field label="Plan"><select className="select" value={planId} onChange={e => setPlanId(e.target.value)} data-testid={`select-driver-plan-${row.driverId}`}><option value="">No plan</option>{allowedPlans.map(p => <option key={p.id} value={p.id}>{p.name} · {money(p.amountMinor)} · {pretty(p.renewalMode)}</option>)}</select></Field>
        <Field label="Subscription status"><select className="select" value={status} onChange={e => setStatus(e.target.value as DriverSubscriptionStatus)} data-testid={`select-driver-status-${row.driverId}`}>{statuses.map(s => <option key={s} value={s}>{pretty(s)}</option>)}</select></Field>
        <Field label="Access ends on"><input className="field" type="date" value={expiresAt} onChange={e => setExpiresAt(e.target.value)} data-testid={`input-driver-expiry-${row.driverId}`} /><span className="helper">Saved as an ISO timestamp at end of the selected day. Clear to remove the end date.</span></Field>
        <Field label="Reason for change"><textarea className="textarea" required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} placeholder="Briefly explain this access update" data-testid={`input-driver-reason-${row.driverId}`} /></Field>
      </div>
      {plan?.renewalMode === "automatic" && plan.id !== current?.planId && <div className="billing-note">Automatic plans cannot be assigned here. Driver consent is only collected at checkout; choose a manual plan or leave the current plan unchanged.</div>}
      {error && <Feedback error>{error}</Feedback>}
      <div className="billing-form-actions"><button type="button" className="button" onClick={close}>Cancel</button><button className="button primary" disabled={update.isPending} data-testid={`button-save-driver-subscription-${row.driverId}`}>{update.isPending ? "Saving…" : "Save access change"}</button></div>
    </form>
  </Modal>;
}

type PromoDraft = {
  code: string; name: string; discountType: "fixed" | "percentage"; discountValue: string; maxDiscount: string;
  planId: string; startsAt: string; endsAt: string; serviceArea: string; maxRedemptions: string;
  newDriverOnly: boolean; minimumCompletedRides: string; minimumRating: string;
};
const blankPromo: PromoDraft = {
  code: "", name: "", discountType: "fixed", discountValue: "", maxDiscount: "", planId: "",
  startsAt: "", endsAt: "", serviceArea: "", maxRedemptions: "", newDriverOnly: false,
  minimumCompletedRides: "", minimumRating: "",
};
function promoDraft(promo: DriverPromotion): PromoDraft {
  return {
    code: promo.code, name: promo.name, discountType: promo.discountType, discountValue: promo.discountType === "fixed" ? (promo.discountValue / 100).toFixed(2) : String(promo.discountValue),
    maxDiscount: promo.maxDiscountMinor == null ? "" : (promo.maxDiscountMinor / 100).toFixed(2),
    planId: promo.planId ?? "", startsAt: dateInput(promo.startsAt), endsAt: dateInput(promo.endsAt),
    serviceArea: promo.serviceArea ?? "", maxRedemptions: promo.maxRedemptions == null ? "" : String(promo.maxRedemptions),
    newDriverOnly: promo.newDriverOnly, minimumCompletedRides: promo.minimumCompletedRides == null ? "" : String(promo.minimumCompletedRides),
    minimumRating: promo.minimumRating == null ? "" : String(promo.minimumRating),
  };
}
function PromotionEditor({ promo, plans, close, saved }: { promo: DriverPromotion | null; plans: DriverSubscriptionPlan[]; close: () => void; saved: () => void }) {
  const create = useCreateDriverPromotion(); const update = useUpdateDriverPromotion();
  const [draft, setDraft] = useState<PromoDraft>(promo ? promoDraft(promo) : blankPromo);
  const [error, setError] = useState("");
  const pending = create.isPending || update.isPending;
  const submit = async (event: FormEvent) => {
    event.preventDefault(); setError("");
    const enteredDiscount = Number(draft.discountValue);
    const discountValue = draft.discountType === "fixed" ? Math.round(enteredDiscount * 100) : enteredDiscount;
    if (!draft.code.trim() || !draft.name.trim() || !Number.isFinite(discountValue) || discountValue < 1) { setError("Add a valid code, name, and discount value."); return; }
    if (!draft.startsAt || !draft.endsAt || new Date(draft.endsAt) < new Date(draft.startsAt)) { setError("Choose a valid promotion window. The end date must not precede the start."); return; }
    const input: DriverPromotionInput = {
      code: draft.code.trim().toUpperCase(), name: draft.name.trim(), discountType: draft.discountType, discountValue,
      maxDiscountMinor: draft.maxDiscount ? Math.round(Number(draft.maxDiscount) * 100) : null, currency: "ZAR",
      planId: draft.planId || null, startsAt: new Date(`${draft.startsAt}T00:00:00.000Z`).toISOString(),
      endsAt: isoDate(draft.endsAt), serviceArea: draft.serviceArea.trim() || null,
      maxRedemptions: draft.maxRedemptions ? Number(draft.maxRedemptions) : null,
      newDriverOnly: draft.newDriverOnly,
      minimumCompletedRides: draft.minimumCompletedRides === "" ? null : Number(draft.minimumCompletedRides),
      minimumRating: draft.minimumRating === "" ? null : Number(draft.minimumRating),
      isActive: promo?.isActive ?? true,
    };
    if (input.discountType === "percentage" && input.discountValue > 100) { setError("Percentage discounts cannot exceed 100."); return; }
    try { if (promo) await update.mutateAsync({ promotionId: promo.id, input }); else await create.mutateAsync(input); saved(); }
    catch (e) { setError(errText(e)); }
  };
  const set = (key: keyof PromoDraft, value: string | boolean) => setDraft(old => ({ ...old, [key]: value }));
  return <Modal title={promo ? "Edit promotion" : "Create promotion"} subtitle="Set the offer and every driver qualification rule." close={close}>
    <form className="billing-form" onSubmit={submit}>
      <div className="billing-form-grid">
        <Field label="Promotion code"><input className="field" required minLength={3} maxLength={32} pattern="[A-Za-z0-9_-]+" value={draft.code} onChange={e => set("code", e.target.value)} data-testid="input-promo-code" /></Field>
        <Field label="Promotion name"><input className="field" required maxLength={100} value={draft.name} onChange={e => set("name", e.target.value)} data-testid="input-promo-name" /></Field>
        <Field label="Discount type"><select className="select" value={draft.discountType} onChange={e => set("discountType", e.target.value)} data-testid="select-promo-type"><option value="fixed">Fixed amount (ZAR)</option><option value="percentage">Percentage</option></select></Field>
        <Field label={draft.discountType === "fixed" ? "Discount amount (ZAR)" : "Discount percent"}><input className="field" type="number" min={draft.discountType === "fixed" ? "0.01" : "1"} step={draft.discountType === "fixed" ? "0.01" : "1"} max={draft.discountType === "percentage" ? 100 : undefined} required value={draft.discountValue} onChange={e => set("discountValue", e.target.value)} data-testid="input-promo-discount" /></Field>
        {draft.discountType === "percentage" && <Field label="Maximum discount (ZAR)"><input className="field" type="number" min="0.01" step="0.01" value={draft.maxDiscount} onChange={e => set("maxDiscount", e.target.value)} data-testid="input-promo-max-discount" /></Field>}
        <Field label="Applies to plan"><select className="select" value={draft.planId} onChange={e => set("planId", e.target.value)} data-testid="select-promo-plan"><option value="">All plans</option>{plans.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></Field>
        <Field label="Starts on"><input className="field" type="date" required value={draft.startsAt} onChange={e => set("startsAt", e.target.value)} data-testid="input-promo-start" /></Field>
        <Field label="Ends on"><input className="field" type="date" required value={draft.endsAt} onChange={e => set("endsAt", e.target.value)} data-testid="input-promo-end" /></Field>
        <Field label="Service area"><input className="field" maxLength={100} placeholder="Any area" value={draft.serviceArea} onChange={e => set("serviceArea", e.target.value)} data-testid="input-promo-service-area" /></Field>
        <Field label="Maximum redemptions"><input className="field" type="number" min="1" step="1" value={draft.maxRedemptions} onChange={e => set("maxRedemptions", e.target.value)} data-testid="input-promo-max-redemptions" /></Field>
        <Field label="Minimum completed rides"><input className="field" type="number" min="0" step="1" value={draft.minimumCompletedRides} onChange={e => set("minimumCompletedRides", e.target.value)} data-testid="input-promo-min-rides" /></Field>
        <Field label="Minimum verified rating"><input className="field" type="number" min="1" max="5" step="0.1" value={draft.minimumRating} onChange={e => set("minimumRating", e.target.value)} data-testid="input-promo-min-rating" /></Field>
        <label className="billing-check full"><input type="checkbox" checked={draft.newDriverOnly} onChange={e => set("newDriverOnly", e.target.checked)} data-testid="check-promo-new-driver" />Available to new drivers only</label>
      </div>
      {error && <Feedback error>{error}</Feedback>}
      <div className="billing-form-actions"><button type="button" className="button" onClick={close}>Cancel</button><button className="button primary" disabled={pending} data-testid="button-save-promotion">{pending ? "Saving…" : promo ? "Save promotion" : "Create promotion"}</button></div>
    </form>
  </Modal>;
}

export default function SubscriptionsPage() {
  const auth = useAuth();
  const [section, setSection] = useState<"plans" | "drivers" | "promotions">("plans");
  const [q, setQ] = useState(""); const [page, setPage] = useState(0);
  const plansQuery = useDriverSubscriptionPlans();
  const driversQuery = useAdminDriverSubscriptions({ ...(q.trim().length >= 2 ? { q: q.trim() } : {}), page, limit: 25 });
  const promotionsQuery = useDriverPromotions();
  const pausePlan = useUpdateDriverSubscriptionPlan(); const pausePromotion = useUpdateDriverPromotion();
  const [planEditor, setPlanEditor] = useState<DriverSubscriptionPlan | null | undefined>(undefined);
  const [editPlanConfirm, setEditPlanConfirm] = useState<DriverSubscriptionPlan | null>(null);
  const [promoEditor, setPromoEditor] = useState<DriverPromotion | null | undefined>(undefined);
  const [driverEditor, setDriverEditor] = useState<AdminDriverSubscriptionRow | null>(null);
  const [confirm, setConfirm] = useState<{ kind: "plan" | "promotion"; id: string; label: string; active: boolean; auto?: boolean } | null>(null);
  const [feedback, setFeedback] = useState<{ text: string; error: boolean } | null>(null);
  const plans = plansQuery.data ?? []; const promotions = promotionsQuery.data ?? [];
  const drivers = driversQuery.data?.drivers ?? [];
  const activePlans = useMemo(() => plans.filter(p => p.isActive), [plans]);
  const activeSubs = plans.reduce((sum, p) => sum + p.activeSubscriptionCount, 0);
  const setSectionPage = (next: typeof section) => { setSection(next); setFeedback(null); };
  const confirmToggle = async () => {
    if (!confirm) return;
    try {
      if (confirm.kind === "plan") await pausePlan.mutateAsync({ planId: confirm.id, input: { isActive: !confirm.active } });
      else await pausePromotion.mutateAsync({ promotionId: confirm.id, input: { isActive: !confirm.active } });
      setFeedback({ text: `${confirm.label} ${confirm.active ? "paused" : "activated"}.`, error: false }); setConfirm(null);
    } catch (e) { setFeedback({ text: errText(e), error: true }); setConfirm(null); }
  };
  const saved = (message: string) => { setPlanEditor(undefined); setPromoEditor(undefined); setDriverEditor(null); setFeedback({ text: message, error: false }); };
  const loading = section === "plans" ? plansQuery.isLoading : section === "drivers" ? driversQuery.isLoading : promotionsQuery.isLoading;
  const queryError = section === "plans" ? plansQuery.error : section === "drivers" ? driversQuery.error : promotionsQuery.error;
  const retry = () => { if (section === "plans") void plansQuery.refetch(); else if (section === "drivers") void driversQuery.refetch(); else void promotionsQuery.refetch(); };

  return <div className="billing-page">
    <header className="billing-top">
      <Link href="/" className="billing-brand"><VhayaMark className="brand-mark" /><span>vhaya<small>operations</small></span></Link>
      <nav className="billing-nav" aria-label="Operations navigation">{nav.map(([href, label]) => <Link href={href} key={href} className={href === "/subscriptions" ? "active" : ""} data-testid={`nav-${label.toLowerCase().replaceAll(" ", "-")}`}>{label}</Link>)}</nav>
      <div className="billing-user"><span className="secure-dot" /><span>{auth.admin?.displayName ?? "Staff"}</span><button className="button small" onClick={() => void auth.signOut()} data-testid="button-signout-billing">Sign out</button></div>
    </header>
    <main>
      <header className="billing-head"><div><div className="eyebrow">Operations / revenue controls</div><h1>Subscriptions &amp; promotions</h1><p>Manage driver access and offers. Changes are recorded; payment history is read-only.</p></div></header>
      <div className="billing-content">
        <div className="billing-banner"><ShieldCheck size={16} /><span><strong>Access and recurring charges need care.</strong> Admin edits never create a charge or enroll a driver into automatic renewal. Automatic renewal requires explicit consent at checkout.</span></div>
        <div className="billing-stats">
          <div className="billing-stat"><span>Active plan capacity in use</span><strong>{activeSubs} <small style={{ font: "500 10px 'DM Sans'", color: "#87968f" }}>active subscriptions across {activePlans.length} plans</small></strong></div>
          <div className="billing-stat"><span>Active plans</span><strong>{activePlans.length}</strong></div>
          <div className="billing-stat"><span>Active promotions</span><strong>{promotions.filter(p => p.isActive).length}</strong></div>
        </div>
        {feedback && <Feedback error={feedback.error}>{feedback.text}</Feedback>}
        <div className="billing-tabs" role="tablist" aria-label="Billing management sections">
          <button className={`billing-tab ${section === "plans" ? "active" : ""}`} role="tab" aria-selected={section === "plans"} onClick={() => setSectionPage("plans")} data-testid="tab-plans"><CreditCard size={14} />Plans<span>{plans.length}</span></button>
          <button className={`billing-tab ${section === "drivers" ? "active" : ""}`} role="tab" aria-selected={section === "drivers"} onClick={() => setSectionPage("drivers")} data-testid="tab-drivers"><Users size={14} />Driver access<span>{driversQuery.data?.drivers.length ?? "—"}</span></button>
          <button className={`billing-tab ${section === "promotions" ? "active" : ""}`} role="tab" aria-selected={section === "promotions"} onClick={() => setSectionPage("promotions")} data-testid="tab-promotions"><BadgePercent size={14} />Promotions<span>{promotions.length}</span></button>
        </div>
        <div className="billing-toolbar">
          <div><h2>{section === "plans" ? "Subscription plans" : section === "drivers" ? "Driver subscriptions" : "Promotion rules"}</h2><p>{section === "plans" ? "Price, cadence, and renewal policy for driver plans." : section === "drivers" ? "Search drivers and review access, renewal signals, and payment history." : "Discount offers and the rules that determine driver eligibility."}</p></div>
          <div className="billing-controls">
            {section === "drivers" && <label className="billing-search"><Search size={14} /><input aria-label="Search drivers" placeholder="Name, email or driver ID" value={q} onChange={e => { setQ(e.target.value); setPage(0); }} data-testid="input-subscription-driver-search" /></label>}
            {section === "plans" && <button className="button primary" onClick={() => setPlanEditor(null)} data-testid="button-create-plan"><Plus size={14} />New plan</button>}
            {section === "promotions" && <button className="button primary" onClick={() => setPromoEditor(null)} data-testid="button-create-promotion"><Plus size={14} />New promotion</button>}
          </div>
        </div>
        {section === "drivers" && q.trim().length === 1 && <p className="helper">Enter at least 2 characters to search.</p>}
        <PageState loading={loading} error={queryError} retry={retry}
          emptyLabel={!loading && !queryError && section === "plans" && !plans.length ? "No subscription plans yet" :
            !loading && !queryError && section === "drivers" && !drivers.length ? "No drivers match this search" :
              !loading && !queryError && section === "promotions" && !promotions.length ? "No promotions have been created" : undefined} />
        {!loading && !queryError && section === "plans" && plans.length > 0 && <div className="billing-plan-grid">
          {plans.map(plan => <article className={`billing-plan-card ${!plan.isActive ? "paused" : ""}`} key={plan.id} data-testid={`card-subscription-plan-${plan.id}`}>
            <div className="billing-plan-top"><div><div className="eyebrow">DRIVER PLAN</div><h3>{plan.name}</h3></div><span className={`billing-pill ${plan.isActive ? "active" : "neutral"}`}>{plan.isActive ? "Active" : "Paused"}</span></div>
            <div className="price">{money(plan.amountMinor)}<small> / {pretty(plan.billingInterval)}</small></div>
            <p>{plan.description || "No description provided."}</p>
            <div className="billing-plan-meta"><span className="billing-pill neutral">{pretty(plan.renewalMode)} renewal</span><span className="billing-pill neutral">{plan.activeSubscriptionCount} active drivers</span></div>
            {plan.renewalMode === "automatic" && <div className="billing-micro">Requires explicit driver consent at checkout; editing this plan does not enroll drivers.</div>}
            <div className="billing-plan-foot"><span className="mono">{plan.id}</span><div className="billing-actions">
              <button className="button small" onClick={() => { if (plan.renewalMode === "automatic") setEditPlanConfirm(plan); else setPlanEditor(plan); }} data-testid={`button-edit-plan-${plan.id}`}><Edit3 size={12} />Edit</button>
              <button className="button small" onClick={() => setConfirm({ kind: "plan", id: plan.id, label: plan.name, active: plan.isActive, auto: plan.renewalMode === "automatic" })} data-testid={`button-toggle-plan-${plan.id}`}>{plan.isActive ? <ToggleRight size={14} /> : <ToggleLeft size={14} />}{plan.isActive ? "Pause" : "Activate"}</button>
            </div></div>
          </article>)}
        </div>}
        {!loading && !queryError && section === "drivers" && drivers.length > 0 && <>
          <div className="billing-table"><table><thead><tr><th>Driver</th><th>Subscription</th><th>Renewal signals</th><th>Last payment</th><th>Verified rating</th><th>Access details</th><th>Manage</th></tr></thead><tbody>
            {drivers.map(row => {
              const sub = row.subscription;
              return <tr key={row.driverId} data-testid={`row-driver-subscription-${row.driverId}`}>
                <td><span className="billing-cell-title">{row.displayName}</span><span className="billing-micro">{row.email ?? row.driverId}</span></td>
                <td>{sub ? <><span className="billing-cell-title">{sub.planName ?? "Plan not set"}</span><span className={`billing-pill ${statusTone(sub.status)}`}>{pretty(sub.status)}</span><span className="billing-micro">{money(sub.amountMinor, sub.currency)} · {pretty(sub.renewalMode)}</span></> : <span className="billing-pill neutral">No subscription</span>}</td>
                <td>{sub ? <><span className={`billing-pill ${sub.autoRenewActive ? "active" : "neutral"}`}>{sub.autoRenewActive ? "Auto-renew on" : "Auto-renew off"}</span>{sub.autoRenewIssue && <span className="billing-micro" title={sub.autoRenewIssue}>Issue: {sub.autoRenewIssue}</span>}{sub.cancelAtPeriodEnd && <span className="billing-micro">Cancellation at period end</span>}</> : "—"}</td>
                <td>{row.lastPayment ? <><span className="billing-money">{money(row.lastPayment.amountMinor, row.lastPayment.currency)}</span><span className="billing-micro">{pretty(row.lastPayment.status)} · {row.lastPayment.provider} · {formatDate(row.lastPayment.startedAt)}</span></> : <span className="billing-micro">No payment recorded</span>}</td>
                <td>{row.rating.averageRating == null ? "Not rated" : <><span className="billing-cell-title">{row.rating.averageRating.toFixed(1)} / 5</span><span className="billing-micro">{row.rating.ratingCount} ratings · verified</span></>}</td>
                <td>{sub ? <><span className="billing-micro">Started {formatDate(sub.startedAt)}</span><span className="billing-micro">Ends {formatDate(sub.expiresAt)}</span><span className="billing-micro">Next payment {formatDate(sub.nextPaymentAt)}</span><span className="billing-micro">Provider {sub.provider ?? "—"}</span><span className="billing-micro">Consent {formatDate(sub.autoRenewConsentAt)}</span></> : "—"}</td>
                <td><button className="button small" onClick={() => setDriverEditor(row)} data-testid={`button-manage-driver-${row.driverId}`}>Manage access</button></td>
              </tr>;
            })}
          </tbody></table></div>
          <div className="billing-pagination"><span>Page {page + 1}</span><button className="button small" disabled={!page} onClick={() => setPage(page - 1)} data-testid="button-subscriptions-prev"><ChevronLeft size={13} />Previous</button><button className="button small" disabled={!driversQuery.data?.hasMore} onClick={() => setPage(page + 1)} data-testid="button-subscriptions-next">Next<ChevronRight size={13} /></button></div>
        </>}
        {!loading && !queryError && section === "promotions" && promotions.length > 0 && <div className="billing-table"><table><thead><tr><th>Offer</th><th>Discount</th><th>Qualification rules</th><th>Window</th><th>Redemptions</th><th>Status</th><th>Actions</th></tr></thead><tbody>
          {promotions.map(promo => <tr key={promo.id} data-testid={`row-promotion-${promo.id}`}>
            <td><span className="billing-cell-title">{promo.name}</span><span className="billing-micro mono">{promo.code} · {promo.id}</span><span className="billing-micro">{promo.planId ? plans.find(p => p.id === promo.planId)?.name ?? "Plan-specific" : "All plans"}{promo.serviceArea ? ` · ${promo.serviceArea}` : ""}</span></td>
            <td><span className="billing-cell-title">{promo.discountType === "fixed" ? money(promo.discountValue, promo.currency) : `${promo.discountValue}%`}</span>{promo.maxDiscountMinor != null && <span className="billing-micro">Cap {money(promo.maxDiscountMinor, promo.currency)}</span>}</td>
            <td><span className="billing-micro">{promo.newDriverOnly ? "New drivers only" : "Any driver"}</span><span className="billing-micro">Rides: {promo.minimumCompletedRides ?? "No minimum"} · Rating: {promo.minimumRating ?? "No minimum"}</span><span className="billing-micro">Max uses: {promo.maxRedemptions ?? "Unlimited"}</span></td>
            <td><span className="billing-micro"><CalendarDays size={11} style={{ verticalAlign: "middle" }} /> {formatDate(promo.startsAt)}</span><span className="billing-micro">to {formatDate(promo.endsAt)}</span></td>
            <td><span className="billing-cell-title">{promo.redemptionCount}</span><span className="billing-micro">{promo.maxRedemptions == null ? "of unlimited" : `of ${promo.maxRedemptions}`}</span></td>
            <td><span className={`billing-pill ${promo.isActive ? "active" : "neutral"}`}>{promo.isActive ? "Active" : "Paused"}</span></td>
            <td><div className="billing-actions"><button className="button small" onClick={() => setPromoEditor(promo)} data-testid={`button-edit-promotion-${promo.id}`}><Edit3 size={12} />Edit</button><button className="button small" onClick={() => setConfirm({ kind: "promotion", id: promo.id, label: promo.code, active: promo.isActive })} data-testid={`button-toggle-promotion-${promo.id}`}>{promo.isActive ? <ToggleRight size={14} /> : <ToggleLeft size={14} />}{promo.isActive ? "Pause" : "Activate"}</button></div></td>
          </tr>)}
        </tbody></table></div>}
      </div>
    </main>
    {planEditor !== undefined && <PlanEditor plan={planEditor} close={() => setPlanEditor(undefined)} saved={() => saved(planEditor ? "Subscription plan updated." : "Subscription plan created.")} />}
    {promoEditor !== undefined && <PromotionEditor promo={promoEditor} plans={plans} close={() => setPromoEditor(undefined)} saved={() => saved(promoEditor ? "Promotion updated." : "Promotion created.")} />}
    {driverEditor && <DriverEditor row={driverEditor} plans={plans} close={() => setDriverEditor(null)} saved={() => saved("Driver subscription access updated. Payment history and renewal consent were not changed.")} />}
    {editPlanConfirm && <Modal title="Review automatic plan edit" subtitle={`You are about to edit ${editPlanConfirm.name}.`} close={() => setEditPlanConfirm(null)} narrow>
      <div className="billing-note"><strong>This active plan supports automatic renewal.</strong> Changes to plan terms could affect recurring service. Existing driver consent remains as recorded; this edit will not enroll anyone or create a charge.</div>
      <div className="billing-form-actions" style={{ marginTop: 18 }}><button className="button" onClick={() => setEditPlanConfirm(null)}>Go back</button><button className="button primary" onClick={() => { setPlanEditor(editPlanConfirm); setEditPlanConfirm(null); }} data-testid="button-continue-plan-edit">Continue to edit</button></div>
    </Modal>}
    {confirm && <Modal title={`${confirm.active ? "Pause" : "Activate"} ${confirm.kind === "plan" ? "plan" : "promotion"}?`}
      subtitle={confirm.active ? "Pausing stops this offer from being used for new checkouts. Existing driver access and recorded payments are not altered." : "This makes the item available again; it does not enroll drivers or charge anyone."}
      close={() => setConfirm(null)} narrow>
      {confirm.kind === "plan" && confirm.auto && <div className="billing-note">This is an automatic-renewal plan. A plan edit or pause can affect recurring access. Drivers still need explicit consent at checkout; no consent or payment is changed here.</div>}
      <div className="billing-form-actions" style={{ marginTop: 18 }}><button className="button" onClick={() => setConfirm(null)}>Go back</button><button className={`button ${confirm.active ? "danger" : "primary"}`} onClick={() => void confirmToggle()} disabled={pausePlan.isPending || pausePromotion.isPending} data-testid="button-confirm-billing-toggle">{pausePlan.isPending || pausePromotion.isPending ? "Saving…" : confirm.active ? "Confirm pause" : "Confirm activation"}</button></div>
    </Modal>}
  </div>;
}
