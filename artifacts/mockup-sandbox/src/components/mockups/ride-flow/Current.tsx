import React from "react";
import "./_group.css";

type Status = "requested" | "countered" | "accepted" | "arrived" | "in_progress" | "completed" | "cancelled" | "unfulfilled";
type JourneyCopy = { title: string; detail: string; marker: string; stage: number; terminal?: "cancelled" | "unfulfilled" | "completed" };
type DriverProfile = { name: string; vehicleColor: string; vehicle: string; registration: string; verificationStatus: string };
type RideTrip = { id: string; status: Status; pickup: string; destination: string; driverProfile?: DriverProfile; unfulfillmentReason?: string };

const JOURNEY_COPY: Record<Status, JourneyCopy> = {
  requested: { title: "Finding your driver", detail: "Your request is with nearby drivers. We’ll keep this journey updated.", marker: "Request sent", stage: 0 },
  countered: { title: "Finding your driver", detail: "A fare counter has come in. Your ride is still being arranged.", marker: "Request in progress", stage: 0 },
  accepted: { title: "Your driver is on the way", detail: "Your pickup is confirmed. Keep an eye out for your driver.", marker: "Driver confirmed", stage: 1 },
  arrived: { title: "Meet your driver", detail: "Your driver is at the pickup point. Take a moment to find one another.", marker: "At pickup", stage: 2 },
  in_progress: { title: "Heading to your destination", detail: "You’re on your way. Your destination is below.", marker: "On the way", stage: 3 },
  completed: { title: "You’ve arrived", detail: "Your journey is complete. Thanks for riding with Vhaya.", marker: "Journey complete", stage: 4, terminal: "completed" },
  cancelled: { title: "This ride was cancelled", detail: "This journey won’t continue. You can request another ride when you’re ready.", marker: "Ride ended", stage: -1, terminal: "cancelled" },
  unfulfilled: { title: "No driver was available", detail: "This request couldn’t be matched. Your pickup and destination are kept here for reference.", marker: "Request ended", stage: -1, terminal: "unfulfilled" },
};
const STEPS = ["Request", "Pickup", "Destination"];
const trip: RideTrip = {
  id: "WF-2841",
  status: "accepted",
  pickup: "The Garden, 11 Hope Street, Gardens",
  destination: "Cape Town International Airport",
  driverProfile: { name: "Lerato Mokoena", vehicleColor: "Silver", vehicle: "Toyota Corolla", registration: "CA 482-917", verificationStatus: "approved" },
};

/** Faithful web port of the app's RiderJourney component and its layout values. */
export function Current() {
  const journey = JOURNEY_COPY[trip.status];
  const isTerminal = journey.stage < 0;
  const activeStep = journey.stage <= 0 ? 0 : journey.stage <= 2 ? 1 : 2;
  const progressWidth = journey.stage <= 0 ? "13%" : journey.stage === 1 ? "43%" : journey.stage === 2 ? "50%" : "100%";
  const accent = journey.terminal === "cancelled" || journey.terminal === "unfulfilled" ? "var(--wf-danger)" : "var(--wf-primary)";
  const verified = trip.driverProfile?.verificationStatus === "approved";

  return (
    <main className="wf-current">
      <section className="wf-current-card" aria-live="polite" aria-label={`Ride progress: ${journey.title}. Pickup: ${trip.pickup}. Destination: ${trip.destination}.`} data-testid={`rider-journey-${trip.id}`}>
        <header className="wf-current-head">
          <div className="wf-current-heading">
            <div className="wf-current-eyebrow">YOUR JOURNEY</div>
            <h2 className="wf-current-title">{journey.title}</h2>
          </div>
          <div className="wf-current-pill" aria-label={journey.marker} style={{ backgroundColor: isTerminal ? "var(--wf-muted)" : "var(--wf-accent)" }}>
            <span className="wf-current-dot" style={{ backgroundColor: accent }} />
            <span>{journey.marker}</span>
          </div>
        </header>
        <p className="wf-current-detail">{journey.detail}</p>
        {!isTerminal && <div className="wf-current-progress" aria-label={`Progress: ${STEPS[activeStep]} stage`} role="progressbar" aria-valuemin={0} aria-valuemax={2} aria-valuenow={activeStep}>
          <div className="wf-track"><div className="wf-track-fill" style={{ width: progressWidth, backgroundColor: accent }} /></div>
          <div className="wf-current-steps">{STEPS.map((step, index) => <span key={step} style={{ color: index <= activeStep ? "var(--wf-fg)" : "var(--wf-subtle)" }}>{step}</span>)}</div>
        </div>}
        <div className="wf-current-route">
          <div className="wf-route-rail" aria-hidden="true"><i className="wf-route-origin" /><i className="wf-route-line" /><i className="wf-route-destination" style={{ borderColor: "var(--wf-primary)" }} /></div>
          <div className="wf-locations">
            <div><div className="wf-loc-label">PICKUP</div><div className="wf-loc-name">{trip.pickup}</div></div>
            <div><div className="wf-loc-label">DESTINATION</div><div className="wf-loc-name">{trip.destination}</div></div>
          </div>
        </div>
        {trip.driverProfile && <div className="wf-current-driver" aria-label={`Driver ${trip.driverProfile.name}, ${trip.driverProfile.vehicleColor} ${trip.driverProfile.vehicle}, registration ${trip.driverProfile.registration}${verified ? ", verified" : ""}`}>
          <div className="wf-initial">{trip.driverProfile.name.trim().charAt(0).toLocaleUpperCase()}</div>
          <div className="wf-driver-copy">
            <div className="wf-driver-name">{trip.driverProfile.name}{verified ? " · verified" : ""}</div>
            <div className="wf-driver-car">{trip.driverProfile.vehicleColor} {trip.driverProfile.vehicle} · {trip.driverProfile.registration}</div>
          </div>
        </div>}
        {trip.status === "unfulfilled" && trip.unfulfillmentReason && <div style={{ marginTop: 8, color: "var(--wf-danger)", fontSize: 11 }}>{trip.unfulfillmentReason}</div>}
      </section>
    </main>
  );
}
