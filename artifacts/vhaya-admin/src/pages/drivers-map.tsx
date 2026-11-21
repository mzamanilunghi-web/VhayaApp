import { useEffect, useMemo, useRef, useState } from "react";
import { CircleHelp, Clock3, MapPin, RefreshCw, Search, ShieldCheck, Users } from "lucide-react";
import L from "leaflet";
import { MapContainer, Marker, TileLayer, ZoomControl, useMap } from "react-leaflet";
import { PageFrame } from "@/pages/admin-workspace";
import { useOnlineDrivers } from "@/hooks/useAdminData";
import type { AdminOnlineDriver } from "@/lib/vhaya-api";

const POLOKWANE: [number, number] = [-23.9045, 29.4689];

function formatStamp(value: number | null | undefined) {
  if (!value) return "Not recorded";
  return new Intl.DateTimeFormat("en-ZA", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function timeAgo(value: number | null | undefined) {
  if (!value) return "Time unavailable";
  const seconds = Math.max(0, Math.floor((Date.now() - value) / 1000));
  if (seconds < 60) return "Just now";
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} hr ago`;
  return `${Math.floor(seconds / 86400)} days ago`;
}

function plotted(driver: AdminOnlineDriver): driver is AdminOnlineDriver & {
  location: NonNullable<AdminOnlineDriver["location"]>;
} {
  return driver.locationState === "current" && driver.location !== null;
}

function statusLabel(driver: AdminOnlineDriver) {
  if (plotted(driver)) return "Current";
  if (driver.locationState === "stale") return "Stale · not plotted";
  if (driver.locationState === "waiting") return "Waiting for location";
  return "Location unavailable";
}

function FocusDriver({ driver }: { driver: AdminOnlineDriver | null }) {
  const map = useMap();
  const latitude = driver && plotted(driver) ? driver.location.latitude : null;
  const longitude = driver && plotted(driver) ? driver.location.longitude : null;

  useEffect(() => {
    if (latitude === null || longitude === null) return;
    map.flyTo([latitude, longitude], Math.max(map.getZoom(), 14), { duration: 0.55 });
  }, [driver?.driverId, latitude, longitude, map]);

  return null;
}

function MarkerLayer({
  drivers,
  selectedId,
  onSelect,
}: {
  drivers: AdminOnlineDriver[];
  selectedId: string | null;
  onSelect: (driverId: string) => void;
}) {
  const currentDrivers = drivers.filter(plotted);

  return (
    <>
      {currentDrivers.map((driver, index) => {
        const isSelected = driver.driverId === selectedId;
        const icon = L.divIcon({
          className: "vhaya-driver-marker-shell",
          html: `<span class="vhaya-driver-marker${isSelected ? " is-selected" : ""}"><i></i></span>`,
          iconSize: [28, 28],
          iconAnchor: [14, 14],
        });
        return (
          <Marker
            key={driver.driverId}
            position={[driver.location.latitude, driver.location.longitude]}
            icon={icon}
            title={`${driver.displayName}, current location`}
            eventHandlers={{ click: () => onSelect(driver.driverId) }}
            zIndexOffset={isSelected ? 1000 : index}
          />
        );
      })}
    </>
  );
}

function FitCurrentDrivers({
  drivers,
  fitKey,
}: {
  drivers: AdminOnlineDriver[];
  fitKey: string;
}) {
  const map = useMap();
  const driversRef = useRef(drivers);
  driversRef.current = drivers;

  useEffect(() => {
    if (!fitKey) return;
    const points = driversRef.current
      .filter(plotted)
      .map((driver) => [driver.location.latitude, driver.location.longitude] as [number, number]);
    if (!points.length) return;
    map.fitBounds(L.latLngBounds(points).pad(0.18), {
      maxZoom: 14,
      animate: true,
      duration: 0.55,
    });
  }, [fitKey, map]);

  return null;
}

function DriverRow({
  driver,
  selected,
  onSelect,
}: {
  driver: AdminOnlineDriver;
  selected: boolean;
  onSelect: () => void;
}) {
  const isCurrent = plotted(driver);
  const lastSeen = driver.location?.updatedAt ?? driver.lastLocationAt;
  const vehicleDetail = [driver.vehicleColor, driver.vehicle].filter(Boolean).join(" ");
  const detailLine = [driver.city, vehicleDetail || "Vehicle not listed"].filter(Boolean).join(" · ");
  const badge = isCurrent
    ? "Current"
    : driver.locationState === "stale"
      ? "Stale"
      : driver.locationState === "waiting"
        ? "Waiting"
        : "Unavailable";
  return (
    <button
      className={`drivers-map-row${selected ? " is-selected" : ""}`}
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      aria-label={`Select ${driver.displayName}, ${statusLabel(driver)}`}
      data-testid={`driver-map-row-${driver.driverId}`}
    >
      <span className={`driver-state-mark ${isCurrent ? "current" : driver.locationState}`}>
        <i />
      </span>
      <span className="drivers-map-row-copy">
        <strong>{driver.displayName}</strong>
        <span>{detailLine}</span>
        <span className="driver-location-meta">
          {isCurrent
            ? `Location updated ${timeAgo(lastSeen)}`
            : driver.locationState === "stale" && lastSeen
              ? `Last location ${timeAgo(lastSeen)}`
              : statusLabel(driver)}
        </span>
      </span>
      <span className={`driver-status-label ${isCurrent ? "current" : driver.locationState}`}>
        {badge}
      </span>
    </button>
  );
}

export function DriversMapPage() {
  const query = useOnlineDrivers(15_000);
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const drivers = query.data?.drivers ?? [];
  const currentDrivers = useMemo(() => drivers.filter(plotted), [drivers]);
  const currentDriverIds = currentDrivers
    .map((driver) => driver.driverId)
    .sort()
    .join("|");
  const staleCount = drivers.filter((driver) => driver.locationState === "stale").length;
  const waitingCount = drivers.filter((driver) => driver.locationState === "waiting").length;
  const visibleDrivers = useMemo(() => {
    const term = search.trim().toLocaleLowerCase();
    const priority = { current: 0, stale: 1, waiting: 2 };
    return [...drivers]
      .filter((driver) => {
        if (!term) return true;
        return [driver.displayName, driver.city, driver.vehicle, driver.vehicleColor]
          .some((value) => value?.toLocaleLowerCase().includes(term));
      })
      .sort((a, b) => priority[a.locationState] - priority[b.locationState]
        || a.displayName.localeCompare(b.displayName));
  }, [drivers, search]);
  const selectedDriver = drivers.find((driver) => driver.driverId === selectedId) ?? null;

  useEffect(() => {
    if (selectedId && !selectedDriver) setSelectedId(null);
  }, [selectedId, selectedDriver]);

  const loadingFirstPage = query.isLoading && !query.data;
  const failedFirstPage = query.isError && !query.data;

  return (
    <PageFrame
      eyebrow="Operations / live view"
      title="Drivers map"
      subtitle="Approved drivers who are online. Only locations updated in the last 90 seconds appear on the map."
    >
      <section className="drivers-map-shell" aria-label="Online driver locations">
        <header className="drivers-map-toolbar">
          <div className="drivers-map-summary">
            <span className="drivers-live-indicator"><i /> Live feed</span>
            <span className="drivers-map-summary-divider" />
            <span className="drivers-map-summary-item"><strong>{drivers.length}</strong> online</span>
            <span className="drivers-map-summary-item"><strong>{currentDrivers.length}</strong> plotted</span>
          </div>
          <div className="drivers-map-toolbar-actions">
            <span className="drivers-feed-stamp" data-testid="drivers-map-generated-at">
              <Clock3 size={13} />
              <span>Feed generated <strong>{formatStamp(query.data?.generatedAt)}</strong></span>
            </span>
            <button
              type="button"
              className="button drivers-refresh-button"
              onClick={() => void query.refetch()}
              disabled={query.isFetching}
              aria-label={query.isFetching ? "Refreshing driver locations" : "Refresh driver locations"}
              data-testid="button-refresh-drivers-map"
            >
              <RefreshCw size={14} className={query.isFetching ? "refresh-turning" : ""} />
              {query.isFetching ? "Refreshing" : "Refresh"}
            </button>
          </div>
        </header>

        {query.isError && query.data && (
          <div className="drivers-map-notice error" role="alert" data-testid="drivers-map-refresh-error">
            <span>Could not refresh the feed. Showing the last successful snapshot.</span>
            <button type="button" onClick={() => void query.refetch()} data-testid="button-retry-drivers-map">Try again</button>
          </div>
        )}

        {loadingFirstPage ? (
          <div className="drivers-map-loading" role="status" aria-label="Loading online drivers" data-testid="drivers-map-loading">
            <aside className="drivers-map-rail-skeleton">
              <div className="skeleton" />
              <div className="skeleton" />
              <div className="skeleton" />
              <div className="skeleton" />
            </aside>
            <div className="drivers-map-canvas-skeleton"><span className="map-skeleton-lines" /></div>
          </div>
        ) : failedFirstPage ? (
          <div className="drivers-map-query-state error" role="alert" data-testid="drivers-map-error">
            <span className="drivers-state-icon"><RefreshCw size={18} /></span>
            <strong>Driver locations are unavailable</strong>
            <p>{query.error instanceof Error ? query.error.message : "The live feed could not be reached."}</p>
            <button type="button" className="button primary" onClick={() => void query.refetch()} data-testid="button-retry-drivers-map">
              <RefreshCw size={13} /> Try again
            </button>
          </div>
        ) : (
          <div className="drivers-map-layout">
            <aside className="drivers-map-rail" aria-label="Online driver list">
              <div className="drivers-rail-heading">
                <div>
                  <span className="eyebrow">LIVE OPERATIONS</span>
                  <h2>Online now <span data-testid="drivers-map-online-count">{drivers.length}</span></h2>
                </div>
                <span className="drivers-rail-icon"><Users size={15} /></span>
              </div>

              <label className="drivers-map-search">
                <Search size={14} aria-hidden="true" />
                <input
                  type="search"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="Find a driver or vehicle"
                  aria-label="Search online drivers"
                  data-testid="input-search-drivers-map"
                />
              </label>

              <div className="drivers-map-list-meta">
                <span>{visibleDrivers.length} {visibleDrivers.length === 1 ? "driver" : "drivers"}</span>
                <span className="drivers-list-legend"><i /> Plotted when current</span>
              </div>
              <div className="drivers-map-list" role="list" data-testid="drivers-map-driver-list">
                {visibleDrivers.map((driver) => (
                  <DriverRow
                    key={driver.driverId}
                    driver={driver}
                    selected={driver.driverId === selectedId}
                    onSelect={() => setSelectedId(driver.driverId)}
                  />
                ))}
                {!visibleDrivers.length && (
                  <div className="drivers-list-empty" data-testid="drivers-map-list-empty">
                    <span className="drivers-empty-mark"><Search size={15} /></span>
                    <strong>{drivers.length ? "No matching drivers" : "No drivers online"}</strong>
                    <span>{drivers.length ? "Try another name, city or vehicle." : "Approved drivers will appear here when they come online."}</span>
                    {search && <button type="button" onClick={() => setSearch("")} data-testid="button-clear-driver-search">Clear search</button>}
                  </div>
                )}
              </div>

              <div className="drivers-rail-footer" data-testid="drivers-map-location-summary">
                <div><span className="driver-state-mark current"><i /></span><span>Current locations</span><strong>{currentDrivers.length}</strong></div>
                <div><span className="driver-state-mark stale"><i /></span><span>Stale, not plotted</span><strong>{staleCount}</strong></div>
                <div><span className="driver-state-mark waiting"><i /></span><span>Waiting for location</span><strong>{waitingCount}</strong></div>
              </div>
            </aside>

            <section className="drivers-map-main" aria-label="Map of current driver locations">
              <div className="drivers-map-caption">
                <div className="map-caption-title"><MapPin size={14} /><strong>Live driver locations</strong></div>
                <span>OpenStreetMap</span>
              </div>
              <div className="drivers-map-canvas" data-testid="drivers-map-canvas">
                <MapContainer
                  center={POLOKWANE}
                  zoom={12}
                  minZoom={8}
                  maxZoom={18}
                  scrollWheelZoom
                  zoomControl={false}
                  className="vhaya-leaflet-map"
                  aria-label="Map showing fresh current locations of online drivers"
                >
                  <TileLayer
                    attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                    url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                  />
                  <ZoomControl position="bottomright" />
                  <MarkerLayer drivers={drivers} selectedId={selectedId} onSelect={setSelectedId} />
                  <FitCurrentDrivers drivers={drivers} fitKey={currentDriverIds} />
                  <FocusDriver driver={selectedDriver} />
                </MapContainer>
                {!currentDrivers.length && (
                  <div className="drivers-map-empty-overlay" data-testid="drivers-map-no-current">
                    <span className="drivers-empty-mark"><MapPin size={16} /></span>
                    <strong>No current locations to plot</strong>
                    <span>Stale and waiting locations are intentionally left off this map.</span>
                  </div>
                )}
                <div className="drivers-map-privacy-note">
                  <ShieldCheck size={13} />
                  <span>Only current locations are shown</span>
                </div>
              </div>
              <footer className="drivers-map-footnote">
                <CircleHelp size={13} />
                <span>Location freshness is determined by the live feed. Stale and waiting drivers remain listed, but are not plotted.</span>
              </footer>
            </section>
          </div>
        )}
      </section>
    </PageFrame>
  );
}

export default DriversMapPage;
