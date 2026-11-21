import { useMemo, useState } from "react";
import {
  Activity,
  ArrowDownUp,
  ArrowRight,
  BadgeCheck,
  BriefcaseBusiness,
  Check,
  CheckCheck,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  CircleCheck,
  Clock3,
  CreditCard,
  FileCheck2,
  Grid2X2,
  Info,
  LockKeyhole,
  Mail,
  Menu,
  MessageSquare,
  MoreHorizontal,
  Search,
  ShieldCheck,
  UserRoundCheck,
  Users,
  Wallet,
  X,
} from "lucide-react";
import "./_group.css";

type Status = "Not reviewed" | "Verified" | "Needs follow-up" | "Not required";
type DocumentKey = "identity" | "licence" | "registration" | "permit";
type DocumentCheck = {
  key: DocumentKey;
  label: string;
  short: string;
  status: Status;
  expiry: string;
  note: string;
};
type Application = {
  id: string;
  name: string;
  email: string;
  initials: string;
  location: string;
  submitted: string;
  vehicle: string;
  plate: string;
  checks: DocumentCheck[];
};

const initialApplications: Application[] = [
  {
    id: "WF-2048",
    name: "Nomsa Mthembu",
    email: "nomsa.mthembu@example.co.za",
    initials: "NM",
    location: "Johannesburg, GP",
    submitted: "Today, 09:42",
    vehicle: "Volkswagen Polo Vivo · White",
    plate: "GP 774-208",
    checks: [
      { key: "identity", label: "Identity document", short: "Identity", status: "Verified", expiry: "", note: "Original inspected in staff process" },
      { key: "licence", label: "Driver licence", short: "Licence", status: "Verified", expiry: "2027-04-18", note: "Code B confirmed" },
      { key: "registration", label: "Vehicle registration", short: "Registration", status: "Not reviewed", expiry: "2027-02-28", note: "" },
      { key: "permit", label: "Operating permit", short: "Permit", status: "Not reviewed", expiry: "2027-01-31", note: "" },
    ],
  },
  {
    id: "WF-2046",
    name: "Thabo Nkosi",
    email: "thabo.nkosi@example.co.za",
    initials: "TN",
    location: "Cape Town, WC",
    submitted: "Today, 08:16",
    vehicle: "Toyota Corolla Quest · Silver",
    plate: "CA 481-936",
    checks: [
      { key: "identity", label: "Identity document", short: "Identity", status: "Verified", expiry: "", note: "Details match application" },
      { key: "licence", label: "Driver licence", short: "Licence", status: "Needs follow-up", expiry: "2026-11-03", note: "Please confirm licence class" },
      { key: "registration", label: "Vehicle registration", short: "Registration", status: "Verified", expiry: "2026-10-15", note: "Registration confirmed" },
      { key: "permit", label: "Operating permit", short: "Permit", status: "Not reviewed", expiry: "", note: "" },
    ],
  },
  {
    id: "WF-2041",
    name: "Ayesha Patel",
    email: "ayesha.patel@example.co.za",
    initials: "AP",
    location: "Durban, KZN",
    submitted: "Yesterday, 16:08",
    vehicle: "",
    plate: "",
    checks: [
      { key: "identity", label: "Identity document", short: "Identity", status: "Verified", expiry: "", note: "Original inspected" },
      { key: "licence", label: "Driver licence", short: "Licence", status: "Not reviewed", expiry: "", note: "" },
      { key: "registration", label: "Vehicle registration", short: "Registration", status: "Not reviewed", expiry: "", note: "" },
      { key: "permit", label: "Operating permit", short: "Permit", status: "Not reviewed", expiry: "", note: "" },
    ],
  },
];

const adminSections = [
  { label: "Overview", icon: Grid2X2 },
  { label: "Driver reviews", icon: UserRoundCheck, count: true },
  { label: "People & roles", icon: Users },
  { label: "Payments", icon: CreditCard },
  { label: "Wallets", icon: Wallet },
  { label: "Compliance", icon: ShieldCheck, count: true },
  { label: "Support cases", icon: MessageSquare },
  { label: "Driver payouts", icon: BriefcaseBusiness },
];

const statusOptions: Status[] = ["Not reviewed", "Verified", "Needs follow-up", "Not required"];

function isComplete(check: DocumentCheck) {
  return check.status === "Verified" || (check.status === "Not required" && !!check.note.trim());
}

function statusStyle(status: Status) {
  if (status === "Verified") return "border-[#d1e2d4] bg-[#e9f2e9] text-[#42694b]";
  if (status === "Needs follow-up") return "border-[#efd9c8] bg-[#fbf0e6] text-[#9a5d32]";
  if (status === "Not required") return "border-[#d9dfd9] bg-[#f0f2ee] text-[#68756c]";
  return "border-[#e7ddd1] bg-[#f7f2e9] text-[#8b6b43]";
}

function formatDocumentCount(application: Application) {
  return application.checks.filter(isComplete).length;
}

export function Enhanced() {
  const [applications, setApplications] = useState(initialApplications);
  const [selectedId, setSelectedId] = useState(initialApplications[0].id);
  const [section, setSection] = useState("Compliance");
  const [notice, setNotice] = useState("");
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("All applications");
  const [confirmations, setConfirmations] = useState<Record<string, boolean>>({});
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [sortNewest, setSortNewest] = useState(true);

  const pendingCount = applications.length;
  const selected = applications.find((application) => application.id === selectedId) ?? applications[0];
  const completeCount = selected.checks.filter(isComplete).length;
  const ready = completeCount === selected.checks.length;
  const filteredApplications = useMemo(() => {
    const normalized = search.trim().toLowerCase();
    return applications
      .filter((application) => !normalized || `${application.name} ${application.email} ${application.id}`.toLowerCase().includes(normalized))
      .filter((application) => filter === "All applications" || (filter === "Ready for decision" ? application.checks.every(isComplete) : !application.checks.every(isComplete)))
      .slice()
      .sort((a, b) => sortNewest ? applications.indexOf(a) - applications.indexOf(b) : applications.indexOf(b) - applications.indexOf(a));
  }, [applications, filter, search, sortNewest]);

  function flash(message: string) {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 2800);
  }

  function updateCheck(documentKey: DocumentKey, updates: Partial<DocumentCheck>) {
    setApplications((current) => current.map((application) => application.id !== selectedId ? application : {
      ...application,
      checks: application.checks.map((check) => check.key === documentKey ? { ...check, ...updates } : check),
    }));
  }

  function saveCheck(check: DocumentCheck) {
    if (check.status === "Not required" && !check.note.trim()) {
      flash("Add a reason before marking this document not required.");
      return;
    }
    flash(`${check.label} saved in this demo only.`);
  }

  function selectSection(label: string) {
    setSection(label);
    setMobileNavOpen(false);
  }

  return (
    <div className="wayfare-admin-current min-h-[100dvh] bg-[#f4f5ef] text-[#26342f]" style={{ fontFamily: '"Avenir Next", "Segoe UI", sans-serif' }}>
      <div className="min-h-[100dvh] xl:grid xl:grid-cols-[238px_minmax(0,1fr)]">
        <aside className={`${mobileNavOpen ? "block" : "hidden"} fixed inset-y-0 left-0 z-30 w-[270px] border-r border-[#dce3da] bg-[#fffefa] px-4 py-5 shadow-[12px_0_40px_rgba(38,52,47,.08)] xl:sticky xl:top-0 xl:block xl:h-[100dvh] xl:w-auto xl:shadow-none`}>
          <div className="flex items-center justify-between px-2">
            <div className="flex items-center gap-2.5">
              <span className="flex h-9 w-9 items-center justify-center rounded-[13px] bg-[#df704c] text-[#fff9ef]">
                <Activity size={19} strokeWidth={2.2} />
              </span>
              <div>
                <div className="text-[18px] font-bold tracking-[-.06em]">wayfare</div>
                <div className="-mt-0.5 text-[9px] font-semibold uppercase tracking-[.16em] text-[#879189]">Operations</div>
              </div>
            </div>
            <button aria-label="Close navigation" className="rounded-lg p-2 text-[#738078] hover:bg-[#f4f5ef] xl:hidden" onClick={() => setMobileNavOpen(false)}><X size={17} /></button>
          </div>
          <div className="mb-2 mt-9 px-3 text-[9px] font-bold uppercase tracking-[.16em] text-[#89938a]">Workspace</div>
          <nav aria-label="Admin sections" className="space-y-1">
            {adminSections.map(({ label, icon: Icon, count }) => {
              const active = section === label;
              return (
                <button key={label} onClick={() => selectSection(label)} aria-current={active ? "page" : undefined} className={`group flex min-h-[42px] w-full items-center gap-3 rounded-[11px] px-3 text-left text-[12px] transition duration-150 ${active ? "bg-[#df704c] font-semibold text-[#fff9ef] shadow-[0_5px_12px_rgba(183,89,59,.12)]" : "text-[#58665d] hover:bg-[#f3f5ef] hover:text-[#26342f]"}`}>
                  <Icon size={16} strokeWidth={active ? 2 : 1.8} />
                  <span className="flex-1">{label}</span>
                  {count ? <span className={`min-w-[22px] rounded-full px-1.5 py-1 text-center text-[9px] font-bold ${active ? "bg-[#fff9ef]/20 text-white" : "bg-[#f7e6de] text-[#a75439]"}`}>{pendingCount}</span> : null}
                </button>
              );
            })}
          </nav>
          <div className="absolute bottom-5 left-4 right-4 rounded-[14px] border border-[#e1e6dc] bg-[#f8f9f5] p-3.5">
            <div className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-[.1em] text-[#66736a]"><LockKeyhole size={13} /> Staff workspace</div>
            <p className="mb-0 mt-2 text-[10px] leading-[1.6] text-[#7b877e]">Use approved staff processes for original document inspection.</p>
          </div>
        </aside>
        {mobileNavOpen ? <button aria-label="Dismiss navigation" className="fixed inset-0 z-20 bg-[#26342f]/25 xl:hidden" onClick={() => setMobileNavOpen(false)} /> : null}

        <main className="min-w-0">
          <header className="sticky top-0 z-10 flex h-[61px] items-center justify-between border-b border-[#dce3da] bg-[#fffefa]/95 px-4 backdrop-blur-md sm:px-7">
            <div className="flex min-w-0 items-center gap-3">
              <button aria-label="Open navigation" className="rounded-lg p-2 text-[#536158] hover:bg-[#f3f5ef] xl:hidden" onClick={() => setMobileNavOpen(true)}><Menu size={18} /></button>
              <div className="truncate text-[11px] text-[#7c887e]"><span className="hidden sm:inline">Wayfare</span><span className="px-2 text-[#c2c9c0]">/</span>Operations<span className="px-2 text-[#c2c9c0]">/</span><span className="font-semibold text-[#34433a]">{section}</span></div>
            </div>
            <button onClick={() => flash("Signed in as Kea Maseko — sample workspace.")} className="flex items-center gap-2.5 rounded-xl border border-[#e0e5dc] bg-[#fffefa] py-1.5 pl-1.5 pr-2.5 text-[11px] font-semibold text-[#435047] transition hover:bg-[#f7f8f3]">
              <span className="flex h-7 w-7 items-center justify-center rounded-full bg-[#e5eee4] text-[9px] font-bold text-[#47624c]">KM</span>
              <span className="hidden sm:inline">Kea Maseko</span><ChevronDown size={13} />
            </button>
          </header>

          <div className="mx-auto max-w-[1480px] px-4 pb-12 pt-6 sm:px-7 lg:px-9">
            <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
              <div>
                <div className="mb-1.5 flex items-center gap-2 text-[9px] font-bold uppercase tracking-[.17em] text-[#859087]"><span className="h-px w-5 bg-[#df704c]" /> Operations desk</div>
                <h1 className="m-0 text-[30px] font-semibold leading-none tracking-[-.055em] text-[#26342f] sm:text-[34px]" style={{ fontFamily: 'Georgia, "Times New Roman", serif' }}>{section}.</h1>
                <p className="mb-0 mt-2 text-[11px] text-[#758178]">{section === "Compliance" ? "A clear view of checks still needed before a safe staff decision." : section === "Driver reviews" ? "Review applications and move each one to its next safe action." : "Wayfare operations workspace · sample data"}</p>
              </div>
              <div className="flex items-center gap-2">
                <span className="rounded-full border border-[#dce3da] bg-[#fffefa] px-3 py-1.5 text-[9px] font-semibold text-[#758178]"><span className="mr-1.5 inline-block h-1.5 w-1.5 rounded-full bg-[#78977c]" /> SAMPLE DATA</span>
                <button onClick={() => flash("This is a static sample workspace; no live records were refreshed.")} aria-label="Refresh sample data" className="flex h-8 items-center gap-2 rounded-[10px] border border-[#dce3da] bg-[#fffefa] px-2.5 text-[10px] font-semibold text-[#526057] hover:bg-[#f7f8f3]"><Activity size={13} /> <span className="hidden sm:inline">Demo snapshot</span></button>
              </div>
            </div>

            <div className="mb-5 flex items-start gap-3 rounded-[14px] border border-[#dfe7dc] bg-[#e8eee4] px-4 py-3.5 text-[10px] leading-[1.65] text-[#435349]">
              <ShieldCheck className="mt-0.5 shrink-0 text-[#5b7a61]" size={16} />
              <p className="m-0"><strong className="font-semibold text-[#35483a]">Safety comes before approval.</strong> Never approve a driver until identity, vehicle and applicable permit checks are completed independently. Wayfare does not store document images; inspect originals through your approved staff process.</p>
            </div>

            {section === "Compliance" || section === "Driver reviews" ? (
              <>
                <section aria-label="Review overview" className="mb-5 grid gap-3 sm:grid-cols-3">
                  <div className="flex items-center gap-3 rounded-[15px] border border-[#dce3da] bg-[#fffefa] px-4 py-3.5">
                    <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#f9eee7] text-[#bd6545]"><Clock3 size={17} /></span>
                    <div><div className="text-[20px] font-semibold leading-none tracking-[-.04em]">{pendingCount}</div><div className="mt-1 text-[9px] font-medium text-[#7b877e]">Applications awaiting review</div></div>
                    <span className="ml-auto rounded-full bg-[#fbf0e6] px-2 py-1 text-[8px] font-bold uppercase tracking-[.07em] text-[#9a5d32]">Pending</span>
                  </div>
                  <div className="flex items-center gap-3 rounded-[15px] border border-[#dce3da] bg-[#fffefa] px-4 py-3.5">
                    <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#e9f2e9] text-[#55765d]"><FileCheck2 size={17} /></span>
                    <div><div className="text-[20px] font-semibold leading-none tracking-[-.04em]">{applications.filter((application) => application.checks.every(isComplete)).length}</div><div className="mt-1 text-[9px] font-medium text-[#7b877e]">Ready for staff decision</div></div>
                    <span className="ml-auto text-[9px] font-semibold text-[#69816e]">of {pendingCount}</span>
                  </div>
                  <div className="flex items-center gap-3 rounded-[15px] border border-[#dce3da] bg-[#fffefa] px-4 py-3.5">
                    <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#f5eee2] text-[#9b7740]"><CircleAlert size={17} /></span>
                    <div><div className="text-[20px] font-semibold leading-none tracking-[-.04em]">{applications.reduce((total, application) => total + application.checks.filter((check) => !isComplete(check)).length, 0)}</div><div className="mt-1 text-[9px] font-medium text-[#7b877e]">Checks outstanding</div></div>
                    <span className="ml-auto text-[9px] font-semibold text-[#917648]">Across queue</span>
                  </div>
                </section>

                <div className="grid items-start gap-4 xl:grid-cols-[minmax(275px,340px)_minmax(0,1fr)]">
                  <section className="overflow-hidden rounded-[18px] border border-[#dce3da] bg-[#fffefa]">
                    <div className="border-b border-[#e6e9e2] px-4 pb-3 pt-4">
                      <div className="flex items-center justify-between gap-2">
                        <div><h2 className="m-0 text-[14px] font-semibold tracking-[-.025em]">Review queue</h2><p className="mb-0 mt-1 text-[9px] text-[#859087]">{pendingCount} applications · all retained in sample</p></div>
                        <button onClick={() => setSortNewest((current) => !current)} aria-label="Toggle queue order" className="rounded-lg border border-[#e2e7df] p-2 text-[#68756c] hover:bg-[#f5f7f2]"><ArrowDownUp size={13} /></button>
                      </div>
                      <label className="mt-3 flex h-9 items-center gap-2 rounded-[10px] border border-[#e0e5dc] bg-[#fafbf8] px-2.5 text-[#8b958d]">
                        <Search size={13} /><input value={search} onChange={(event) => setSearch(event.target.value)} aria-label="Search applications" placeholder="Search name or reference" className="min-w-0 flex-1 border-0 bg-transparent text-[10px] text-[#26342f] outline-none placeholder:text-[#9aa39b]" />
                        {search ? <button aria-label="Clear search" onClick={() => setSearch("")} className="text-[#89938a]"><X size={13} /></button> : null}
                      </label>
                      <div className="mt-2 flex gap-1.5">
                        {["All applications", "Needs checks", "Ready for decision"].map((option) => (
                          <button key={option} onClick={() => setFilter(option)} className={`rounded-full px-2.5 py-1.5 text-[8px] font-semibold transition ${filter === option ? "bg-[#e8eee4] text-[#49614e]" : "text-[#818d83] hover:bg-[#f3f5ef]"}`}>{option}</button>
                        ))}
                      </div>
                    </div>
                    <div className="max-h-[560px] overflow-y-auto p-2">
                      {filteredApplications.length ? filteredApplications.map((application) => {
                        const count = formatDocumentCount(application);
                        const active = application.id === selectedId;
                        const done = count === application.checks.length;
                        return (
                          <button key={application.id} onClick={() => setSelectedId(application.id)} className={`mb-1 w-full rounded-[13px] border p-3 text-left transition duration-150 ${active ? "border-[#e5b49f] bg-[#fcf4ef] shadow-[0_2px_8px_rgba(73,64,48,.04)]" : "border-transparent bg-transparent hover:border-[#e4e8e0] hover:bg-[#f8f9f5]"}`}>
                            <div className="flex items-start gap-2.5">
                              <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-[10px] font-bold ${active ? "bg-[#df704c] text-[#fff9ef]" : "bg-[#e9eee6] text-[#526a56]"}`}>{application.initials}</span>
                              <span className="min-w-0 flex-1">
                                <span className="flex items-center justify-between gap-2"><span className="truncate text-[11px] font-semibold text-[#34433a]">{application.name}</span><ChevronRight size={13} className={active ? "text-[#bd6849]" : "text-[#a0aaa1]"} /></span>
                                <span className="mt-1 block truncate text-[9px] text-[#839087]">{application.id} <span className="px-1">·</span> {application.submitted}</span>
                                <span className="mt-2 flex items-center justify-between gap-2">
                                  <span className="flex items-center gap-1.5 text-[9px] font-medium text-[#738078]"><span className="flex gap-[3px]">{application.checks.map((check) => <i key={check.key} className={`h-1.5 w-3 rounded-sm ${isComplete(check) ? "bg-[#83a58a]" : check.status === "Needs follow-up" ? "bg-[#d3a16c]" : "bg-[#e4e4d9]"}`} />)}</span>{count}/4 checks</span>
                                  <span className={`rounded-full px-2 py-1 text-[8px] font-semibold ${done ? "bg-[#e9f2e9] text-[#4c7053]" : "bg-[#f7f1e7] text-[#8a6e3e]"}`}>{done ? "Ready" : "In review"}</span>
                                </span>
                              </span>
                            </div>
                          </button>
                        );
                      }) : (
                        <div className="px-5 py-10 text-center">
                          <span className="mx-auto flex h-10 w-10 items-center justify-center rounded-full bg-[#f0f3ec] text-[#7d8a7e]"><Search size={16} /></span>
                          <p className="mb-0 mt-3 text-[11px] font-semibold text-[#445248]">No applications match</p>
                          <p className="mb-0 mt-1 text-[9px] text-[#879189]">Try a different search or filter.</p>
                          <button onClick={() => { setSearch(""); setFilter("All applications"); }} className="mt-3 text-[9px] font-semibold text-[#a8573c] hover:underline">Clear search and filters</button>
                        </div>
                      )}
                    </div>
                    <div className="flex items-center gap-2 border-t border-[#e6e9e2] px-4 py-3 text-[9px] leading-[1.55] text-[#879189]"><Info size={13} className="shrink-0 text-[#819181]" />Applications stay visible until a staff member makes a final decision.</div>
                  </section>

                  <section className="min-w-0 overflow-hidden rounded-[18px] border border-[#dce3da] bg-[#fffefa]">
                    <div className="border-b border-[#e6e9e2] px-4 py-4 sm:px-5">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div className="flex items-center gap-3">
                          <span className="flex h-11 w-11 items-center justify-center rounded-full bg-[#e8eee4] text-[12px] font-bold text-[#4b6450]">{selected.initials}</span>
                          <div>
                            <div className="flex flex-wrap items-center gap-2"><h2 className="m-0 text-[16px] font-semibold tracking-[-.03em]">{selected.name}</h2><span className="rounded-full bg-[#f7f1e7] px-2 py-1 text-[8px] font-bold uppercase tracking-[.05em] text-[#8a6e3e]">Pending staff decision</span></div>
                            <p className="mb-0 mt-1 text-[9px] text-[#7e8a81]">{selected.email} <span className="px-1">·</span>{selected.id}</p>
                          </div>
                        </div>
                        <button onClick={() => flash("Application details are shown in this sample review.")} className="inline-flex items-center gap-1.5 rounded-lg border border-[#e0e5dc] px-2.5 py-2 text-[9px] font-semibold text-[#647168] hover:bg-[#f7f8f3]">Application details <ArrowRight size={12} /></button>
                      </div>
                      <div className="mt-4 grid gap-2 sm:grid-cols-3">
                        <div className="rounded-[10px] bg-[#f7f8f4] px-3 py-2"><div className="text-[8px] font-bold uppercase tracking-[.09em] text-[#89938a]">Area</div><div className="mt-1 text-[10px] font-medium text-[#46544b]">{selected.location}</div></div>
                        <div className="rounded-[10px] bg-[#f7f8f4] px-3 py-2"><div className="text-[8px] font-bold uppercase tracking-[.09em] text-[#89938a]">Vehicle</div><div className="mt-1 truncate text-[10px] font-medium text-[#46544b]">{selected.vehicle || "Awaiting details"}</div></div>
                        <div className="rounded-[10px] bg-[#f7f8f4] px-3 py-2"><div className="text-[8px] font-bold uppercase tracking-[.09em] text-[#89938a]">Registration</div><div className="mt-1 text-[10px] font-medium text-[#46544b]">{selected.plate || "Awaiting details"}</div></div>
                      </div>
                      {(!selected.vehicle || !selected.plate) ? <p className="mb-0 mt-2 flex items-center gap-1.5 text-[9px] text-[#8b7654]"><Mail size={12} /> Vehicle and number-plate details will arrive by email.</p> : null}
                    </div>

                    <div className="px-4 py-4 sm:px-5">
                      <div className="mb-3 flex flex-wrap items-end justify-between gap-2">
                        <div><h3 className="m-0 text-[13px] font-semibold tracking-[-.02em]">Document checks</h3><p className="mb-0 mt-1 text-[9px] text-[#879189]">Record the outcome from your approved off-platform review.</p></div>
                        <div className="flex items-center gap-2"><div className="h-1.5 w-20 overflow-hidden rounded-full bg-[#ecefe8] sm:w-28"><div className="h-full rounded-full bg-[#78977c] transition-all duration-300" style={{ width: `${completeCount / 4 * 100}%` }} /></div><span className="text-[9px] font-semibold text-[#68756c]">{completeCount}/4 complete</span></div>
                      </div>
                      <div className="space-y-2.5">
                        {selected.checks.map((check, index) => (
                          <article key={check.key} className="rounded-[13px] border border-[#e4e8e0] bg-[#fdfdfa] p-3 sm:p-3.5">
                            <div className="flex flex-wrap items-center justify-between gap-2">
                              <div className="flex items-center gap-2">
                                <span className={`flex h-6 w-6 items-center justify-center rounded-full text-[9px] font-bold ${isComplete(check) ? "bg-[#e6efe5] text-[#55765d]" : check.status === "Needs follow-up" ? "bg-[#fbefe4] text-[#9a5d32]" : "bg-[#f0f1eb] text-[#818b81]"}`}>{isComplete(check) ? <Check size={12} /> : String(index + 1).padStart(2, "0")}</span>
                                <h4 className="m-0 text-[11px] font-semibold text-[#3b4940]">{check.label}</h4>
                              </div>
                              <span className={`rounded-full border px-2 py-1 text-[8px] font-semibold ${statusStyle(check.status)}`}>{check.status}</span>
                            </div>
                            <div className="mt-3 grid gap-2 lg:grid-cols-[minmax(0,1fr)_150px_minmax(150px,.9fr)_auto] lg:items-end">
                              <label className="block text-[8px] font-semibold text-[#77837a]">Review status
                                <select value={check.status} onChange={(event) => updateCheck(check.key, { status: event.target.value as Status })} className="mt-1 block h-9 w-full rounded-[9px] border border-[#dfe4dc] bg-[#fffefa] px-2.5 text-[10px] font-medium text-[#47544b] outline-none transition focus:border-[#d98c6d]">
                                  {statusOptions.map((option) => <option key={option}>{option}</option>)}
                                </select>
                              </label>
                              <label className="block text-[8px] font-semibold text-[#77837a]">Expiry date <span className="font-normal text-[#9ba39b]">{check.key === "identity" ? "· not applicable" : "· YYYY-MM-DD"}</span>
                                <input disabled={check.key === "identity"} type={check.key === "identity" ? "text" : "date"} value={check.key === "identity" ? "Not applicable" : check.expiry} onChange={(event) => updateCheck(check.key, { expiry: event.target.value })} className="mt-1 block h-9 w-full rounded-[9px] border border-[#dfe4dc] bg-[#fffefa] px-2.5 text-[10px] font-medium text-[#47544b] outline-none transition focus:border-[#d98c6d] disabled:bg-[#f3f4ef] disabled:text-[#969f96]" />
                              </label>
                              <label className="block text-[8px] font-semibold text-[#77837a]">{check.status === "Not required" ? "Reason required" : "Staff note"}
                                <input value={check.note} onChange={(event) => updateCheck(check.key, { note: event.target.value })} placeholder={check.status === "Not required" ? "Explain why it does not apply" : "Add a brief check note"} className="mt-1 block h-9 w-full rounded-[9px] border border-[#dfe4dc] bg-[#fffefa] px-2.5 text-[10px] font-medium text-[#47544b] outline-none transition placeholder:font-normal placeholder:text-[#a3aba3] focus:border-[#d98c6d]" />
                              </label>
                              <button onClick={() => saveCheck(check)} className="inline-flex h-9 items-center justify-center gap-1.5 rounded-[9px] bg-[#df704c] px-3 text-[9px] font-semibold text-[#fff9ef] transition hover:bg-[#cc6544] active:scale-[.98]"><Check size={12} /> Save check</button>
                            </div>
                            {check.status === "Not required" ? <p className={`mb-0 mt-2 text-[8px] ${check.note.trim() ? "text-[#738078]" : "text-[#9c4036]"}`}>{check.note.trim() ? "Reason recorded for this not-required decision." : "A staff reason is required before this check can count as complete."}</p> : null}
                          </article>
                        ))}
                      </div>
                    </div>

                    <div className="border-t border-[#e6e9e2] bg-[#fafbf8] px-4 py-4 sm:px-5">
                      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                        <div><h3 className="m-0 text-[12px] font-semibold">Final application decision</h3><p className="mb-0 mt-1 text-[9px] text-[#879189]">A staff member must take this action separately from compliance checks.</p></div>
                        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[8px] font-semibold ${ready ? "bg-[#e9f2e9] text-[#4d7254]" : "bg-[#f7f1e7] text-[#8b7045]"}`}><span className={`h-1.5 w-1.5 rounded-full ${ready ? "bg-[#719078]" : "bg-[#c3985d]"}`} />{ready ? "Checks complete" : `${4 - completeCount} checks still needed`}</span>
                      </div>
                      <label className={`flex cursor-pointer items-start gap-2.5 rounded-[11px] border px-3 py-2.5 text-[9px] leading-[1.6] transition ${ready ? "border-[#dce3da] bg-[#fffefa] hover:border-[#c8d6c8]" : "cursor-not-allowed border-[#e6e8e2] bg-[#f4f5f1] text-[#919991]"}`}>
                        <input type="checkbox" disabled={!ready} checked={confirmations[selected.id] ?? false} onChange={(event) => setConfirmations((current) => ({ ...current, [selected.id]: event.target.checked }))} className="mt-0.5 accent-[#df704c] disabled:opacity-40" />
                        <span><strong className="font-semibold">I completed the off-platform identity and legal checks and inspected the original documents through the approved process.</strong><span className="mt-0.5 block text-[#7d887f]">This confirmation is required before a manual approval action can be taken.</span></span>
                      </label>
                      {!ready ? <p className="mb-0 mt-2 flex items-center gap-1.5 text-[9px] text-[#8b7654]"><Info size={12} /> Complete all four checks before staff approval is enabled.</p> : null}
                      <div className="mt-3 flex flex-wrap items-center gap-2">
                        <button disabled={!ready || !confirmations[selected.id]} onClick={() => flash("Demo only — no approval was submitted and this application remains in the queue.")} className="inline-flex min-h-9 items-center gap-2 rounded-[10px] bg-[#df704c] px-3.5 text-[10px] font-semibold text-[#fff9ef] transition hover:bg-[#cb6545] disabled:cursor-not-allowed disabled:bg-[#e4e7e1] disabled:text-[#929b92]"><CheckCheck size={14} /> Approve application</button>
                        <button onClick={() => flash("Demo only — follow-up has not been sent.")} className="inline-flex min-h-9 items-center gap-2 rounded-[10px] border border-[#dce3da] bg-[#fffefa] px-3 text-[9px] font-semibold text-[#526057] hover:bg-[#f4f6f1]"><MessageSquare size={13} /> Request follow-up</button>
                        <span className="ml-auto flex items-center gap-1.5 text-[8px] text-[#89938a]"><LockKeyhole size={11} /> Checks alone never approve</span>
                      </div>
                      <div className="mt-3 flex items-start gap-2 border-t border-[#e7eae4] pt-3 text-[8px] leading-[1.55] text-[#89938a]"><Info size={12} className="mt-px shrink-0" />This is a visual mockup with sample records. Buttons provide local demo feedback only; no data is sent or persisted.</div>
                    </div>
                  </section>
                </div>
              </>
            ) : (
              <section className="rounded-[18px] border border-[#dce3da] bg-[#fffefa] p-5 sm:p-7">
                <div className="flex items-start gap-3">
                  <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#e8eee4] text-[#55765d]"><BadgeCheck size={18} /></span>
                  <div><h2 className="m-0 text-[15px] font-semibold">{section}</h2><p className="mb-0 mt-1 text-[10px] text-[#7c887f]">This area is represented in the Wayfare operations navigation.</p></div>
                </div>
                <div className="mt-5 rounded-[13px] border border-dashed border-[#dce3da] bg-[#fafbf8] px-5 py-8 text-center">
                  <span className="mx-auto flex h-10 w-10 items-center justify-center rounded-full bg-[#eef2e9] text-[#738078]"><MoreHorizontal size={18} /></span>
                  <h3 className="mb-0 mt-3 text-[11px] font-semibold text-[#46544b]">Sample workspace view</h3>
                  <p className="mx-auto mb-0 mt-1 max-w-[340px] text-[9px] leading-5 text-[#879189]">This standalone compliance mockup keeps the full admin navigation visible. The selected section is a local preview, not a connected operations tool.</p>
                  <button onClick={() => setSection("Compliance")} className="mt-4 inline-flex items-center gap-1.5 rounded-lg bg-[#df704c] px-3 py-2 text-[9px] font-semibold text-white hover:bg-[#cb6545]"><ChevronLeft size={13} /> Return to compliance</button>
                </div>
              </section>
            )}

            <footer className="mt-7 flex flex-wrap items-center justify-between gap-2 border-t border-[#dce3da] pt-3 text-[8px] text-[#8b958d]">
              <span>Wayfare operations <span className="px-1">·</span> South Africa</span>
              <span>Sample review data <span className="px-1">·</span> No documents stored</span>
            </footer>
          </div>
        </main>
      </div>

      {notice ? <div role="status" className="fixed bottom-4 right-4 z-50 flex max-w-[calc(100vw-2rem)] items-start gap-2.5 rounded-[12px] border border-[#43534a] bg-[#26342f] px-4 py-3 text-[10px] font-medium leading-[1.5] text-[#f8f8f2] shadow-[0_12px_30px_rgba(38,52,47,.2)]"><CircleCheck className="mt-0.5 shrink-0 text-[#a9c4a8]" size={15} /><span>{notice}</span><button aria-label="Dismiss message" className="ml-1 text-[#bcc7bd] hover:text-white" onClick={() => setNotice("")}><X size={13} /></button></div> : null}
    </div>
  );
}

export default Enhanced;
