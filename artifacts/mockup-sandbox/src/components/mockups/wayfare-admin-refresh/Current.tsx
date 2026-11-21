import { useState } from "react";
import {
  BadgeCheck,
  BriefcaseBusiness,
  Check,
  ChevronDown,
  CreditCard,
  Grid2X2,
  MessageSquare,
  ShieldCheck,
  Users,
  UserRoundCheck,
  Wallet,
  X,
} from "lucide-react";
import "./_group.css";

type CheckStatus = "Not reviewed" | "Verified" | "Needs follow-up" | "Not required";

const sections = [
  { label: "Overview", icon: Grid2X2 },
  { label: "Driver reviews", icon: UserRoundCheck, count: "3" },
  { label: "People & roles", icon: Users },
  { label: "Payments", icon: CreditCard },
  { label: "Wallets", icon: Wallet },
  { label: "Compliance", icon: ShieldCheck, count: "3", active: true },
  { label: "Support cases", icon: MessageSquare },
  { label: "Driver payouts", icon: BriefcaseBusiness },
];

const checks = [
  { label: "Identity", expiry: "", status: "Verified" as CheckStatus, note: "Original inspected" },
  { label: "Driver licence", expiry: "2027-04-18", status: "Verified" as CheckStatus, note: "Class B checked" },
  { label: "Vehicle registration", expiry: "2027-02-28", status: "Verified" as CheckStatus, note: "Registration confirmed" },
  { label: "Operating permit", expiry: "2027-01-31", status: "Verified" as CheckStatus, note: "Permit checked" },
];

export function Current() {
  const [confirmed, setConfirmed] = useState(false);
  const [saved, setSaved] = useState<string[]>([]);
  const [statusByCheck, setStatusByCheck] = useState<Record<string, CheckStatus>>(
    Object.fromEntries(checks.map((item) => [item.label, item.status])),
  );
  const [notice, setNotice] = useState("");
  const allVerified = checks.every(
    (item) => statusByCheck[item.label] === "Verified" || statusByCheck[item.label] === "Not required",
  );

  function saveCheck(name: string) {
    setSaved((current) => [...new Set([...current, name])]);
    setNotice(`${name} check saved`);
    window.setTimeout(() => setNotice(""), 2200);
  }

  return (
    <div className="wayfare-admin-current min-h-screen bg-[#f4f5ef] text-[#26342f]">
      <main className="min-w-0">
          <header className="flex h-[60px] items-center justify-between border-b border-[#dce3da] bg-[#fffefa] px-5 sm:px-8">
            <div className="text-[11px] text-[#738078]">Operations <span className="px-1.5">/</span> Compliance</div>
            <button className="flex items-center gap-2 rounded-xl border border-[#dce3da] px-3 py-2 text-[11px] font-semibold">
              <span className="flex h-7 w-7 items-center justify-center rounded-full bg-[#e8eee4] text-[10px] text-[#3f5946]">KM</span>
              Kea Maseko
              <ChevronDown size={13} />
            </button>
          </header>

          <div className="mx-auto max-w-[1420px] px-4 pb-12 pt-6 sm:px-8">
            <div className="mb-4 flex flex-wrap items-end justify-between gap-4">
              <div>
                <p className="text-[9px] font-semibold uppercase tracking-[.15em] text-[#738078]">Operations</p>
                <h1 className="mt-1 text-[29px] font-bold tracking-[-.045em]">Compliance.</h1>
              </div>
              <span className="rounded-full border border-[#dce3da] bg-[#fffefa] px-3 py-1.5 text-[10px] font-medium text-[#738078]">Sample review data</span>
            </div>

            <div className="mb-5 flex gap-3 rounded-2xl bg-[#e8eee4] px-4 py-3.5 text-[11px] leading-[1.65] text-[#435349]">
              <ShieldCheck className="mt-0.5 shrink-0" size={16} />
              <p>Shared ride data refreshes automatically. Never approve a driver until identity, vehicle, and applicable permit checks are completed independently.</p>
            </div>

            <div className="grid items-start gap-5 xl:grid-cols-[244px_minmax(0,1fr)]">
              <section className="hidden rounded-[18px] border border-[#dce3da] bg-[#fffefa] p-3 xl:block">
                <p className="mb-3 px-2 text-[9px] font-bold uppercase tracking-[.13em] text-[#738078]">Admin menu</p>
                <div className="space-y-1">
                  {sections.map(({ label, icon: Icon, count, active }) => (
                    <div key={label} className={`flex min-h-10 items-center gap-2.5 rounded-xl px-2.5 text-[11px] ${
                      active ? "bg-[#df704c] font-semibold text-white" : "text-[#26342f]"
                    }`}>
                      <Icon size={15} />
                      <span className="flex-1">{label}</span>
                      {count ? <span className="rounded-full bg-[#fae8df] px-2 py-0.5 text-[9px] font-bold text-[#a44c34]">{count}</span> : null}
                    </div>
                  ))}
                </div>
              </section>

              <section className="min-w-0 rounded-[20px] border border-[#dce3da] bg-[#fffefa] p-4 shadow-[0_5px_22px_rgba(44,53,44,.035)] sm:p-5">
                <div className="flex flex-wrap items-end justify-between gap-2">
                  <div>
                    <h2 className="text-[18px] font-semibold tracking-[-.025em]">Driver compliance</h2>
                    <p className="mt-1 text-[11px] text-[#738078]">Record staff checks for each required document.</p>
                  </div>
                  <span className="rounded-full bg-[#f4f5ef] px-3 py-1.5 text-[10px] font-semibold text-[#738078]">2 checks need attention across the queue</span>
                </div>
                <div className="mt-3 flex gap-2 rounded-xl border border-[#dce3da] bg-[#f8f9f5] p-3 text-[10px] leading-4 text-[#738078]">
                  <ShieldCheck className="mt-0.5 shrink-0 text-[#738078]" size={14} />
                  <p>Vhaya does not store document images. Inspect originals through your approved staff process.</p>
                </div>

                <article className="mt-4 rounded-2xl border border-[#dce3da] bg-[#fffefa] p-4">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <h3 className="text-[15px] font-semibold">Nomsa Mthembu</h3>
                      <p className="mt-1 text-[11px] text-[#738078]">nomsa.mthembu@example.com · submitted</p>
                      <p className="mt-1.5 text-[11px] font-medium">VW Polo · GP 774-208</p>
                    </div>
                    <span className="rounded-full bg-[#f7efdc] px-2.5 py-1 text-[9px] font-bold uppercase tracking-[.04em] text-[#8a7131]">Pending staff approval</span>
                  </div>

                  <div className="mt-4 border-t border-[#dce3da] pt-3">
                    <div className="mb-2 flex items-center justify-between">
                      <h4 className="text-[12px] font-semibold">Final application decision</h4>
                      <span className={`text-[10px] font-semibold ${allVerified ? "text-[#4f7656]" : "text-[#9c4036]"}`}>
                        {allVerified ? "All checks cleared" : "Checks still needed"}
                      </span>
                    </div>
                    <label className="flex cursor-pointer items-start gap-2 rounded-xl border border-[#dce3da] bg-[#f8f9f5] p-3 text-[10px] leading-[1.5]">
                      <input type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} className="mt-0.5 accent-[#df704c]" />
                      <span>I completed the off-platform identity and legal checks and inspected the original documents.</span>
                    </label>
                    <button
                      disabled={!confirmed || !allVerified}
                      onClick={() => setNotice("Approval confirmation opened")}
                      className="mt-2 inline-flex min-h-10 items-center gap-2 rounded-xl bg-[#df704c] px-4 text-[11px] font-semibold text-white transition hover:brightness-95 disabled:cursor-not-allowed disabled:bg-[#c6cec5] disabled:text-[#738078]"
                    >
                      <Check size={14} /> Approve application
                    </button>
                  </div>

                  <div className="mt-4 space-y-3">
                    {checks.map((item) => {
                      const status = statusByCheck[item.label];
                      return (
                        <section key={item.label} className="border-t border-[#dce3da] pt-3">
                          <div className="flex flex-wrap items-center justify-between gap-2">
                            <h4 className="text-[12px] font-semibold">{item.label}</h4>
                            {saved.includes(item.label) ? (
                              <span className="inline-flex items-center gap-1 rounded-full bg-[#e8eee4] px-2 py-1 text-[9px] font-semibold text-[#4f7656]"><BadgeCheck size={12} /> Saved</span>
                            ) : null}
                          </div>
                          <div className="mt-2 flex flex-wrap gap-1.5">
                            {(["Not reviewed", "Verified", "Needs follow-up", "Not required"] as const).map((choice) => (
                              <button
                                key={choice}
                                onClick={() => setStatusByCheck((current) => ({ ...current, [item.label]: choice }))}
                                aria-pressed={status === choice}
                                className={`rounded-lg border px-2.5 py-2 text-[9px] font-medium ${
                                  status === choice ? "border-[#df704c] bg-[#fbeee8] text-[#9c4e37]" : "border-[#dce3da] bg-[#fffefa] text-[#738078]"
                                }`}
                              >{choice}</button>
                            ))}
                          </div>
                          {item.expiry ? (
                            <label className="mt-2 block max-w-[270px] text-[10px] font-medium text-[#26342f]">
                              Expiry date (YYYY-MM-DD)
                              <input defaultValue={item.expiry} className="mt-1 block h-10 w-full rounded-xl border border-[#dce3da] bg-[#fffefa] px-3 text-[11px]" />
                            </label>
                          ) : null}
                          <label className="mt-2 block text-[10px] font-medium text-[#26342f]">
                            Staff note
                            <input defaultValue={item.note} placeholder="Optional review note" className="mt-1 block h-10 w-full rounded-xl border border-[#dce3da] bg-[#fffefa] px-3 text-[11px]" />
                          </label>
                          <button onClick={() => saveCheck(item.label)} className="mt-2 inline-flex min-h-9 items-center gap-2 rounded-xl bg-[#df704c] px-3.5 text-[10px] font-semibold text-white hover:brightness-95">
                            <Check size={13} /> Save check
                          </button>
                        </section>
                      );
                    })}
                  </div>
                </article>
                {notice ? (
                  <div role="status" className="fixed bottom-5 right-5 z-20 flex items-center gap-2 rounded-xl bg-[#26342f] px-4 py-3 text-[11px] font-semibold text-white shadow-xl">
                    {notice === "Approval confirmation opened" ? <X size={14} /> : <Check size={14} />}
                    {notice}
                  </div>
                ) : null}
              </section>
            </div>
          </div>
      </main>
    </div>
  );
}

export default Current;
