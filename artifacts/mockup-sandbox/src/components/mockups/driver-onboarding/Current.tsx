import { ArrowLeft, ArrowRight, Save, Truck, X } from "lucide-react";
import "./_group.css";

const steps = ["Your details", "Vehicle", "Documents", "Review"];

export function Current() {
  return (
    <main className="do-current">
      <section className="do-device" aria-label="Driver application vehicle step">
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
            <input className="do-input" value="Toyota Corolla" readOnly />
          </label>
          <label className="do-field">
            <span className="do-label">Colour</span>
            <input className="do-input" value="White" readOnly />
          </label>
          <label className="do-field">
            <span className="do-label">Registration number</span>
            <input className="do-input" value="ABC 123 GP" readOnly />
          </label>
          <label className="do-field">
            <span className="do-label">Driver licence expiry</span>
            <input className="do-input" placeholder="YYYY-MM-DD" readOnly />
          </label>
          <label className="do-field">
            <span className="do-label">Operating licence / permit reference</span>
            <input
              className="do-input"
              placeholder="Reference number, if available"
              readOnly
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
    </main>
  );
}
