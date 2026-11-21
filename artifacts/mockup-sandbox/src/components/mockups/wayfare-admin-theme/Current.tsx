import "./_group.css";
import { useEffect, useState, type ReactNode } from "react";
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
  Activity,
  AlertCircle,
  Check,
  ChevronDown,
  Download,
  Moon,
  Printer,
  RefreshCw,
  ShieldCheck,
  Sun,
} from "lucide-react";

const links = [
  ["/", "Overview"],
  ["/rides", "Rides"],
  ["/drivers-map", "Drivers map"],
  ["/people", "People"],
  ["/driver-reviews", "Driver reviews"],
  ["/driver-compliance", "Compliance"],
  ["/support", "Support"],
  ["/finance", "Finance"],
  ["/subscriptions", "Billing"],
  ["/account-deletions", "Deletions"],
] as const;

const date = (value: number | string | null | undefined) =>
  value
    ? new Intl.DateTimeFormat("en-ZA", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(value))
    : "Not recorded";

function Feedback({
  children,
  error = false,
}: {
  children: ReactNode;
  error?: boolean;
}) {
  return (
    <div
      className={`feedback ${error ? "error" : ""}`}
      role={error ? "alert" : "status"}
    >
      {error ? <AlertCircle size={15} /> : <Check size={15} />}
      <span>{children}</span>
    </div>
  );
}

function QueryState({
  loading,
  error,
  empty,
  retry,
}: {
  loading: boolean;
  error?: unknown;
  empty?: boolean;
  retry: () => void;
}) {
  if (loading)
    return (
      <div className="state-box">
        <div className="skeleton" aria-label="Loading data" />
      </div>
    );
  if (error)
    return (
      <div className="state-box error">
        <strong>Could not load this data.</strong>
        <button className="button" onClick={retry} data-testid="button-retry">
          Try again
        </button>
      </div>
    );
  if (empty)
    return (
      <div className="empty">
        Nothing to show yet. Try another search or check back later.
      </div>
    );
  return null;
}

function PageFrame({
  title,
  subtitle,
  eyebrow,
  children,
}: {
  title: string;
  subtitle: string;
  eyebrow: string;
  children: ReactNode;
}) {
  return (
    <div className="vhaya">
      <header className="global-header">
        <a href="/" className="brand">
          <span className="brand-mark">V</span>
          <span>
            <b>vhaya</b>
            <small>operations</small>
          </span>
        </a>
        <nav className="global-nav" aria-label="Operations navigation">
          {links.map(([href, label]) => (
            <a
              href={href}
              key={href}
              className={`global-nav-link ${href === "/" ? "active" : ""}`}
            >
              {label}
            </a>
          ))}
        </nav>
        <div className="global-user">
          <span className="secure-dot" />
          <span>Ops Team</span>
          <button className="button small">Sign out</button>
        </div>
      </header>
      <main className="page admin-page">
        <header className="page-head">
          <div>
            <div className="eyebrow">{eyebrow}</div>
            <h1 className="title">{title}</h1>
            <p className="subtitle">{subtitle}</p>
          </div>
        </header>
        <div className="content admin-content">{children}</div>
      </main>
    </div>
  );
}

export function Current() {
  const [auto, setAuto] = useState(false);
  const [dark, setDark] = useState(
    () =>
      typeof localStorage !== "undefined" &&
      localStorage.getItem("vhaya-dark") === "true",
  );
  const [menu, setMenu] = useState(false);
  useEffect(() => {
    document.documentElement.classList.toggle("dark", dark);
    localStorage.setItem("vhaya-dark", String(dark));
  }, [dark]);

  // Static responses replace the original live API hooks for this isolated preview.
  const overview = {
    data: {
      summary: {
        users: 2284,
        suspendedUsers: 14,
        rideRequests: 328,
        activeRides: 18,
        pendingDriverReviews: 7,
        paymentIssues: 12,
        activeSupportCases: 9,
        urgentSupportCases: 3,
        unassignedSupportCases: 2,
      },
      support: {
        last30Days: 127,
        byCategory: [
          { category: "driver_verification", count: 42 },
          { category: "ride_support", count: 31 },
          { category: "payments", count: 26 },
          { category: "account_access", count: 18 },
          { category: "other", count: 10 },
        ],
        byPriority: [
          { priority: "normal", count: 76 },
          { priority: "high", count: 38 },
          { priority: "urgent", count: 13 },
        ],
      },
      dataUpdatedAt: Date.now(),
    },
    error: null,
    isLoading: false,
    isFetching: false,
    refetch: () => undefined,
  };
  const activity = {
    data: [
      {
        id: "a-1",
        createdAt: Date.now() - 180000,
        summary: "Driver documents approved",
        actor: "N. Mokoena",
        subject: "Thabo M.",
      },
      {
        id: "a-2",
        createdAt: Date.now() - 840000,
        summary: "Support case moved to in progress",
        actor: "L. Nkosi",
        subject: "Ride · VY-20481",
      },
      {
        id: "a-3",
        createdAt: Date.now() - 1620000,
        summary: "Payout batch reconciled",
        actor: "S. Dlamini",
        subject: "Batch · PB-1082",
      },
    ],
    error: null,
    isLoading: false,
    isFetching: false,
    refetch: () => undefined,
  };
  const counts = overview.data.summary;
  const categories = overview.data.support.byCategory;
  const priorities = overview.data.support.byPriority;
  const loading = false;
  const kpis = [
    ["Users", counts.users],
    ["Suspended users", counts.suspendedUsers],
    ["Ride requests", counts.rideRequests],
    ["Active rides", counts.activeRides],
    ["Driver reviews", counts.pendingDriverReviews],
    ["Payment issues", counts.paymentIssues],
    ["Active support", counts.activeSupportCases],
    [
      "Urgent / unassigned",
      `${counts.urgentSupportCases} / ${counts.unassignedSupportCases}`,
    ],
  ] as const;
  const lastRefreshed = date(overview.data.dataUpdatedAt);
  const exportCsv = (
    name: string,
    rows: Array<Record<string, string | number>>,
  ) => {
    const keys = rows.length ? Object.keys(rows[0]) : [];
    const csv = [
      keys.join(","),
      ...rows.map((row) =>
        keys
          .map(
            (key) =>
              `"${String(row[key] ?? "").replaceAll('"', '""')}"`,
          )
          .join(","),
      ),
    ].join("\n");
    const url = URL.createObjectURL(
      new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" }),
    );
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = name;
    anchor.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  };

  return (
    <PageFrame
      eyebrow="Operations / live view"
      title="Good day, team."
      subtitle="A clear read on what needs attention across Vhaya. All figures are supplied by the live operations API."
    >
      <div className="dash-toolbar">
        <span className="live">
          <i /> Live operations
        </span>
        <span className="refresh-stamp">Last refreshed {lastRefreshed}</span>
        <button
          className="button"
          onClick={() => {
            void overview.refetch();
            void activity.refetch();
          }}
          disabled={loading}
          data-testid="button-dashboard-refresh"
        >
          <RefreshCw size={13} /> Refresh
        </button>
        <div className="relative">
          <button
            className="button"
            onClick={() => setMenu(!menu)}
            data-testid="button-dashboard-refresh-menu"
          >
            <ChevronDown size={13} /> Auto-refresh {auto ? "on" : "off"}
          </button>
          {menu && (
            <div className="refresh-menu">
              <button
                onClick={() => {
                  setAuto(!auto);
                  setMenu(false);
                }}
                data-testid="button-toggle-auto-refresh"
              >
                {auto ? "Turn auto-refresh off" : "Enable every 5 minutes"}
              </button>
              <small>
                Automatic refresh never runs more often than every 5 minutes.
              </small>
            </div>
          )}
        </div>
        <button
          className="button"
          onClick={() => window.print()}
          aria-label="Print to PDF"
          data-testid="button-print-dashboard"
        >
          <Printer size={14} />
        </button>
        <button
          className="button"
          aria-label="Toggle dark mode"
          onClick={() => {
            const next = !dark;
            setDark(next);
            localStorage.setItem("vhaya-dark", String(next));
          }}
          data-testid="button-toggle-dark-mode"
        >
          {dark ? <Sun size={14} /> : <Moon size={14} />}
        </button>
      </div>
      {(overview.error || activity.error) && (
        <Feedback error>Could not load live operations data.</Feedback>
      )}
      <div className="kpis">
        {kpis.map(([label, value]) => (
          <article className="kpi" key={label}>
            <div className="kpi-top">
              {label}
              <Activity size={14} />
            </div>
            {loading ? (
              <div className="skeleton metric-skeleton" />
            ) : (
              <div className="kpi-value">{value}</div>
            )}
          </article>
        ))}
      </div>
      <div className="overview-grid dashboard-breakdown">
        <section className="panel chart-panel">
          <header className="panel-heading">
            <div>
              <span className="eyebrow">LAST 30 DAYS</span>
              <h2 className="panel-title">Support by category</h2>
              <p className="helper">
                {overview.data.support.last30Days} cases received in the last
                30 days.
              </p>
            </div>
            <button
              className="button small"
              onClick={() =>
                exportCsv(
                  "support-by-category.csv",
                  categories.map((item) => ({
                    category: item.category,
                    count: item.count,
                  })),
                )
              }
              data-testid="button-export-categories"
            >
              <Download size={13} /> CSV
            </button>
          </header>
          <div
            className="chart-container"
            role="img"
            aria-label="Support cases by category, last 30 days"
          >
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={categories}
                layout="vertical"
                margin={{ top: 4, right: 18, bottom: 4, left: 4 }}
              >
                <CartesianGrid
                  stroke="#e7eee9"
                  strokeDasharray="3 3"
                  horizontal={false}
                />
                <XAxis
                  type="number"
                  allowDecimals={false}
                  tick={{ fontSize: 10, fill: "#7d8d84" }}
                  axisLine={false}
                  tickLine={false}
                />
                <YAxis
                  type="category"
                  dataKey="category"
                  width={100}
                  tickFormatter={(value: string) => value.replaceAll("_", " ")}
                  tick={{ fontSize: 10, fill: "#5f7469" }}
                  axisLine={false}
                  tickLine={false}
                />
                <Tooltip
                  formatter={(value) => [value, "Cases"]}
                  labelFormatter={(value) =>
                    String(value).replaceAll("_", " ")
                  }
                />
                <Bar
                  dataKey="count"
                  fill="#21816f"
                  radius={[0, 5, 5, 0]}
                  maxBarSize={20}
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>
        <section className="panel chart-panel">
          <header className="panel-heading">
            <div>
              <span className="eyebrow">LAST 30 DAYS</span>
              <h2 className="panel-title">Support by priority</h2>
              <p className="helper">
                Aggregates reflect the same 30-day reporting window.
              </p>
            </div>
            <button
              className="button small"
              onClick={() =>
                exportCsv(
                  "support-by-priority.csv",
                  priorities.map((item) => ({
                    priority: item.priority,
                    count: item.count,
                  })),
                )
              }
              data-testid="button-export-priorities"
            >
              <Download size={13} /> CSV
            </button>
          </header>
          <div
            className="chart-container"
            role="img"
            aria-label="Support cases by priority, last 30 days"
          >
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={priorities}
                margin={{ top: 8, right: 14, bottom: 8, left: 4 }}
              >
                <CartesianGrid
                  stroke="#e7eee9"
                  strokeDasharray="3 3"
                  vertical={false}
                />
                <XAxis
                  dataKey="priority"
                  tick={{ fontSize: 10, fill: "#5f7469" }}
                  axisLine={false}
                  tickLine={false}
                />
                <YAxis
                  allowDecimals={false}
                  tick={{ fontSize: 10, fill: "#7d8d84" }}
                  axisLine={false}
                  tickLine={false}
                />
                <Tooltip formatter={(value) => [value, "Cases"]} />
                <Bar
                  dataKey="count"
                  fill="#c58b45"
                  radius={[5, 5, 0, 0]}
                  maxBarSize={42}
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>
      </div>
      <section className="panel activity-panel">
        <header className="panel-heading">
          <div>
            <span className="eyebrow">AUDIT TRAIL</span>
            <h2 className="panel-title">Recent activity</h2>
          </div>
          <span className="tag">{activity.data.length} records</span>
        </header>
        <QueryState
          loading={false}
          empty={!activity.data.length}
          retry={() => undefined}
        />
        {!!activity.data.length && (
          <div className="activity-list">
            {activity.data.map((item) => (
              <div className="activity-row" key={item.id}>
                <time>{date(item.createdAt)}</time>
                <div>
                  <strong>{item.summary}</strong>
                  <span>
                    {item.actor} · {item.subject}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </PageFrame>
  );
}
