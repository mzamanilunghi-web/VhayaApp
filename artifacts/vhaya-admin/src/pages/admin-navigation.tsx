import { Link, useLocation } from "wouter";
import { useAuth } from "@/lib/auth-context";

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

export function LegacyRouteNav() {
  const [path] = useLocation();
  const auth = useAuth();
  if (auth.status !== "ready") return null;

  return (
    <nav className="legacy-route-nav" aria-label="Operations navigation">
      {links.map(([href, label]) => (
        <Link
          href={href}
          key={href}
          className={`global-nav-link ${path === href ? "active" : ""}`}
          data-testid={`nav-${label.toLowerCase().replaceAll(" ", "-")}`}
        >
          {label}
        </Link>
      ))}
    </nav>
  );
}
