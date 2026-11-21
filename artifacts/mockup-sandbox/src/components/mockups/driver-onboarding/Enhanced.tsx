import { useMemo, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Save,
  Search,
  Truck,
  X,
} from "lucide-react";
import "./_group.css";

const steps = ["Your details", "Vehicle", "Documents", "Review"];
const vehicles = [
  "Toyota Corolla",
  "Toyota Corolla Cross",
  "Toyota Starlet",
  "Toyota Hilux",
  "Toyota Fortuner",
  "Toyota Urban Cruiser",
  "Volkswagen Polo Vivo",
  "Volkswagen Polo",
  "Volkswagen T-Cross",
  "Volkswagen Tiguan",
  "Suzuki Swift",
  "Suzuki Fronx",
  "Suzuki Baleno",
  "Hyundai Grand i10",
  "Hyundai i20",
  "Hyundai Creta",
  "Kia Picanto",
  "Kia Sonet",
  "Kia Seltos",
  "Ford Ranger",
  "Ford Everest",
  "Nissan Magnite",
  "Nissan Navara",
  "Renault Kwid",
  "Renault Kiger",
  "Isuzu D-Max",
  "GWM P-Series",
  "Haval Jolion",
  "BMW 3 Series",
  "Mercedes-Benz C-Class",
  "Audi A3",
  "Mazda CX-5",
  "Chery Tiggo 4 Pro",
  "Mahindra XUV 3XO",
];

function isoDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function monthName(date: Date) {
  return date.toLocaleDateString("en-ZA", { month: "long", year: "numeric" });
}

export function Enhanced() {
  const [vehicle, setVehicle] = useState("Toyota Corolla");
  const [vehicleSearchOpen, setVehicleSearchOpen] = useState(false);
  const [registration, setRegistration] = useState("ABC 123 GP");
  const [expiry, setExpiry] = useState("");
  const [calendarOpen, setCalendarOpen] = useState(false);
  const [calendarMonth, setCalendarMonth] = useState(new Date(2026, 10, 1));
  const [selectedDay, setSelectedDay] = useState<number | null>(null);

  const suggestions = useMemo(() => {
    const query = vehicle.trim().toLowerCase();
    const matches = vehicles.filter((item) => item.toLowerCase().includes(query));
    return (query ? matches : vehicles.slice(0, 6)).slice(0, 6);
  }, [vehicle]);

  const daysInMonth = new Date(
    calendarMonth.getFullYear(),
    calendarMonth.getMonth() + 1,
    0,
  ).getDate();
  const firstWeekday = new Date(
    calendarMonth.getFullYear(),
    calendarMonth.getMonth(),
    1,
  ).getDay();

  const chooseDate = () => {
    if (!selectedDay) return;
    const date = new Date(
      calendarMonth.getFullYear(),
      calendarMonth.getMonth(),
      selectedDay,
    );
    const value = isoDate(date);
    setExpiry(value);
    setCalendarOpen(false);
  };

  return (
    <main className="do-enhanced">
      <section className="do-device do-enhanced-device" aria-label="Enhanced driver application vehicle step">
        <header className="do-header">
          <h1 className="do-title">Drive with Vhaya</h1>
          <Truck className="do-header-icon" aria-hidden="true" />
        </header>
        <p className="do-description">
          Apply in a few clear steps, save your progress, and track staff review.
          Driver access stays off until your checks are approved.
        </p>
        <nav className="do-progress" aria-label="Application steps">
          {steps.map((label, index) => (
            <div
              className={`do-step ${index < 2 ? "complete" : ""} ${index === 1 ? "active" : ""}`}
              key={label}
            >
              <span className="do-step-dot">{index + 1}</span>
              <span>{label}</span>
            </div>
          ))}
        </nav>

        <h2 className="do-section-title">Vehicle details</h2>
        <div className="do-fields">
          <label className="do-field">
            <span className="do-label">Make and model</span>
            <span className="do-search-wrap">
              <input
                className="do-input"
                value={vehicle}
                onChange={(event) => {
                  setVehicle(event.target.value);
                  setVehicleSearchOpen(true);
                }}
                onFocus={() => setVehicleSearchOpen(true)}
                onBlur={() => window.setTimeout(() => setVehicleSearchOpen(false), 160)}
                placeholder="Search make or model"
                aria-label="Search vehicle make and model"
                autoComplete="off"
              />
              <Search className="do-search-icon" aria-hidden="true" />
              {vehicleSearchOpen ? (
                <span className="do-suggestions" role="listbox">
                  {suggestions.map((item) => (
                    <button
                      className="do-suggestion"
                      key={item}
                      type="button"
                      onMouseDown={(event) => event.preventDefault()}
                      onClick={() => {
                        setVehicle(item);
                        setVehicleSearchOpen(false);
                      }}
                    >
                      {item}
                    </button>
                  ))}
                  {vehicle.trim() &&
                  !vehicles.some(
                    (item) => item.toLowerCase() === vehicle.trim().toLowerCase(),
                  ) ? (
                    <button
                      className="do-suggestion"
                      type="button"
                      onMouseDown={(event) => event.preventDefault()}
                      onClick={() => setVehicleSearchOpen(false)}
                    >
                      Use “{vehicle.trim()}” instead
                    </button>
                  ) : null}
                </span>
              ) : null}
            </span>
          </label>
          <label className="do-field">
            <span className="do-label">Colour</span>
            <input className="do-input" defaultValue="White" />
          </label>
          <label className="do-field">
            <span className="do-label">Registration number</span>
            <input
              className="do-input"
              value={registration}
              onChange={(event) =>
                setRegistration(event.target.value.toUpperCase().slice(0, 30))
              }
              onBlur={() => {
                const compact = registration.replace(/[\s-]/g, "");
                const gautengPattern = compact.match(/^([A-Z]{3})(\d{3})([A-Z]{2})$/);
                if (gautengPattern) {
                  setRegistration(
                    `${gautengPattern[1]} ${gautengPattern[2]} ${gautengPattern[3]}`,
                  );
                }
              }}
              aria-label="Vehicle registration number"
            />
            <span className="do-field-hint">
              We’ll format ABC123GP as ABC 123 GP and keep other plate styles.
            </span>
          </label>
          <div className="do-field">
            <span className="do-label">Driver licence expiry</span>
            <button
              className={`do-picker-trigger ${expiry ? "" : "is-empty"}`}
              type="button"
              onClick={() => {
                setCalendarMonth(new Date(2026, 10, 1));
                setSelectedDay(null);
                setCalendarOpen(true);
              }}
              aria-haspopup="dialog"
              aria-expanded={calendarOpen}
            >
              <span>{expiry || "Choose expiry date"}</span>
              <CalendarDays className="do-calendar-icon" aria-hidden="true" />
            </button>
          </div>
          <label className="do-field">
            <span className="do-label">Operating licence / permit reference</span>
            <input
              className="do-input"
              placeholder="Reference number, if available"
            />
          </label>
        </div>
        <p className="do-helper">
          No vehicle yet? Save a draft and return when you have vehicle details.
          Vhaya has not connected a rental partner.
        </p>
        <div className="do-actions">
          <button className="do-button secondary" type="button">
            <ArrowLeft /> Back
          </button>
          <button className="do-button" type="button">
            <ArrowRight /> Continue
          </button>
        </div>
        <div className="do-actions">
          <button className="do-button secondary" type="button">
            <Save /> Save draft
          </button>
          <button className="do-button secondary" type="button">
            <X /> Close
          </button>
        </div>
      </section>

      {calendarOpen ? (
        <div
          className="do-calendar-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) setCalendarOpen(false);
          }}
        >
          <section
            className="do-calendar-dialog"
            role="dialog"
            aria-modal="true"
            aria-label="Choose driver licence expiry date"
          >
            <div className="do-calendar-heading">
              <button
                className="do-calendar-nav"
                type="button"
                aria-label="Previous month"
                onClick={() =>
                  setCalendarMonth(
                    new Date(
                      calendarMonth.getFullYear(),
                      calendarMonth.getMonth() - 1,
                      1,
                    ),
                  )
                }
              >
                <ChevronLeft size={17} />
              </button>
              <strong>{monthName(calendarMonth)}</strong>
              <button
                className="do-calendar-nav"
                type="button"
                aria-label="Next month"
                onClick={() =>
                  setCalendarMonth(
                    new Date(
                      calendarMonth.getFullYear(),
                      calendarMonth.getMonth() + 1,
                      1,
                    ),
                  )
                }
              >
                <ChevronRight size={17} />
              </button>
            </div>
            <div className="do-calendar-week" aria-hidden="true">
              {["S", "M", "T", "W", "T", "F", "S"].map((day, index) => (
                <span key={`${day}-${index}`}>{day}</span>
              ))}
            </div>
            <div className="do-calendar-grid">
              {Array.from({ length: firstWeekday }, (_, index) => (
                <span className="do-calendar-blank" key={`blank-${index}`} />
              ))}
              {Array.from({ length: daysInMonth }, (_, index) => {
                const day = index + 1;
                return (
                  <button
                    className={`do-calendar-day ${selectedDay === day ? "selected" : ""}`}
                    key={day}
                    type="button"
                    aria-pressed={selectedDay === day}
                    onClick={() => setSelectedDay(day)}
                  >
                    {day}
                  </button>
                );
              })}
            </div>
            <div className="do-calendar-footer">
              <button
                className="do-button secondary"
                type="button"
                onClick={() => setCalendarOpen(false)}
              >
                Cancel
              </button>
              <button
                className="do-button"
                type="button"
                disabled={!selectedDay}
                onClick={chooseDate}
              >
                Use date
              </button>
            </div>
          </section>
        </div>
      ) : null}
    </main>
  );
}
