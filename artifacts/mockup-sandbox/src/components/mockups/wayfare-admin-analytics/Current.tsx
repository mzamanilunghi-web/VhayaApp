import {
  Activity,
  AlertCircle,
  ArrowUpRight,
  BriefcaseBusiness,
  CalendarDays,
  ChevronDown,
  CircleDollarSign,
  CreditCard,
  Grid2X2,
  LifeBuoy,
  MessageSquare,
  Navigation,
  ShieldCheck,
  Truck,
  UserCheck,
  Users,
  Wallet,
} from "lucide-react";
import "./_group.css";

const navItems = [
  { label: "Overview", icon: Grid2X2, active: true },
  { label: "Driver reviews", icon: UserCheck, count: "7" },
  { label: "People & roles", icon: Users },
  { label: "Payments", icon: CreditCard },
  { label: "Wallets", icon: Wallet },
  { label: "Compliance", icon: ShieldCheck },
  { label: "Support cases", icon: MessageSquare },
  { label: "Driver payouts", icon: BriefcaseBusiness },
];

const metrics = [
  { label: "Ride requests", value: "103", icon: Navigation, tone: "coral" },
  { label: "Active trips", value: "18", icon: Truck, tone: "sage" },
  { label: "Driver reviews", value: "7", icon: Users, tone: "gold" },
  { label: "Payment issues", value: "3", icon: AlertCircle, tone: "red" },
];

const recentTrips = [
  {
    code: "WF-2841",
    rider: "Lerato Mokoena",
    route: "Rosebank Mall → OR Tambo International",
    category: "Airport transfer",
    time: "08:42",
    fare: "R 386.50",
    status: "Needs driver",
    tone: "coral",
  },
  {
    code: "WF-2838",
    rider: "Thabo Ndlovu",
    route: "Braamfontein → Sandton City",
    category: "Everyday ride",
    time: "08:38",
    fare: "R 174.00",
    status: "Needs driver",
    tone: "coral",
  },
  {
    code: "WF-2833",
    rider: "Amara Jacobs",
    route: "Claremont → Cape Town International",
    category: "Airport transfer",
    time: "08:31",
    fare: "R 248.50",
    status: "Assigned",
    tone: "sage",
  },
  {
    code: "WF-2829",
    rider: "Zanele Khumalo",
    route: "Umhlanga Ridge → King Shaka International",
    category: "Airport transfer",
    time: "08:24",
    fare: "R 312.00",
    status: "Review",
    tone: "gold",
  },
];

function StatusPill({
  children,
  tone,
}: {
  children: string;
  tone: string;
}) {
  const styles: Record<string, string> = {
    coral: "bg-[#fae8df] text-[#a44c34]",
    sage: "bg-[#e5eee5] text-[#41634b]",
    gold: "bg-[#f4efdd] text-[#8a7131]",
    red: "bg-[#fae8e5] text-[#a64438]",
  };

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-semibold ${styles[tone]}`}
    >
      <span
        className={`h-1.5 w-1.5 rounded-full ${
          tone === "coral"
            ? "bg-[#df7455]"
            : tone === "sage"
              ? "bg-[#6a9670]"
              : tone === "gold"
                ? "bg-[#c6a84e]"
                : "bg-[#d66b5f]"
        }`}
      />
      {children}
    </span>
  );
}

export function Current() {
  return (
    <div className="wayfare-admin-theme min-h-screen bg-[#f5f3ec] text-[#202823]">
      <div className="flex min-h-screen">
        <aside className="hidden w-[232px] shrink-0 flex-col bg-[#26362e] px-4 py-6 text-[#f6f3e9] lg:flex">
          <div className="mb-9 flex items-center rounded-xl bg-[#f5f3ec] px-3 py-2">
            <img
              src="/__mockup/images/wayfare-logo.png"
              alt="Wayfare"
              className="h-7 w-[138px] object-contain object-left"
            />
          </div>
          <div className="mb-3 px-3 text-[9px] font-bold uppercase tracking-[.18em] text-[#9caa9f]">
            Operations
          </div>
          <nav className="space-y-1" aria-label="Admin sections">
            {navItems.map(({ label, icon: Icon, active, count }) => (
              <div
                key={label}
                className={`flex min-h-10 items-center gap-3 rounded-xl px-3 text-[12px] ${
                  active
                    ? "bg-[#394d41] font-semibold text-white"
                    : "text-[#c1cbc2]"
                }`}
              >
                <Icon size={15} strokeWidth={1.8} />
                <span className="flex-1">{label}</span>
                {count ? (
                  <span className="rounded-full bg-[#e96b43] px-2 py-0.5 text-[9px] font-bold text-white">
                    {count}
                  </span>
                ) : null}
              </div>
            ))}
          </nav>
          <div className="mt-auto rounded-2xl border border-white/10 bg-white/[.045] p-4">
            <div className="mb-2 flex items-center gap-2 text-[11px] font-semibold text-[#e8ede7]">
              <span className="h-2 w-2 rounded-full bg-[#8bac80]" />
              Johannesburg desk
            </div>
            <div className="text-[10px] leading-5 text-[#aebbb0]">
              Shared operations view
            </div>
            <div className="mt-3 border-t border-white/10 pt-3 text-[9px] text-[#aebbb0]">
              Preview data · South Africa
            </div>
          </div>
        </aside>

        <main className="min-w-0 flex-1">
          <header className="flex h-[66px] items-center justify-between border-b border-[#e3e0d6] bg-[#fffefa] px-5 sm:px-8">
            <div>
              <div className="text-[9px] font-semibold uppercase tracking-[.16em] text-[#858b82]">
                Operations / Overview
              </div>
              <div className="mt-0.5 text-[12px] font-semibold text-[#303a32]">
                Wayfare admin
              </div>
            </div>
            <div className="flex items-center gap-2 sm:gap-4">
              <div className="hidden items-center gap-2 rounded-full border border-[#e3e0d6] bg-[#fbfaf5] px-3 py-1.5 text-[10px] font-medium text-[#68736a] sm:flex">
                <span className="h-1.5 w-1.5 rounded-full bg-[#77a17b]" />
                Desk active
              </div>
              <button className="flex items-center gap-2 rounded-lg border border-[#e3e0d6] bg-[#fffefa] px-3 py-2 text-[10px] font-semibold text-[#586359]">
                <CalendarDays size={13} />
                All activity
                <ChevronDown size={12} />
              </button>
              <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[#dfe8dc] text-[10px] font-bold text-[#3f5946]">
                KM
              </div>
            </div>
          </header>

          <div className="mx-auto max-w-[1320px] px-5 pb-10 pt-7 sm:px-8">
            <div className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
              <div>
                <div className="mb-2 inline-flex items-center gap-2 rounded-full bg-[#e7ece3] px-2.5 py-1 text-[9px] font-bold uppercase tracking-[.12em] text-[#4f6952]">
                  <Activity size={11} />
                  Live operations overview
                  <span className="font-medium normal-case tracking-normal text-[#849181]">
                    · preview data
                  </span>
                </div>
                <h1 className="font-['Inter'] text-[28px] font-bold tracking-[-.045em] text-[#26342b] sm:text-[32px]">
                  Ride overview.
                </h1>
                <p className="mt-1 text-[11px] text-[#747c73]">
                  A clear view of requests, completed fares and recent activity.
                </p>
              </div>
              <button className="inline-flex w-fit items-center gap-2 rounded-lg border border-[#e3e0d6] bg-[#fffefa] px-3 py-2 text-[10px] font-semibold text-[#69736a]">
                Last updated just now
                <ArrowUpRight size={12} />
              </button>
            </div>

            <div className="mb-5 flex items-start gap-3 rounded-2xl border border-[#dfe6da] bg-[#e9efe6] px-4 py-3.5">
              <ShieldCheck size={16} className="mt-0.5 shrink-0 text-[#536d55]" />
              <p className="text-[10px] leading-[1.65] text-[#465848]">
                Shared ride data refreshes automatically. Never approve a driver
                until identity, vehicle, and applicable permit checks are
                completed independently.
              </p>
            </div>

            <div className="mb-5 grid grid-cols-2 gap-3 xl:grid-cols-4">
              {metrics.map(({ label, value, icon: Icon, tone }) => (
                <section
                  key={label}
                  className="rounded-2xl border border-[#e4e2d8] bg-[#fffefa] p-4 shadow-[0_4px_15px_rgba(44,53,44,.025)]"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-[10px] font-medium text-[#7a8279]">
                      {label}
                    </span>
                    <span
                      className={`flex h-8 w-8 items-center justify-center rounded-xl ${
                        tone === "coral"
                          ? "bg-[#fae8df] text-[#ad563d]"
                          : tone === "sage"
                            ? "bg-[#e5eee5] text-[#527254]"
                            : tone === "gold"
                              ? "bg-[#f4efdd] text-[#8a7131]"
                              : "bg-[#fae8e5] text-[#aa5145]"
                      }`}
                    >
                      <Icon size={15} />
                    </span>
                  </div>
                  <div className="mt-3 font-['Inter'] text-[26px] font-bold tracking-[-.04em] text-[#26342b]">
                    {value}
                  </div>
                  <div className="mt-1 text-[9px] text-[#969b92]">
                    Current snapshot
                  </div>
                </section>
              ))}
            </div>

            <div className="grid items-start gap-5 xl:grid-cols-[.72fr_1.28fr]">
              <section className="rounded-2xl border border-[#e4e2d8] bg-[#fffefa] p-5 shadow-[0_4px_15px_rgba(44,53,44,.025)]">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h2 className="text-[14px] font-bold tracking-[-.02em] text-[#303a32]">
                      Completed ride fares
                    </h2>
                    <p className="mt-1 text-[10px] leading-4 text-[#858b81]">
                      Totals by currency from completed trip records.
                    </p>
                  </div>
                  <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-[#f5eee5] text-[#a45b3f]">
                    <CircleDollarSign size={15} />
                  </span>
                </div>
                <div className="mt-5 rounded-xl bg-[#f7f6f0] px-4 py-4">
                  <div className="text-[9px] font-semibold uppercase tracking-[.1em] text-[#858b81]">
                    ZAR · Completed fares
                  </div>
                  <div className="mt-1 font-['Inter'] text-[25px] font-bold tracking-[-.04em] text-[#26342b]">
                    R 18,420
                  </div>
                  <div className="mt-2 border-t border-[#e5e2d8] pt-2 text-[9px] leading-4 text-[#858b81]">
                    Ride fare totals only — not payment captures or driver
                    settlements.
                  </div>
                </div>
                <div className="mt-4 flex items-center justify-between text-[10px]">
                  <span className="text-[#798177]">Completed rides</span>
                  <span className="font-semibold text-[#39463b]">72 trips</span>
                </div>
              </section>

              <section className="overflow-hidden rounded-2xl border border-[#e4e2d8] bg-[#fffefa] shadow-[0_4px_15px_rgba(44,53,44,.025)]">
                <div className="flex items-center justify-between border-b border-[#eeece5] px-5 py-4">
                  <div>
                    <h2 className="text-[14px] font-bold tracking-[-.02em] text-[#303a32]">
                      Recent ride activity
                    </h2>
                    <p className="mt-1 text-[10px] text-[#858b81]">
                      Latest ride requests across Wayfare.
                    </p>
                  </div>
                  <span className="rounded-lg bg-[#f0efe9] px-2.5 py-1.5 text-[9px] font-semibold text-[#737b72]">
                    103 total
                  </span>
                </div>
                <div>
                  {recentTrips.map((trip) => (
                    <article
                      key={trip.code}
                      className="grid grid-cols-[1fr_auto] gap-x-3 gap-y-2 border-b border-[#eeece5] px-5 py-3.5 last:border-0 sm:grid-cols-[minmax(0,1fr)_auto_auto]"
                    >
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="text-[10px] font-semibold text-[#39453c]">
                            {trip.rider}
                          </span>
                          <span className="text-[9px] text-[#9a9d94]">
                            {trip.code}
                          </span>
                        </div>
                        <div className="mt-1 flex min-w-0 items-center gap-1.5 text-[9px] text-[#68736a]">
                          <span className="truncate">{trip.route}</span>
                        </div>
                        <div className="mt-1 text-[9px] text-[#969b92]">
                          {trip.category} · {trip.time}
                        </div>
                      </div>
                      <div className="col-start-2 row-start-1 text-right text-[10px] font-semibold text-[#39453c] sm:col-start-auto sm:row-start-auto">
                        {trip.fare}
                      </div>
                      <div className="col-span-2 sm:col-span-1">
                        <StatusPill tone={trip.tone}>{trip.status}</StatusPill>
                      </div>
                    </article>
                  ))}
                </div>
              </section>
            </div>

            <div className="mt-5 flex flex-wrap items-center justify-between gap-2 border-t border-[#e3e0d6] pt-4 text-[9px] text-[#90948b]">
              <span>Wayfare Operations · South Africa</span>
              <span>
                Values and ride records shown are sample data for this design
                reference.
              </span>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}

export default Current;
