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
  MapPin,
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

const week = [
  { day: "Mon", count: 12 },
  { day: "Tue", count: 14 },
  { day: "Wed", count: 13 },
  { day: "Thu", count: 15 },
  { day: "Fri", count: 17 },
  { day: "Sat", count: 16 },
  { day: "Sun", count: 16 },
];

const statuses = [
  { label: "Completed", count: 72, color: "#64866a" },
  { label: "Active", count: 18, color: "#e96b43" },
  { label: "Waiting for driver", count: 9, color: "#c5a44e" },
  { label: "Cancelled", count: 4, color: "#c9786a" },
];

const cities = [
  { name: "Johannesburg", count: 58, share: 56, short: "JHB", color: "#e96b43" },
  { name: "Cape Town", count: 31, share: 30, short: "CPT", color: "#64866a" },
  { name: "Durban", count: 14, share: 14, short: "DBN", color: "#c5a44e" },
];

const recentTrips = [
  {
    code: "WF-2841",
    rider: "Lerato Mokoena",
    route: "Rosebank Mall → OR Tambo International",
    category: "Airport transfer",
    time: "08:42",
    fare: "R 386.50",
    status: "Waiting for driver",
    tone: "coral",
  },
  {
    code: "WF-2838",
    rider: "Thabo Ndlovu",
    route: "Braamfontein → Sandton City",
    category: "Everyday ride",
    time: "08:38",
    fare: "R 174.00",
    status: "Waiting for driver",
    tone: "coral",
  },
  {
    code: "WF-2833",
    rider: "Amara Jacobs",
    route: "Claremont → Cape Town International",
    category: "Airport transfer",
    time: "08:31",
    fare: "R 248.50",
    status: "Active",
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

const styles = `
  .wf-overview {
    --wf-ink: #26362e;
    --wf-paper: #f5f3ec;
    --wf-white: #fffefa;
    --wf-line: #e5e2d8;
    --wf-muted: #7b8379;
    --wf-forest: #304238;
    min-height: 100vh;
    background: var(--wf-paper);
    color: var(--wf-ink);
    font-family: "DM Sans", ui-sans-serif, system-ui, sans-serif;
    font-size: 12px;
  }
  .wf-overview h1, .wf-overview h2, .wf-overview p { margin: 0; }
  .wf-overview h1, .wf-overview h2, .wf-overview .wf-metric > strong,
  .wf-overview .wf-fare-total > strong {
    font-family: "Plus Jakarta Sans", "DM Sans", ui-sans-serif, sans-serif;
  }
  .wf-shell { display: flex; min-height: 100vh; }
  .wf-sidebar {
    position: relative; display: flex; width: 226px; flex: 0 0 226px;
    flex-direction: column; padding: 23px 14px 16px; background: #26382f;
    color: #f6f3e9;
  }
  .wf-brand { display: flex; align-items: center; height: 40px; margin: 0 3px 31px; padding: 6px 10px; border-radius: 10px; background: #f5f3ec; }
  .wf-brand img { width: 126px; height: 27px; object-fit: contain; object-position: left center; }
  .wf-nav-label { padding: 0 10px 9px; color: #a9b5a9; font-size: 9px; font-weight: 700; letter-spacing: .17em; text-transform: uppercase; }
  .wf-nav { display: grid; gap: 4px; }
  .wf-nav-item { display: flex; min-height: 39px; align-items: center; gap: 11px; padding: 0 10px; border: 1px solid transparent; border-radius: 10px; color: #c1cbc2; font-size: 11px; font-weight: 500; }
  .wf-nav-item > span { flex: 1; }
  .wf-nav-item.is-active { background: #3c5144; color: #fffefa; font-weight: 700; }
  .wf-nav-item b { min-width: 20px; padding: 3px 6px; border-radius: 20px; background: #e96b43; color: white; text-align: center; font-size: 9px; }
  .wf-sidebar-bottom { margin-top: auto; padding: 13px 12px 11px; border: 1px solid rgb(255 255 255 / 11%); border-radius: 13px; background: rgb(255 255 255 / 4%); }
  .wf-desk-title { display: flex; align-items: center; gap: 8px; color: #e8ede7; font-size: 10px; font-weight: 700; }
  .wf-desk-title > span, .wf-preview-label > span { width: 7px; height: 7px; border-radius: 50%; background: #88a783; }
  .wf-sidebar-bottom p { margin-top: 7px; color: #b5c0b5; font-size: 10px; }
  .wf-sidebar-rule { height: 1px; margin: 11px 0 9px; background: rgb(255 255 255 / 10%); }
  .wf-sidebar-bottom > span { color: #aebbb0; font-size: 9px; }
  .wf-main { min-width: 0; flex: 1; }
  .wf-topbar { display: flex; min-height: 62px; align-items: center; justify-content: space-between; gap: 12px; padding: 0 clamp(18px, 3vw, 38px); border-bottom: 1px solid var(--wf-line); background: var(--wf-white); }
  .wf-breadcrumb { display: flex; align-items: center; gap: 9px; color: #878d83; font-size: 10px; }
  .wf-breadcrumb i { color: #c4c4b9; font-style: normal; }
  .wf-breadcrumb strong { color: #354239; font-weight: 700; }
  .wf-top-meta { display: flex; align-items: center; gap: 14px; }
  .wf-preview-label { display: inline-flex; align-items: center; gap: 7px; color: #687668; font-size: 9px; font-weight: 700; letter-spacing: .02em; }
  .wf-period { display: flex; height: 31px; align-items: center; gap: 8px; padding: 0 10px; border: 1px solid var(--wf-line); border-radius: 8px; background: #fffefa; color: #657065; font-size: 9px; font-weight: 600; }
  .wf-avatar { display: grid; width: 31px; height: 31px; place-items: center; border-radius: 50%; background: #dfe8dc; color: #405a46; font-size: 9px; font-weight: 800; }
  .wf-content { width: min(1320px, 100%); margin: 0 auto; padding: 24px clamp(18px, 3vw, 38px) 24px; }
  .wf-page-intro { display: flex; align-items: end; justify-content: space-between; gap: 18px; margin-bottom: 17px; }
  .wf-kicker, .wf-eyebrow { color: #81877c; font-size: 8px; font-weight: 800; letter-spacing: .14em; text-transform: uppercase; }
  .wf-kicker { display: flex; align-items: center; gap: 7px; color: #526a54; }
  .wf-kicker span { color: #a1a398; font-weight: 600; }
  .wf-page-intro h1 { margin-top: 7px; color: #28372e; font-size: clamp(25px, 3vw, 31px); font-weight: 800; letter-spacing: -.055em; line-height: 1.12; }
  .wf-page-intro h1 > span { color: #e96b43; }
  .wf-page-intro p { margin-top: 6px; color: #747c73; font-size: 10px; }
  .wf-snapshot-date { display: grid; gap: 4px; padding: 0 0 2px; text-align: right; }
  .wf-snapshot-date span { color: #979a90; font-size: 9px; }
  .wf-snapshot-date strong { color: #59645a; font-size: 10px; font-weight: 700; }
  .wf-trust-note { display: flex; min-height: 43px; align-items: center; gap: 10px; margin-bottom: 14px; padding: 9px 12px; border: 1px solid #dfe6da; border-radius: 11px; background: #e9efe6; color: #526852; }
  .wf-trust-note > svg { flex: 0 0 auto; }
  .wf-trust-note p { flex: 1; color: #566556; font-size: 9px; line-height: 1.5; }
  .wf-trust-note p strong { color: #3f5946; }
  .wf-trust-note > span { padding: 5px 7px; border-radius: 20px; background: #dce7d8; color: #557054; white-space: nowrap; font-size: 8px; font-weight: 700; }
  .wf-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-bottom: 12px; }
  .wf-metric, .wf-panel { border: 1px solid #e4e2d8; border-radius: 13px; background: var(--wf-white); box-shadow: 0 3px 12px rgb(44 53 44 / 2.5%); }
  .wf-metric { min-height: 105px; padding: 13px 14px 11px; }
  .wf-metric-top { display: flex; align-items: center; justify-content: space-between; color: #777f75; font-size: 9px; font-weight: 600; }
  .wf-metric-icon { display: grid; width: 29px; height: 29px; place-items: center; border-radius: 9px; font-style: normal; }
  .tone-coral { background: #fae8df; color: #ad563d; }
  .tone-sage { background: #e5eee5; color: #527254; }
  .tone-gold { background: #f4efdd; color: #8a7131; }
  .tone-red { background: #fae8e5; color: #aa5145; }
  .wf-metric > strong { display: block; margin-top: 6px; color: #29372e; font-size: 23px; font-weight: 800; letter-spacing: -.045em; line-height: 1; }
  .wf-metric > small { display: block; margin-top: 6px; color: #9a9d94; font-size: 8px; }
  .wf-visual-grid { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(260px, .85fr); gap: 12px; align-items: stretch; }
  .wf-panel { padding: 16px; }
  .wf-panel h2, .wf-section-heading h2, .wf-fare-top h2, .wf-activity-head h2 { margin-top: 4px; color: #303a32; font-size: 13px; font-weight: 800; letter-spacing: -.025em; }
  .wf-panel-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
  .wf-panel-head p, .wf-activity-head p { margin-top: 4px; color: #858b81; font-size: 9px; }
  .wf-chart-total { display: flex; align-items: baseline; gap: 5px; padding: 5px 8px; border-radius: 7px; background: #f7f6f0; color: #788075; }
  .wf-chart-total strong { color: #38463b; font-size: 14px; }
  .wf-chart-total small { font-size: 8px; }
  .wf-volume-chart { display: flex; height: 142px; margin-top: 14px; }
  .wf-chart-ylabels { display: flex; width: 23px; flex: 0 0 23px; flex-direction: column; justify-content: space-between; padding: 1px 0 19px; color: #a0a296; font-size: 8px; }
  .wf-chart-plot { position: relative; flex: 1; min-width: 0; }
  .wf-chart-grid { position: absolute; inset: 0 0 19px; display: flex; flex-direction: column; justify-content: space-between; }
  .wf-chart-grid i { display: block; border-top: 1px dashed #e8e6de; }
  .wf-bars { position: absolute; inset: 0 2px 0; display: flex; align-items: stretch; justify-content: space-around; gap: 7px; }
  .wf-bar-column { position: relative; display: flex; width: 10%; flex-direction: column; align-items: center; justify-content: flex-end; }
  .wf-bar-value { position: absolute; bottom: calc((var(--bar-height, 0) * 1%) + 23px); color: #758074; font-size: 8px; }
  .wf-bar { z-index: 1; width: min(22px, 70%); min-height: 3px; margin-bottom: 19px; border-radius: 5px 5px 2px 2px; background: #a9bca5; transition: transform .2s ease, opacity .2s ease; }
  .wf-bar-column:nth-child(1) .wf-bar { height: 66.67% !important; }
  .wf-bar-column:nth-child(2) .wf-bar { height: 77.78% !important; }
  .wf-bar-column:nth-child(3) .wf-bar { height: 72.22% !important; }
  .wf-bar-column:nth-child(4) .wf-bar { height: 83.33% !important; }
  .wf-bar-column:nth-child(5) .wf-bar { height: 94.44% !important; }
  .wf-bar-column:nth-child(6) .wf-bar { height: 88.89% !important; }
  .wf-bar-column:nth-child(7) .wf-bar { height: 88.89% !important; }
  .wf-bar-peak { background: #e96b43 !important; }
  .wf-bar-day { position: absolute; bottom: 0; color: #82897f; font-size: 8px; }
  .wf-chart-foot { display: flex; justify-content: space-between; margin-top: 3px; color: #92958b; font-size: 8px; }
  .wf-chart-foot > span:first-child { display: inline-flex; align-items: center; gap: 6px; color: #717c70; }
  .wf-legend-mark { width: 7px; height: 7px; border-radius: 2px; background: #a9bca5; }
  .wf-section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
  .wf-heading-detail { padding: 5px 7px; border-radius: 6px; background: #f4f3ed; color: #777e74; font-size: 8px; font-weight: 600; white-space: nowrap; }
  .wf-status-content { display: flex; align-items: center; gap: clamp(14px, 2vw, 25px); margin-top: 16px; }
  .wf-donut { position: relative; display: grid; width: 118px; height: 118px; flex: 0 0 118px; place-items: center; border-radius: 50%; background: conic-gradient(#64866a 0deg 251.65deg, #e96b43 251.65deg 314.56deg, #c5a44e 314.56deg 346.02deg, #c9786a 346.02deg 360deg); }
  .wf-donut::before { position: absolute; width: 78px; height: 78px; border-radius: 50%; background: #fffefa; content: ""; }
  .wf-donut-center { z-index: 1; display: grid; gap: 1px; text-align: center; }
  .wf-donut-center strong { color: #2d3c32; font-size: 20px; letter-spacing: -.05em; }
  .wf-donut-center span { color: #8a9086; font-size: 8px; }
  .wf-status-legend { display: grid; min-width: 0; flex: 1; gap: 10px; }
  .wf-status-row { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: 9px; font-size: 8px; }
  .wf-status-name { display: flex; min-width: 0; align-items: center; gap: 6px; color: #727b71; white-space: nowrap; }
  .wf-status-name i { width: 7px; height: 7px; flex: 0 0 auto; border-radius: 2px; }
  .wf-status-row > strong { min-width: 17px; color: #39453c; font-size: 9px; text-align: right; }
  .wf-status-share { width: 24px; color: #9a9d94; text-align: right; }
  .wf-panel-note { margin-top: 14px !important; padding-top: 10px; border-top: 1px solid #efede6; color: #9a9d94; font-size: 8px; }
  .wf-area-panel { padding-bottom: 13px; }
  .wf-area-list { display: grid; gap: 13px; margin-top: 18px; }
  .wf-area-row { display: flex; align-items: center; gap: 9px; }
  .wf-city-mark { display: grid; width: 32px; height: 30px; flex: 0 0 32px; place-items: center; border: 1px solid currentColor; border-radius: 8px; background: #fbfaf5; font-size: 8px; font-weight: 800; letter-spacing: .04em; }
  .wf-area-main { min-width: 0; flex: 1; }
  .wf-area-label { display: flex; justify-content: space-between; gap: 7px; margin-bottom: 6px; color: #727b71; font-size: 9px; }
  .wf-area-label strong { overflow: hidden; color: #49564b; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
  .wf-area-label span { color: #4b574c; font-weight: 700; }
  .wf-area-track { height: 5px; overflow: hidden; border-radius: 10px; background: #efeee7; }
  .wf-area-track i { display: block; height: 100%; border-radius: inherit; }
  .wf-area-share { width: 27px; color: #8b9187; font-size: 8px; text-align: right; }
  .wf-area-foot { display: flex; align-items: center; gap: 6px; margin-top: 14px; padding-top: 10px; border-top: 1px solid #efede6; color: #92958b; font-size: 8px; }
  .wf-fares-panel { display: flex; flex-direction: column; }
  .wf-fare-top { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
  .wf-fare-icon { display: grid; width: 32px; height: 32px; place-items: center; border-radius: 9px; background: #f5eee5; color: #a45b3f; }
  .wf-fare-total { margin-top: 16px; padding: 13px 14px 10px; border-radius: 10px; background: #f7f6f0; }
  .wf-fare-total > span { color: #858b81; font-size: 8px; font-weight: 700; letter-spacing: .09em; text-transform: uppercase; }
  .wf-fare-total > span i { padding: 0 3px; color: #c5a44e; font-style: normal; }
  .wf-fare-total > strong { display: block; margin-top: 5px; color: #29372e; font-size: 25px; font-weight: 800; letter-spacing: -.045em; }
  .wf-fare-count { display: flex; justify-content: space-between; margin-top: 11px; padding-top: 9px; border-top: 1px solid #e5e2d8; color: #798177; font-size: 9px; }
  .wf-fare-count b { color: #39463b; font-weight: 700; }
  .wf-fare-disclaimer { margin-top: auto !important; padding-top: 11px; color: #858b81; font-size: 8px; line-height: 1.55; }
  .wf-activity-panel { margin-top: 12px; padding: 0; overflow: hidden; }
  .wf-activity-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 16px 12px; border-bottom: 1px solid #eeece5; }
  .wf-total-chip { display: inline-flex; align-items: center; gap: 6px; padding: 6px 8px; border-radius: 7px; background: #f0efe9; color: #737b72; font-size: 8px; font-weight: 700; white-space: nowrap; }
  .wf-table-head, .wf-trip-row { display: grid; grid-template-columns: minmax(0, 1.8fr) minmax(110px, .7fr) 80px 132px; align-items: center; gap: 14px; }
  .wf-table-head { padding: 8px 16px; background: #fbfaf6; color: #999c92; font-size: 8px; font-weight: 700; letter-spacing: .05em; text-transform: uppercase; }
  .wf-trip-row { min-height: 58px; padding: 9px 16px; border-top: 1px solid #f0eee8; }
  .wf-trip-route { min-width: 0; }
  .wf-trip-route > div { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
  .wf-trip-route strong { color: #39453c; font-size: 9px; font-weight: 700; }
  .wf-trip-route > div span { color: #9a9d94; font-size: 8px; }
  .wf-trip-route p { overflow: hidden; margin-top: 4px; color: #68736a; font-size: 8px; text-overflow: ellipsis; white-space: nowrap; }
  .wf-trip-meta { display: grid; gap: 4px; color: #737d72; font-size: 8px; }
  .wf-trip-meta small { color: #999c92; font-size: 8px; }
  .wf-trip-fare { color: #39453c; font-size: 9px; text-align: right; }
  .wf-status { display: inline-flex; width: fit-content; align-items: center; gap: 6px; padding: 5px 8px; border-radius: 30px; font-size: 8px; font-weight: 700; white-space: nowrap; }
  .wf-status-dot { width: 5px; height: 5px; border-radius: 50%; background: currentColor; }
  .wf-status-coral { background: #fae8df; color: #a44c34; }
  .wf-status-sage { background: #e5eee5; color: #41634b; }
  .wf-status-gold { background: #f4efdd; color: #8a7131; }
  .wf-footer { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 9px; margin-top: 15px; padding-top: 11px; border-top: 1px solid #e3e0d6; color: #90948b; font-size: 8px; }
  .wf-footer span { display: inline-flex; align-items: center; gap: 5px; }
  .wf-footer i { color: #b2b5aa; font-style: normal; }
  @media (min-width: 1400px) { .wf-visual-grid { grid-template-columns: minmax(0, 1.2fr) minmax(310px, .85fr) minmax(250px, .8fr); } .wf-volume-panel { grid-column: span 2; } }
  @media (max-width: 1000px) { .wf-sidebar { width: 196px; flex-basis: 196px; } .wf-visual-grid { grid-template-columns: minmax(0, 1.2fr) minmax(250px, .9fr); } }
  @media (max-width: 760px) {
    .wf-shell { display: block; }
    .wf-sidebar { display: none; }
    .wf-topbar { min-height: 55px; padding: 0 16px; }
    .wf-top-meta { gap: 9px; }
    .wf-preview-label { font-size: 0; }
    .wf-preview-label > span { width: 8px; height: 8px; }
    .wf-period { height: 29px; gap: 5px; padding: 0 7px; font-size: 8px; }
    .wf-avatar { width: 28px; height: 28px; }
    .wf-content { padding: 20px 15px 20px; }
    .wf-page-intro { align-items: flex-start; }
    .wf-snapshot-date { display: none; }
    .wf-trust-note { align-items: flex-start; }
    .wf-trust-note > span { display: none; }
    .wf-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
    .wf-metric { min-height: 96px; padding: 11px; }
    .wf-visual-grid { grid-template-columns: minmax(0, 1fr); }
    .wf-volume-chart { height: 155px; }
    .wf-status-content { justify-content: center; }
    .wf-area-list { max-width: 420px; }
    .wf-fare-disclaimer { margin-top: 12px !important; }
    .wf-table-head { display: none; }
    .wf-trip-row { grid-template-columns: minmax(0, 1fr) auto; gap: 7px 10px; padding: 12px 13px; }
    .wf-trip-route { grid-column: 1 / -1; }
    .wf-trip-meta { grid-column: 1; grid-row: 2; display: flex; gap: 7px; }
    .wf-trip-fare { grid-column: 2; grid-row: 2; }
    .wf-trip-row .wf-status { grid-column: 1 / -1; grid-row: 3; }
    .wf-footer { align-items: flex-start; flex-direction: column; }
  }
  @media (max-width: 390px) {
    .wf-content { padding-right: 12px; padding-left: 12px; }
    .wf-status-content { gap: 12px; }
    .wf-donut { width: 100px; height: 100px; flex-basis: 100px; }
    .wf-donut::before { width: 66px; height: 66px; }
    .wf-status-legend { gap: 8px; }
    .wf-status-row { gap: 5px; font-size: 7px; }
    .wf-status-name { gap: 4px; }
  }
`;

function StatusPill({ children, tone }: { children: string; tone: string }) {
  return (
    <span className={`wf-status wf-status-${tone}`}>
      <span className="wf-status-dot" />
      {children}
    </span>
  );
}

function SectionHeading({
  eyebrow,
  title,
  detail,
}: {
  eyebrow: string;
  title: string;
  detail?: string;
}) {
  return (
    <div className="wf-section-heading">
      <div>
        <div className="wf-eyebrow">{eyebrow}</div>
        <h2>{title}</h2>
      </div>
      {detail ? <span className="wf-heading-detail">{detail}</span> : null}
    </div>
  );
}

function WeeklyVolume() {
  const max = 18;
  return (
    <section className="wf-panel wf-volume-panel">
      <div className="wf-panel-head">
        <div>
          <div className="wf-eyebrow">Request rhythm · 7 days</div>
          <h2>Trip volume</h2>
          <p>Ride requests by day across the preview week.</p>
        </div>
        <span className="wf-chart-total"><strong>103</strong><small>requests</small></span>
      </div>
      <div className="wf-volume-chart" role="img" aria-label="Trip requests: Monday 12, Tuesday 14, Wednesday 13, Thursday 15, Friday 17, Saturday 16, Sunday 16">
        <div className="wf-chart-ylabels" aria-hidden="true">
          <span>18</span><span>12</span><span>6</span><span>0</span>
        </div>
        <div className="wf-chart-plot">
          <div className="wf-chart-grid"><i /><i /><i /><i /></div>
          <div className="wf-bars">
            {week.map((item, index) => (
              <div className="wf-bar-column" key={item.day}>
                <span className="wf-bar-value" style={{ bottom: `calc(${(item.count / max) * 100}% + 20px)` }}>{item.count}</span>
                <div
                  className={`wf-bar ${index === 4 ? "wf-bar-peak" : ""}`}
                  style={{ height: `${(item.count / max) * 100}%` }}
                />
                <span className="wf-bar-day">{item.day}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
      <div className="wf-chart-foot">
        <span><i className="wf-legend-mark" /> Ride requests</span>
        <span>Preview week · Mon–Sun</span>
      </div>
    </section>
  );
}

function StatusMix() {
  return (
    <section className="wf-panel wf-status-panel">
      <SectionHeading eyebrow="Request outcomes" title="Ride status mix" detail="103 total" />
      <div className="wf-status-content">
        <div
          className="wf-donut"
          role="img"
          aria-label="Ride status mix: 72 completed, 18 active, 9 waiting for driver, 4 cancelled"
        >
          <div className="wf-donut-center"><strong>103</strong><span>rides</span></div>
        </div>
        <div className="wf-status-legend">
          {statuses.map((item) => (
            <div className="wf-status-row" key={item.label}>
              <span className="wf-status-name"><i style={{ backgroundColor: item.color }} />{item.label}</span>
              <strong>{item.count}</strong>
              <span className="wf-status-share">{Math.round((item.count / 103) * 100)}%</span>
            </div>
          ))}
        </div>
      </div>
      <p className="wf-panel-note">Counts describe the same synthetic preview snapshot.</p>
    </section>
  );
}

function ServiceAreas() {
  return (
    <section className="wf-panel wf-area-panel">
      <SectionHeading eyebrow="Where requests begin" title="Service areas" detail="103 requests" />
      <div className="wf-area-list">
        {cities.map((city) => (
          <div className="wf-area-row" key={city.name}>
            <div className="wf-city-mark" style={{ color: city.color }}>{city.short}</div>
            <div className="wf-area-main">
              <div className="wf-area-label"><strong>{city.name}</strong><span>{city.count}</span></div>
              <div className="wf-area-track"><i style={{ width: `${city.share}%`, backgroundColor: city.color }} /></div>
            </div>
            <span className="wf-area-share">{city.share}%</span>
          </div>
        ))}
      </div>
      <div className="wf-area-foot"><MapPin size={13} /> Request count by service area</div>
    </section>
  );
}

export function VisualOverview() {
  return (
    <div className="wayfare-admin-theme wf-overview">
      <style>{styles}</style>
      <div className="wf-shell">
        <aside className="wf-sidebar">
          <div className="wf-brand">
            <img src="/__mockup/images/wayfare-logo.png" alt="Wayfare" />
          </div>
          <div className="wf-nav-label">Operations</div>
          <nav aria-label="Admin sections" className="wf-nav">
            {navItems.map(({ label, icon: Icon, active, count }) => (
              <div className={`wf-nav-item ${active ? "is-active" : ""}`} key={label}>
                <Icon size={15} strokeWidth={1.8} />
                <span>{label}</span>
                {count ? <b>{count}</b> : null}
              </div>
            ))}
          </nav>
          <div className="wf-sidebar-bottom">
            <div className="wf-desk-title"><span /> Johannesburg desk</div>
            <p>Shared operations view</p>
            <div className="wf-sidebar-rule" />
            <span>South Africa · design preview</span>
          </div>
        </aside>

        <main className="wf-main">
          <header className="wf-topbar">
            <div className="wf-breadcrumb"><span>Operations</span><i>/</i><strong>Overview</strong></div>
            <div className="wf-top-meta">
              <span className="wf-preview-label"><span /> Synthetic preview data</span>
              <span className="wf-period"><CalendarDays size={13} /> Preview week <ChevronDown size={12} /></span>
              <div className="wf-avatar" aria-label="Admin initials">KM</div>
            </div>
          </header>

          <div className="wf-content">
            <div className="wf-page-intro">
              <div>
                <div className="wf-kicker"><Activity size={12} /> OPERATIONS SNAPSHOT <span>· DESIGN PREVIEW</span></div>
                <h1>Ride overview<span>.</span></h1>
                <p>Requests, readiness and completed fares across Wayfare.</p>
              </div>
              <div className="wf-snapshot-date"><span>Sample snapshot</span><strong>Preview week · 7 days</strong></div>
            </div>

            <div className="wf-trust-note">
              <ShieldCheck size={16} />
              <p><strong>For exploration only.</strong> Values below are synthetic design-preview data and are not live operational records.</p>
              <span>Not connected to API</span>
            </div>

            <div className="wf-metrics">
              {metrics.map(({ label, value, icon: Icon, tone }) => (
                <section className="wf-metric" key={label}>
                  <div className="wf-metric-top">
                    <span>{label}</span>
                    <i className={`wf-metric-icon tone-${tone}`}><Icon size={15} /></i>
                  </div>
                  <strong>{value}</strong>
                  <small>Current preview snapshot</small>
                </section>
              ))}
            </div>

            <div className="wf-visual-grid">
              <WeeklyVolume />
              <StatusMix />
              <ServiceAreas />
              <section className="wf-panel wf-fares-panel">
                <div className="wf-fare-top">
                  <div>
                    <div className="wf-eyebrow">Completed trip records</div>
                    <h2>Completed ride fares</h2>
                  </div>
                  <span className="wf-fare-icon"><CircleDollarSign size={17} /></span>
                </div>
                <div className="wf-fare-total">
                  <span>ZAR <i>·</i> completed fares</span>
                  <strong>R 18,420</strong>
                  <div className="wf-fare-count"><span>Completed rides</span><b>72 trips</b></div>
                </div>
                <p className="wf-fare-disclaimer">Ride fare totals from completed trip records; not payment captures or driver settlements.</p>
              </section>
            </div>

            <section className="wf-panel wf-activity-panel">
              <div className="wf-activity-head">
                <div>
                  <div className="wf-eyebrow">Operations feed · sample records</div>
                  <h2>Recent ride activity</h2>
                  <p>Latest requests in the synthetic overview.</p>
                </div>
                <span className="wf-total-chip"><Navigation size={12} /> 103 total requests</span>
              </div>
              <div className="wf-table-wrap">
                <div className="wf-table-head"><span>Rider &amp; route</span><span>Category · time</span><span>Fare</span><span>Status</span></div>
                {recentTrips.map((trip) => (
                  <article className="wf-trip-row" key={trip.code}>
                    <div className="wf-trip-route">
                      <div><strong>{trip.rider}</strong><span>{trip.code}</span></div>
                      <p>{trip.route}</p>
                    </div>
                    <div className="wf-trip-meta"><span>{trip.category}</span><small>{trip.time}</small></div>
                    <strong className="wf-trip-fare">{trip.fare}</strong>
                    <StatusPill tone={trip.tone}>{trip.status}</StatusPill>
                  </article>
                ))}
              </div>
            </section>

            <footer className="wf-footer">
              <span>Wayfare Operations <i>·</i> South Africa</span>
              <span><ShieldCheck size={12} /> All displayed values are synthetic design-preview data</span>
              <span>Snapshot · 103 ride requests <ArrowUpRight size={12} /></span>
            </footer>
          </div>
        </main>
      </div>
    </div>
  );
}

export default VisualOverview;
