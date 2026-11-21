import { useMemo, useState } from "react";
import {
  Activity,
  ArrowDownLeft,
  ArrowUpRight,
  Bell,
  Check,
  ChevronDown,
  Clock3,
  Compass,
  MapPin,
  Menu,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Users,
  X,
} from "lucide-react";

type RideStatus = "Needs driver" | "Assigned" | "Review";
type RideRequest = {
  id: string;
  requester: string;
  initials: string;
  pickup: string;
  dropoff: string;
  time: string;
  fare: number;
  status: RideStatus;
  note: string;
  requesterTrips: number;
  requesterRating: string;
  preferredDriver?: string;
};

const initialRequests: RideRequest[] = [
  {
    id: "WF-2841",
    requester: "Lerato Mokoena",
    initials: "LM",
    pickup: "Rosebank Mall, Rosebank",
    dropoff: "OR Tambo International",
    time: "08:42",
    fare: 386.5,
    status: "Needs driver",
    note: "Two bags · airport trip",
    requesterTrips: 18,
    requesterRating: "4.9",
  },
  {
    id: "WF-2838",
    requester: "Thabo Ndlovu",
    initials: "TN",
    pickup: "Braamfontein, Johannesburg",
    dropoff: "Sandton City",
    time: "08:38",
    fare: 174,
    status: "Needs driver",
    note: "Pickup at the east entrance",
    requesterTrips: 7,
    requesterRating: "4.8",
  },
  {
    id: "WF-2833",
    requester: "Amara Jacobs",
    initials: "AJ",
    pickup: "Claremont, Cape Town",
    dropoff: "Cape Town International",
    time: "08:31",
    fare: 248.5,
    status: "Assigned",
    note: "Driver confirmed · 4 min away",
    requesterTrips: 42,
    requesterRating: "5.0",
    preferredDriver: "Sipho Dlamini",
  },
  {
    id: "WF-2829",
    requester: "Zanele Khumalo",
    initials: "ZK",
    pickup: "Umhlanga Ridge, Durban",
    dropoff: "King Shaka International",
    time: "08:24",
    fare: 312,
    status: "Review",
    note: "First ride · verify pickup details",
    requesterTrips: 0,
    requesterRating: "New",
  },
];

const drivers = [
  { name: "Sipho Dlamini", initials: "SD", car: "Toyota Corolla · CA 482-119", area: "Rosebank", eta: "3 min", rating: "4.9", rides: 284 },
  { name: "Nomsa Mthembu", initials: "NM", car: "VW Polo · GP 774-208", area: "Sandton", eta: "6 min", rating: "4.8", rides: 192 },
  { name: "Aiden Petersen", initials: "AP", car: "Suzuki Swift · CA 118-605", area: "Claremont", eta: "8 min", rating: "4.9", rides: 317 },
];

type ReviewCheckStatus = "Reviewed off-platform" | "Needs follow-up" | "Pending" | "New account";
type PersonRecord = {
  name: string;
  initials: string;
  type: "Requester" | "Driver";
  detail: string;
  area: string;
  signal: string;
  joined: string;
  lastActive: string;
  needsReview: boolean;
  vehicle?: string;
  reviewChecks?: { label: string; status: ReviewCheckStatus }[];
};

const people: PersonRecord[] = [
  { name: "Lerato Mokoena", initials: "LM", type: "Requester", detail: "18 rides · 4.9 rating", area: "Rosebank, Johannesburg", signal: "In good standing", joined: "February 2025", lastActive: "Today · 08:42", needsReview: false },
  { name: "Thabo Ndlovu", initials: "TN", type: "Requester", detail: "7 rides · 4.8 rating", area: "Braamfontein, Johannesburg", signal: "In good standing", joined: "November 2025", lastActive: "Today · 08:38", needsReview: false },
  { name: "Zanele Khumalo", initials: "ZK", type: "Requester", detail: "First ride · new account", area: "Umhlanga Ridge, Durban", signal: "First-ride check", joined: "Today", lastActive: "First request · 08:24", needsReview: true, reviewChecks: [{ label: "Account details", status: "New account" }, { label: "Pickup confirmation", status: "Needs follow-up" }] },
  { name: "Sipho Dlamini", initials: "SD", type: "Driver", detail: "284 rides · 4.9 rating", area: "Rosebank, Johannesburg", signal: "Verified off-platform", joined: "June 2024", lastActive: "Online · 08:40", needsReview: false, vehicle: "Toyota Corolla · CA 482-119", reviewChecks: [{ label: "Identity & licence", status: "Reviewed off-platform" }, { label: "Vehicle registration", status: "Reviewed off-platform" }, { label: "Operating permit", status: "Reviewed off-platform" }] },
  { name: "Nomsa Mthembu", initials: "NM", type: "Driver", detail: "192 rides · 4.8 rating", area: "Sandton, Johannesburg", signal: "Permit needs review", joined: "August 2024", lastActive: "Online · 08:10", needsReview: true, vehicle: "VW Polo · GP 774-208", reviewChecks: [{ label: "Identity & licence", status: "Reviewed off-platform" }, { label: "Vehicle registration", status: "Reviewed off-platform" }, { label: "Operating permit", status: "Needs follow-up" }] },
  { name: "Aiden Petersen", initials: "AP", type: "Driver", detail: "317 rides · 4.9 rating", area: "Claremont, Cape Town", signal: "Verified off-platform", joined: "March 2024", lastActive: "Online · 08:27", needsReview: false, vehicle: "Suzuki Swift · CA 118-605", reviewChecks: [{ label: "Identity & licence", status: "Reviewed off-platform" }, { label: "Vehicle registration", status: "Reviewed off-platform" }, { label: "Operating permit", status: "Reviewed off-platform" }] },
];

function formatRand(amount: number) {
  return new Intl.NumberFormat("en-ZA", {
    style: "currency",
    currency: "ZAR",
    maximumFractionDigits: amount % 1 === 0 ? 0 : 2,
  }).format(amount);
}

function Avatar({ initials, tone = "sage", size = "md" }: { initials: string; tone?: "sage" | "coral" | "ink"; size?: "sm" | "md" | "lg" }) {
  const colors = {
    sage: "bg-[#e5ece2] text-[#3c5d49]",
    coral: "bg-[#fae7de] text-[#a94c35]",
    ink: "bg-[#e7e8e2] text-[#39443c]",
  };
  const sizes = { sm: "h-8 w-8 text-[10px]", md: "h-10 w-10 text-[11px]", lg: "h-12 w-12 text-xs" };
  return <span className={`inline-flex shrink-0 items-center justify-center rounded-full font-semibold tracking-[.04em] ${colors[tone]} ${sizes[size]}`}>{initials}</span>;
}

function StatusPill({ status }: { status: RideStatus }) {
  const styles = status === "Needs driver"
    ? "bg-[#fae8df] text-[#a44c34]"
    : status === "Assigned"
      ? "bg-[#e5eee5] text-[#41634b]"
      : "bg-[#f4efdd] text-[#8a7131]";
  return <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-semibold tracking-[.02em] ${styles}`}><span className={`h-1.5 w-1.5 rounded-full ${status === "Needs driver" ? "bg-[#df7455]" : status === "Assigned" ? "bg-[#6a9670]" : "bg-[#c6a84e]"}`} />{status}</span>;
}

export function AdminConsole() {
  const [requests, setRequests] = useState(initialRequests);
  const [activeSection, setActiveSection] = useState<"Requests" | "People">("People");
  const [selectedId, setSelectedId] = useState("WF-2841");
  const [filter, setFilter] = useState<"All requests" | "Needs driver" | "Assigned">("All requests");
  const [search, setSearch] = useState("");
  const [driverChoice, setDriverChoice] = useState(drivers[0].name);
  const [toast, setToast] = useState("");
  const [reviewed, setReviewed] = useState<string[]>([]);
  const [selectedPerson, setSelectedPerson] = useState("Nomsa Mthembu");
  const [peopleSearch, setPeopleSearch] = useState("");
  const [peopleFilter, setPeopleFilter] = useState<"Everyone" | "Drivers" | "Requesters" | "Needs review">("Everyone");
  const [reviewedPeople, setReviewedPeople] = useState<string[]>([]);
  const [followUpPeople, setFollowUpPeople] = useState<string[]>([]);
  const selected = requests.find((ride) => ride.id === selectedId) ?? requests[0];
  const filteredRequests = useMemo(() => requests.filter((ride) => {
    const matchesFilter = filter === "All requests" || ride.status === filter;
    const matchesSearch = `${ride.requester} ${ride.pickup} ${ride.dropoff} ${ride.id}`.toLowerCase().includes(search.toLowerCase());
    return matchesFilter && matchesSearch;
  }), [filter, requests, search]);
  const filteredPeople = useMemo(() => people.filter((entry) => {
    const matchesType = peopleFilter === "Everyone" || peopleFilter === "Needs review" || entry.type === (peopleFilter === "Drivers" ? "Driver" : "Requester");
    const matchesReview = peopleFilter !== "Needs review" || (entry.needsReview && !reviewedPeople.includes(entry.name));
    const matchesSearch = `${entry.name} ${entry.detail} ${entry.area} ${entry.signal}`.toLowerCase().includes(peopleSearch.toLowerCase());
    return matchesType && matchesReview && matchesSearch;
  }), [peopleFilter, peopleSearch, reviewedPeople]);
  const person = people.find((entry) => entry.name === selected.requester);
  const selectedRecord = people.find((entry) => entry.name === selectedPerson) ?? people[0];
  const selectedPersonRide = requests.find((ride) => ride.requester === selectedRecord.name || ride.preferredDriver === selectedRecord.name);
  const selectedPersonReviewed = reviewedPeople.includes(selectedRecord.name);
  const selectedPersonFollowUp = followUpPeople.includes(selectedRecord.name);
  const pendingPeopleReviews = people.filter((entry) => entry.needsReview && !reviewedPeople.includes(entry.name)).length;

  function assignDriver() {
    if (!selected || selected.status === "Assigned") return;
    setRequests((current) => current.map((ride) => ride.id === selected.id
      ? { ...ride, status: "Assigned", preferredDriver: driverChoice, note: "Driver confirmed · heading to pickup" }
      : ride));
    setToast(`${driverChoice} assigned to ${selected.id}`);
    window.setTimeout(() => setToast(""), 3200);
  }

  function markReviewed() {
    if (!reviewed.includes(selected.id)) setReviewed((current) => [...current, selected.id]);
    if (selected.status === "Review") {
      setRequests((current) => current.map((ride) => ride.id === selected.id
        ? { ...ride, status: "Needs driver", note: "Details reviewed · ready for dispatch" }
        : ride));
    }
    setToast(`${selected.requester}'s record reviewed`);
    window.setTimeout(() => setToast(""), 3200);
  }

  function markPersonReviewed() {
    if (!reviewedPeople.includes(selectedRecord.name)) {
      setReviewedPeople((current) => [...current, selectedRecord.name]);
    }
    setFollowUpPeople((current) => current.filter((name) => name !== selectedRecord.name));
    if (peopleFilter === "Needs review") {
      const nextRecord = people.find((entry) => entry.needsReview && entry.name !== selectedRecord.name && !reviewedPeople.includes(entry.name));
      if (nextRecord) setSelectedPerson(nextRecord.name);
      else setPeopleFilter("Everyone");
    }
    setToast(`${selectedRecord.name}'s demo review marked complete`);
    window.setTimeout(() => setToast(""), 3200);
  }

  function flagPersonForFollowUp() {
    setFollowUpPeople((current) => current.includes(selectedRecord.name) ? current : [...current, selectedRecord.name]);
    setToast(`${selectedRecord.name} flagged for follow-up in this demo`);
    window.setTimeout(() => setToast(""), 3200);
  }

  return (
    <div className="min-h-screen bg-[#f3f2eb] text-[#252d27] [font-family:'DM_Sans',sans-serif]">
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Manrope:wght@500;600;700;800&display=swap');
        .wayfare-scroll::-webkit-scrollbar{width:5px}.wayfare-scroll::-webkit-scrollbar-thumb{background:#d8d9cf;border-radius:8px}
        button:focus-visible,input:focus-visible,select:focus-visible{outline:3px solid rgba(233,107,67,.34);outline-offset:2px}
        @media (prefers-reduced-motion:no-preference){.wf-enter{animation:wfIn .38s ease-out both}@keyframes wfIn{from{opacity:.4;transform:translateY(5px)}to{opacity:1;transform:translateY(0)}}}
      `}</style>
      <div className="flex min-h-screen">
        <aside className="hidden w-[232px] shrink-0 flex-col bg-[#26362e] px-5 py-6 text-[#f6f3e9] lg:flex">
          <div className="mb-10 flex items-center gap-3 px-1">
            <div className="rounded-xl bg-[#f5f3ec] px-2.5 py-1.5">
              <img src="/__mockup/images/vhaya-logo.svg" alt="Vhaya" className="h-8 w-[138px] object-contain object-left" />
            </div>
          </div>
          <div className="mb-3 px-3 text-[9px] font-bold uppercase tracking-[.2em] text-[#9caa9f]">Operations desk</div>
          <nav className="space-y-1" aria-label="Operations sections">
            <button onClick={() => setActiveSection("Requests")} className={`flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left text-[13px] transition ${activeSection === "Requests" ? "bg-[#394d41] font-semibold text-white" : "text-[#c1cbc2] hover:bg-[#31443a] hover:text-white"}`}>
              <Activity size={16} strokeWidth={1.8} /> Ride requests
              <span className="ml-auto rounded-full bg-[#e96b43] px-2 py-0.5 text-[10px] font-bold text-white">{requests.filter((r) => r.status === "Needs driver").length}</span>
            </button>
              <button onClick={() => setActiveSection("People")} className={`flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left text-[13px] transition ${activeSection === "People" ? "bg-[#394d41] font-semibold text-white" : "text-[#c1cbc2] hover:bg-[#31443a] hover:text-white"}`}>
               <Users size={16} strokeWidth={1.8} /> People &amp; reviews
            </button>
          </nav>
          <div className="mt-auto rounded-2xl border border-white/10 bg-white/[.045] p-4">
            <div className="mb-2 flex items-center gap-2 text-[11px] font-semibold text-[#e8ede7]"><span className="h-2 w-2 rounded-full bg-[#8bac80]" /> Johannesburg desk</div>
            <div className="text-[11px] leading-5 text-[#aebbb0]">Morning shift · 07:00–15:00</div>
            <div className="mt-3 border-t border-white/10 pt-3 text-[10px] text-[#aebbb0]">Demo workspace · South Africa</div>
          </div>
        </aside>

        <main className="min-w-0 flex-1">
          <header className="sticky top-0 z-20 border-b border-[#e5e3d9] bg-[#f7f6f0]/95 backdrop-blur">
            <div className="mx-auto flex h-[70px] max-w-[1560px] items-center justify-between px-4 sm:px-7 xl:px-10">
              <div className="flex items-center gap-3">
                <button className="rounded-lg p-2 text-[#667168] hover:bg-[#ebeae2] lg:hidden" onClick={() => setActiveSection(activeSection === "Requests" ? "People" : "Requests")} aria-label="Switch section"><Menu size={19} /></button>
                <div className="lg:hidden"><img src="/__mockup/images/vhaya-logo.svg" alt="Vhaya" className="h-7 w-24 object-contain object-left" /></div>
                <div className="hidden lg:block">
                   <div className="text-[10px] font-semibold uppercase tracking-[.18em] text-[#81887f]">Tuesday, 20 October 2026 <span className="mx-1.5 text-[#c1c3b9]">/</span> Johannesburg</div>
                  <div className="mt-0.5 text-[13px] font-semibold text-[#303a32]">Morning operations</div>
                </div>
                <div className="lg:hidden text-[13px] font-semibold">{activeSection === "Requests" ? "Ride requests" : "People & reviews"}</div>
              </div>
              <div className="flex items-center gap-3 sm:gap-5">
                <span className="hidden items-center gap-2 rounded-full border border-[#e3e2d8] bg-[#fbfaf5] px-3 py-1.5 text-[10px] font-medium text-[#68736a] sm:inline-flex"><span className="h-1.5 w-1.5 rounded-full bg-[#77a17b]" /> Desk active</span>
                <button className="relative rounded-full p-2 text-[#727b72] transition hover:bg-[#ecebe3]" aria-label="Notifications"><Bell size={17} strokeWidth={1.7} /><span className="absolute right-1.5 top-1.5 h-1.5 w-1.5 rounded-full bg-[#e96b43]" /></button>
                <div className="flex items-center gap-2.5 border-l border-[#e3e2d8] pl-3 sm:pl-5">
                  <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[#dfe8dc] text-[10px] font-bold text-[#3f5946]">KM</div>
                  <div className="hidden sm:block"><div className="text-[11px] font-semibold">Kea Maseko</div><div className="text-[10px] text-[#81887f]">Dispatcher</div></div>
                </div>
              </div>
            </div>
          </header>

          <div className="mx-auto max-w-[1560px] px-4 pb-10 pt-6 sm:px-7 sm:pt-8 xl:px-10">
            <div className="mb-6 flex flex-col justify-between gap-5 xl:flex-row xl:items-end">
              <div>
                <div className="mb-2 inline-flex items-center gap-2 rounded-full bg-[#e7ece3] px-2.5 py-1 text-[9px] font-bold uppercase tracking-[.14em] text-[#4f6952]"><Compass size={11} /> {activeSection === "Requests" ? "Live desk snapshot" : "People review queue"} <span className="font-medium normal-case tracking-normal text-[#849181]">· demo data</span></div>
                <h1 className="font-['Manrope',sans-serif] text-[26px] font-bold tracking-[-.045em] text-[#26342b] sm:text-[31px]">{activeSection === "Requests" ? "Keep the city moving." : "People behind every ride."}</h1>
                <p className="mt-1 text-[12px] text-[#747c73]">{activeSection === "Requests" ? "A clear view of today’s requests, people and next steps." : "Review driver readiness and requester history in one place."}</p>
              </div>
              <div className="grid grid-cols-3 gap-2 sm:gap-3 xl:w-[510px]">
                <div className="rounded-2xl border border-[#e4e2d8] bg-[#fbfaf5] px-3 py-3 sm:px-4">
                  <div className="flex items-center justify-between text-[9px] font-semibold uppercase tracking-[.1em] text-[#81887e]">{activeSection === "Requests" ? "Waiting" : "Needs review"} <Clock3 size={13} /></div>
                  <div className="mt-1.5 font-['Manrope',sans-serif] text-[23px] font-bold tracking-[-.04em]">{activeSection === "Requests" ? requests.filter((r) => r.status === "Needs driver").length : pendingPeopleReviews}<span className="ml-1.5 text-[10px] font-medium tracking-normal text-[#92968d]">{activeSection === "Requests" ? "requests" : "records"}</span></div>
                </div>
                <div className="rounded-2xl border border-[#e4e2d8] bg-[#fbfaf5] px-3 py-3 sm:px-4">
                  <div className="flex items-center justify-between text-[9px] font-semibold uppercase tracking-[.1em] text-[#81887e]">{activeSection === "Requests" ? "On the road" : "Drivers"} <ArrowUpRight size={13} /></div>
                  <div className="mt-1.5 font-['Manrope',sans-serif] text-[23px] font-bold tracking-[-.04em]">{activeSection === "Requests" ? "12" : people.filter((entry) => entry.type === "Driver").length}<span className="ml-1.5 text-[10px] font-medium tracking-normal text-[#92968d]">{activeSection === "Requests" ? "rides" : "records"}</span></div>
                </div>
                <div className="rounded-2xl border border-[#e4e2d8] bg-[#fbfaf5] px-3 py-3 sm:px-4">
                  <div className="flex items-center justify-between text-[9px] font-semibold uppercase tracking-[.1em] text-[#81887e]">{activeSection === "Requests" ? "Available" : "Requesters"} <Users size={13} /></div>
                  <div className="mt-1.5 font-['Manrope',sans-serif] text-[23px] font-bold tracking-[-.04em]">{activeSection === "Requests" ? "8" : people.filter((entry) => entry.type === "Requester").length}<span className="ml-1.5 text-[10px] font-medium tracking-normal text-[#92968d]">{activeSection === "Requests" ? "drivers" : "records"}</span></div>
                </div>
              </div>
            </div>

            <div className="grid items-start gap-5 xl:grid-cols-[minmax(0,1.18fr)_minmax(420px,.82fr)]">
              <section className="min-w-0 overflow-hidden rounded-[20px] border border-[#e2e0d6] bg-[#fbfaf6] shadow-[0_5px_22px_rgba(44,53,44,.035)]">
                {activeSection === "Requests" ? (
                  <>
                <div className="flex flex-col gap-4 border-b border-[#e9e7df] px-4 py-4 sm:px-5 sm:py-5">
                  <div className="flex items-start justify-between gap-3">
                    <div><div className="flex items-center gap-2"><h2 className="font-['Manrope',sans-serif] text-[16px] font-bold tracking-[-.025em]">Ride requests</h2><span className="rounded-md bg-[#efeee7] px-1.5 py-0.5 text-[9px] font-semibold text-[#7d8379]">DEMO</span></div><p className="mt-1 text-[11px] text-[#858a81]">Select a request to review and dispatch.</p></div>
                    <button onClick={() => { setSearch(""); setFilter("All requests"); }} className="flex items-center gap-1.5 rounded-lg border border-[#e4e2d9] px-2.5 py-2 text-[10px] font-semibold text-[#656f66] transition hover:bg-[#f1f0e9]"><SlidersHorizontal size={13} /> <span className="hidden sm:inline">Reset</span></button>
                  </div>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex rounded-lg bg-[#f0efe9] p-1">
                      {(["All requests", "Needs driver", "Assigned"] as const).map((item) => <button key={item} onClick={() => setFilter(item)} className={`rounded-md px-2.5 py-1.5 text-[10px] font-semibold transition sm:px-3 ${filter === item ? "bg-[#fbfaf6] text-[#344438] shadow-sm" : "text-[#81877e] hover:text-[#38453b]"}`}>{item}</button>)}
                    </div>
                    <label className="flex h-8 min-w-[155px] flex-1 items-center gap-2 rounded-lg border border-[#e7e5dc] bg-[#fffefa] px-2.5 sm:max-w-[210px]">
                      <Search size={13} className="shrink-0 text-[#8b9188]" /><input aria-label="Search requests" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search rides…" className="min-w-0 flex-1 bg-transparent text-[10px] text-[#374139] outline-none placeholder:text-[#9a9d94]" />
                    </label>
                  </div>
                </div>
                <div className="wayfare-scroll max-h-[560px] overflow-y-auto">
                  {filteredRequests.length ? filteredRequests.map((ride) => (
                    <button key={ride.id} onClick={() => setSelectedId(ride.id)} className={`wf-enter group relative block w-full border-b border-[#eeece5] px-4 py-4 text-left transition last:border-0 hover:bg-[#f5f4ed] sm:px-5 ${selectedId === ride.id ? "bg-[#f2f4ed]" : "bg-transparent"}`}>
                      {selectedId === ride.id && <span className="absolute bottom-3 left-0 top-3 w-[3px] rounded-r-full bg-[#e96b43]" />}
                      <div className="flex items-start gap-3">
                        <Avatar initials={ride.initials} tone={ride.status === "Review" ? "coral" : "sage"} />
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-center justify-between gap-x-2 gap-y-1">
                            <div className="flex items-center gap-2"><span className="text-[12px] font-semibold text-[#303a32]">{ride.requester}</span><span className="text-[9px] text-[#9a9d94]">{ride.id}</span></div>
                            <div className="flex items-center gap-2"><StatusPill status={ride.status} /><span className="text-[10px] font-semibold text-[#525c52]">{formatRand(ride.fare)}</span></div>
                          </div>
                          <div className="mt-2 grid gap-x-3 gap-y-1.5 text-[10px] text-[#69736a] sm:grid-cols-[1fr_auto] sm:items-center">
                            <div className="flex min-w-0 items-center gap-1.5"><MapPin size={11} className="shrink-0 text-[#d36a49]" /><span className="truncate">{ride.pickup}</span><ArrowDownLeft size={10} className="shrink-0 text-[#a1a49b]" /><span className="truncate">{ride.dropoff}</span></div>
                            <span className="whitespace-nowrap text-[9px] text-[#92968d]">{ride.time} · {ride.note}</span>
                          </div>
                        </div>
                      </div>
                    </button>
                  )) : <div className="px-6 py-14 text-center"><div className="mx-auto mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-[#efeee7] text-[#788176]"><Search size={17} /></div><div className="text-[12px] font-semibold">No matching requests</div><p className="mt-1 text-[10px] text-[#858b81]">Try another name or clear your filters.</p></div>}
                </div>
                <div className="flex items-center justify-between border-t border-[#e9e7df] px-4 py-3 text-[9px] text-[#8b9087] sm:px-5"><span>Showing {filteredRequests.length} of {requests.length} demo requests</span><span>Updated just now <span className="ml-1 inline-block h-1.5 w-1.5 rounded-full bg-[#91a18b]" /></span></div>
                  </>
                ) : (
                  <>
                    <div className="border-b border-[#e9e7df] px-4 py-4 sm:px-5 sm:py-5">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <div className="flex items-center gap-2"><h2 className="font-['Manrope',sans-serif] text-[16px] font-bold tracking-[-.025em]">People &amp; records</h2><span className="rounded-md bg-[#efeee7] px-1.5 py-0.5 text-[9px] font-semibold text-[#7d8379]">DEMO</span></div>
                          <p className="mt-1 text-[11px] text-[#858a81]">Review history, readiness and open follow-ups.</p>
                        </div>
                      </div>
                      <label className="mt-4 flex h-9 items-center gap-2 rounded-lg border border-[#e7e5dc] bg-[#fffefa] px-3">
                        <Search size={14} className="shrink-0 text-[#8b9188]" />
                        <input aria-label="Search people" value={peopleSearch} onChange={(event) => setPeopleSearch(event.target.value)} placeholder="Search name, location or status" className="min-w-0 flex-1 bg-transparent text-[11px] text-[#374139] outline-none placeholder:text-[#9a9d94]" />
                      </label>
                      <div className="mt-3 flex flex-wrap gap-1.5">
                        {(["Everyone", "Drivers", "Requesters", "Needs review"] as const).map((item) => (
                          <button key={item} onClick={() => setPeopleFilter(item)} className={`rounded-lg px-2.5 py-1.5 text-[10px] font-semibold transition ${peopleFilter === item ? "bg-[#34463a] text-white" : "bg-[#f0efe9] text-[#727b72] hover:bg-[#e9e8df] hover:text-[#344438]"}`}>{item}</button>
                        ))}
                      </div>
                    </div>
                    <div className="wayfare-scroll max-h-[560px] overflow-y-auto">
                      {filteredPeople.length ? filteredPeople.map((entry) => {
                        const isReviewed = reviewedPeople.includes(entry.name);
                        const isFollowUp = followUpPeople.includes(entry.name);
                        const reviewLabel = isFollowUp ? "Follow-up" : isReviewed ? "Reviewed" : entry.needsReview ? "Needs review" : entry.signal;
                        const reviewStyle = isFollowUp || (entry.needsReview && !isReviewed)
                          ? "bg-[#f7efdc] text-[#8a7131]"
                          : "bg-[#e8efe6] text-[#4d6b52]";
                        return (
                          <button key={entry.name} aria-pressed={selectedPerson === entry.name} onClick={() => setSelectedPerson(entry.name)} className={`wf-enter group relative flex w-full items-start gap-3 border-b border-[#eeece5] px-4 py-4 text-left transition last:border-0 hover:bg-[#f5f4ed] sm:px-5 ${selectedPerson === entry.name ? "bg-[#f2f4ed]" : "bg-transparent"}`}>
                            {selectedPerson === entry.name && <span className="absolute bottom-3 left-0 top-3 w-[3px] rounded-r-full bg-[#e96b43]" />}
                            <Avatar initials={entry.initials} tone={entry.type === "Driver" ? "ink" : entry.needsReview ? "coral" : "sage"} />
                            <span className="min-w-0 flex-1">
                              <span className="flex flex-wrap items-center justify-between gap-2">
                                <span className="text-[12px] font-semibold text-[#303a32]">{entry.name}</span>
                                <span className={`rounded-full px-2 py-1 text-[9px] font-semibold ${reviewStyle}`}>{reviewLabel}</span>
                              </span>
                              <span className="mt-1 block text-[10px] text-[#69736a]">{entry.type} · {entry.detail}</span>
                              <span className="mt-1.5 flex items-center gap-1.5 text-[10px] text-[#858b81]"><MapPin size={11} className="text-[#d36a49]" />{entry.area}</span>
                              {entry.vehicle && <span className="mt-1 block truncate text-[10px] text-[#858b81]">{entry.vehicle}</span>}
                            </span>
                          </button>
                        );
                      }) : (
                        <div className="px-6 py-14 text-center"><div className="mx-auto mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-[#efeee7] text-[#788176]"><Search size={17} /></div><div className="text-[12px] font-semibold">No matching records</div><p className="mt-1 text-[10px] text-[#858b81]">Try another name or filter.</p></div>
                      )}
                    </div>
                    <div className="flex items-center justify-between border-t border-[#e9e7df] px-4 py-3 text-[9px] text-[#8b9087] sm:px-5"><span>Showing {filteredPeople.length} of {people.length} demo records</span><span>{pendingPeopleReviews} reviews to check</span></div>
                  </>
                )}
              </section>

              <section className="min-w-0 rounded-[20px] border border-[#e2e0d6] bg-[#fbfaf6] shadow-[0_5px_22px_rgba(44,53,44,.035)]">
                <div className="flex items-start justify-between border-b border-[#e9e7df] px-4 py-4 sm:px-5 sm:py-5">
                  <div><div className="text-[9px] font-bold uppercase tracking-[.16em] text-[#899087]">{activeSection === "Requests" ? "Selected request" : "Selected person"}</div><h2 className="mt-1 font-['Manrope',sans-serif] text-[16px] font-bold tracking-[-.025em]">{activeSection === "Requests" ? selected.id : selectedRecord.name}</h2></div>
                  <div className="rounded-lg bg-[#efeee7] px-2 py-1 text-[9px] font-semibold text-[#747b72]">DEMO DATA</div>
                </div>
                {activeSection === "People" ? (
                  <div className="p-4 sm:p-5">
                    <div className="flex flex-wrap items-start gap-3">
                      <Avatar initials={selectedRecord.initials} tone={selectedRecord.type === "Driver" ? "ink" : "sage"} size="lg" />
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2"><h3 className="text-[14px] font-bold text-[#303a32]">{selectedRecord.name}</h3><span className="rounded-full bg-[#efeee7] px-2 py-0.5 text-[9px] font-semibold text-[#687168]">{selectedRecord.type}</span></div>
                        <p className="mt-1 text-[10px] text-[#858b81]">{selectedRecord.detail}</p>
                      </div>
                      <span className={`rounded-full px-2.5 py-1 text-[9px] font-semibold ${selectedPersonFollowUp ? "bg-[#fae8df] text-[#a44c34]" : selectedPersonReviewed ? "bg-[#e8efe6] text-[#4d6b52]" : selectedRecord.needsReview ? "bg-[#f7efdc] text-[#8a7131]" : "bg-[#e8efe6] text-[#4d6b52]"}`}>
                        {selectedPersonFollowUp ? "Follow-up flagged" : selectedPersonReviewed ? "Review complete" : selectedRecord.signal}
                      </span>
                    </div>

                    <div className="mt-4 grid grid-cols-2 gap-2">
                      <div className="rounded-xl border border-[#e9e7df] bg-[#fffefa] p-3"><div className="text-[8px] font-bold uppercase tracking-[.1em] text-[#9a9e95]">Member since</div><div className="mt-1 text-[10px] font-semibold text-[#49554b]">{selectedRecord.joined}</div></div>
                      <div className="rounded-xl border border-[#e9e7df] bg-[#fffefa] p-3"><div className="text-[8px] font-bold uppercase tracking-[.1em] text-[#9a9e95]">Recent activity</div><div className="mt-1 text-[10px] font-semibold text-[#49554b]">{selectedRecord.lastActive}</div></div>
                      <div className="rounded-xl border border-[#e9e7df] bg-[#fffefa] p-3"><div className="text-[8px] font-bold uppercase tracking-[.1em] text-[#9a9e95]">Service area</div><div className="mt-1 text-[10px] font-semibold text-[#49554b]">{selectedRecord.area}</div></div>
                      <div className="rounded-xl border border-[#e9e7df] bg-[#fffefa] p-3"><div className="text-[8px] font-bold uppercase tracking-[.1em] text-[#9a9e95]">{selectedRecord.type === "Driver" ? "Vehicle" : "Account activity"}</div><div className="mt-1 text-[10px] font-semibold text-[#49554b]">{selectedRecord.vehicle ?? selectedRecord.detail}</div></div>
                    </div>

                    <div className="mt-4 overflow-hidden rounded-xl border border-[#e8e6dd] bg-[#fffefa]">
                      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#efede5] px-3.5 py-3">
                        <div><div className="text-[11px] font-semibold text-[#39453c]">Review checklist</div><div className="mt-0.5 text-[9px] text-[#8a8f86]">{selectedRecord.type === "Driver" ? "Identity, vehicle and permit notes" : "Requester account and ride checks"}</div></div>
                        <span className="rounded-md bg-[#f2f0e8] px-2 py-1 text-[8px] font-bold uppercase tracking-[.08em] text-[#7c8178]">Demo · off-platform</span>
                      </div>
                      <div className="divide-y divide-[#f0eee7] px-3.5">
                        {selectedRecord.reviewChecks?.map((check) => {
                          const needsAttention = check.status === "Needs follow-up" || check.status === "New account";
                          return (
                            <div key={check.label} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
                              <span className="text-[10px] font-medium text-[#515d53]">{check.label}</span>
                              <span className={`rounded-full px-2 py-1 text-[9px] font-semibold ${needsAttention ? "bg-[#f7efdc] text-[#8a7131]" : "bg-[#e8efe6] text-[#4d6b52]"}`}>{check.status}</span>
                            </div>
                          );
                        })}
                      </div>
                      <div className="flex gap-2 border-t border-[#efede5] bg-[#f7f6f0] px-3.5 py-3 text-[9px] leading-4 text-[#737c72]"><ShieldCheck size={13} className="mt-0.5 shrink-0 text-[#66806a]" /><span>Verification is represented for design only. Driver document checks currently happen outside Vhaya.</span></div>
                    </div>

                    {selectedPersonRide && (
                      <div className="mt-4 rounded-xl border border-[#e8e6dd] bg-[#f6f5ef] p-3.5">
                        <div className="mb-2 text-[9px] font-bold uppercase tracking-[.12em] text-[#8c9188]">Related demo ride</div>
                        <button onClick={() => { setSelectedId(selectedPersonRide.id); setActiveSection("Requests"); }} className="flex w-full items-center justify-between gap-3 text-left">
                          <span className="min-w-0"><span className="block text-[10px] font-semibold text-[#39453c]">{selectedPersonRide.id} · {selectedPersonRide.status}</span><span className="mt-1 block truncate text-[9px] text-[#777f76]">{selectedPersonRide.pickup} → {selectedPersonRide.dropoff}</span></span>
                          <ArrowUpRight size={14} className="shrink-0 text-[#9a553d]" />
                        </button>
                      </div>
                    )}

                    <div className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2">
                      <button onClick={markPersonReviewed} aria-pressed={selectedPersonReviewed} className={`rounded-lg px-3 py-2.5 text-[10px] font-bold transition ${selectedPersonReviewed ? "border border-[#d7e2d4] bg-[#e9f0e7] text-[#4b6950]" : "bg-[#e96b43] text-white hover:bg-[#d95e39]"}`}>{selectedPersonReviewed ? "Review complete" : "Mark review complete"}</button>
                      <button onClick={flagPersonForFollowUp} aria-pressed={selectedPersonFollowUp} className={`rounded-lg border px-3 py-2.5 text-[10px] font-semibold transition ${selectedPersonFollowUp ? "border-[#edc8b8] bg-[#fae8df] text-[#a44c34]" : "border-[#e4e2d9] bg-[#fbfaf6] text-[#687168] hover:bg-[#f0efe8]"}`}>{selectedPersonFollowUp ? "Follow-up flagged" : "Flag for follow-up"}</button>
                    </div>
                  </div>
                ) : (
                  <div className="p-4 sm:p-5">
                    <div className="flex items-start gap-3.5">
                      <Avatar initials={selected.initials} tone="sage" size="lg" />
                      <div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><h3 className="text-[14px] font-bold text-[#303a32]">{selected.requester}</h3>{selected.requesterTrips === 0 && <span className="rounded-full bg-[#f4efdd] px-2 py-0.5 text-[8px] font-semibold text-[#8a7131]">First ride</span>}</div><div className="mt-1 text-[10px] text-[#858b81]">Requester · {selected.requesterTrips} completed rides · {selected.requesterRating} rating</div></div>
                      <button onClick={markReviewed} className={`rounded-lg border px-2.5 py-2 text-[9px] font-semibold transition ${reviewed.includes(selected.id) ? "border-[#d7e2d4] bg-[#e9f0e7] text-[#4b6950]" : "border-[#e4e2d9] text-[#687168] hover:bg-[#f0efe8]"}`}>{reviewed.includes(selected.id) ? <span className="flex items-center gap-1"><Check size={12} /> Reviewed</span> : "Review record"}</button>
                    </div>
                    <div className="mt-5 rounded-xl border border-[#e8e6dd] bg-[#fffefa] p-3.5">
                      <div className="mb-3 flex items-center justify-between"><span className="text-[9px] font-bold uppercase tracking-[.13em] text-[#8c9188]">Ride details</span><span className="text-[9px] font-medium text-[#8a8f86]">Requested at {selected.time}</span></div>
                      <div className="relative space-y-3 pl-5">
                        <span className="absolute bottom-2 left-[5px] top-2 border-l border-dashed border-[#c9cec2]" />
                        <div className="relative"><span className="absolute -left-5 top-1 h-[11px] w-[11px] rounded-full border-[3px] border-[#e96b43] bg-[#fffefa]" /><div className="text-[8px] font-bold uppercase tracking-[.1em] text-[#a0a39a]">Pickup</div><div className="mt-0.5 text-[10px] font-semibold text-[#3b453d]">{selected.pickup}</div></div>
                        <div className="relative"><span className="absolute -left-5 top-1 h-[11px] w-[11px] rounded-full border-[3px] border-[#718c72] bg-[#fffefa]" /><div className="text-[8px] font-bold uppercase tracking-[.1em] text-[#a0a39a]">Drop-off</div><div className="mt-0.5 text-[10px] font-semibold text-[#3b453d]">{selected.dropoff}</div></div>
                      </div>
                      <div className="mt-4 flex items-center justify-between border-t border-[#efede5] pt-3"><span className="text-[9px] text-[#8b9087]">Estimated fare · demo</span><span className="text-[12px] font-bold text-[#354138]">{formatRand(selected.fare)}</span></div>
                    </div>
                    {selected.status === "Review" && <div className="mt-3 flex gap-2 rounded-xl border border-[#eadfbf] bg-[#f7f2e3] px-3 py-2.5 text-[9px] leading-4 text-[#76683e]"><ShieldCheck size={14} className="mt-0.5 shrink-0" /><span><strong>Record needs a quick look.</strong> This requester is new. Confirm pickup details before moving the request to dispatch.</span></div>}
                    {selected.status === "Assigned" ? (
                      <div className="mt-4 flex items-center gap-3 rounded-xl border border-[#dce7d9] bg-[#edf3ea] p-3">
                        <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[#dce9d8] text-[#4f7556]"><Check size={15} /></div>
                        <div className="min-w-0 flex-1"><div className="text-[10px] font-semibold text-[#3d5943]">Driver assigned</div><div className="mt-0.5 text-[9px] text-[#718474]">{selected.preferredDriver ?? "Driver"} · heading to pickup</div></div>
                        <span className="text-[9px] text-[#748574]">Demo action</span>
                      </div>
                    ) : (
                      <div className="mt-4 rounded-xl border border-[#e8e6dd] bg-[#f6f5ef] p-3.5">
                        <div className="mb-2.5 flex items-center justify-between"><span className="text-[9px] font-bold uppercase tracking-[.13em] text-[#8c9188]">Suggested drivers</span><span className="text-[8px] text-[#969a91]">Nearby · demo</span></div>
                        <div className="space-y-1.5">
                          {drivers.map((driver) => <button key={driver.name} onClick={() => setDriverChoice(driver.name)} className={`flex w-full items-center gap-2.5 rounded-lg border px-2.5 py-2 text-left transition ${driverChoice === driver.name ? "border-[#bac9b7] bg-[#f7faf4]" : "border-transparent bg-[#fbfaf6] hover:border-[#e0e2d8]"}`}>
                            <Avatar initials={driver.initials} tone="ink" size="sm" />
                            <span className="min-w-0 flex-1"><span className="block text-[10px] font-semibold text-[#3e483f]">{driver.name} <span className="font-normal text-[#9a9d94]">· {driver.rating} ★</span></span><span className="mt-0.5 block truncate text-[8px] text-[#858b81]">{driver.car} · {driver.area}</span></span>
                            <span className="whitespace-nowrap text-[9px] font-semibold text-[#6b776b]">{driver.eta}</span>
                            <span className={`ml-1 flex h-4 w-4 items-center justify-center rounded-full border ${driverChoice === driver.name ? "border-[#759075] bg-[#759075] text-white" : "border-[#d4d8ce] text-transparent"}`}><Check size={9} /></span>
                          </button>)}
                        </div>
                        <button onClick={assignDriver} className="mt-3 flex w-full items-center justify-center gap-2 rounded-lg bg-[#e96b43] px-4 py-2.5 text-[10px] font-bold text-white shadow-[0_3px_8px_rgba(194,87,56,.16)] transition hover:bg-[#d95e39] active:translate-y-px">
                          Assign {driverChoice}<ChevronDown size={13} className="rotate-[-90deg]" />
                        </button>
                        <p className="mt-2 text-center text-[8px] text-[#969a91]">Simulation only · no driver will be contacted</p>
                      </div>
                    )}
                    <div className="mt-4 flex items-center justify-between px-1 text-[9px] text-[#8b9087]"><span>Request note</span><span className="max-w-[72%] truncate text-right text-[#667067]">{selected.note}</span></div>
                  </div>
                )}
              </section>
            </div>
            <footer className="mt-5 flex flex-col gap-1 border-t border-[#e2e0d6] pt-4 text-[9px] text-[#90948b] sm:flex-row sm:items-center sm:justify-between"><span>Vhaya Operations · Johannesburg, South Africa</span><span>All names, rides and figures shown are demo data. Actions are simulated locally.</span></footer>
          </div>
        </main>
      </div>
      {toast && <div role="status" className="wf-enter fixed bottom-5 left-1/2 z-50 flex -translate-x-1/2 items-center gap-2 rounded-xl bg-[#26362e] px-4 py-3 text-[11px] font-medium text-white shadow-xl"><Check size={14} className="text-[#b4cfaa]" />{toast}<button onClick={() => setToast("")} className="ml-2 rounded p-0.5 text-white/60 hover:text-white" aria-label="Dismiss notification"><X size={13} /></button></div>}
    </div>
  );
}

export default AdminConsole;
