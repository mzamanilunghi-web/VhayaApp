import React, { useState } from "react";
import "./_group.css";

type Stage = "matching" | "found" | "authorize" | "tracking";
const labels: Stage[] = ["matching", "found", "authorize", "tracking"];

function StatusBar() {
  return <div className="wf-statusbar"><span>9:41</span><span>•••　 4G　 ▰</span></div>;
}

export function StagedRideFlow() {
  const [stage, setStage] = useState<Stage>("matching");
  const [authorized, setAuthorized] = useState(false);
  const stageIndex = labels.indexOf(stage);
  const advance = () => {
    if (stage === "matching") setStage("found");
    else if (stage === "found") setStage("authorize");
    else if (stage === "authorize") { setAuthorized(true); setStage("tracking"); }
  };
  const reset = () => { setStage("matching"); setAuthorized(false); };

  return <main className="wf-prototype"><section className="wf-device">
    <StatusBar />
    <header className="wf-brandbar"><div className="wf-wordmark">Vhaya<b>.</b></div><button className="wf-icon-button" aria-label="Close prototype" onClick={reset}>×</button></header>
    <div className="wf-stepper" aria-label={`Step ${stageIndex + 1} of 4`}>{labels.map((item, i) => <i key={item} className={i <= stageIndex ? "active" : ""} />)}</div>
    {stage === "matching" && <section className="wf-screen" key={stage}>
      <div className="wf-kicker">RIDE REQUEST SENT</div>
      <h1>We’re finding<br />your driver.</h1>
      <p>Your request is with nearby drivers. We’ll update you as soon as someone accepts.</p>
      <div className="wf-map" aria-label="Map around Cape Town"><span className="wf-map-label one">Gardens</span><span className="wf-map-label two">City Bowl</span><span className="wf-map-point" /></div>
      <div className="wf-card wf-route-card">
        <div className="wf-route-symbols" aria-hidden="true"><i /><i /><i /></div>
        <div className="wf-route-data">
          <div className="wf-route-item"><label>PICKUP</label><strong>The Garden, Gardens</strong></div>
          <div className="wf-route-item"><label>DESTINATION</label><strong>Cape Town International Airport</strong></div>
        </div>
      </div>
      <div className="wf-indeterminate" aria-label="Searching for an available driver" />
      <div className="wf-quiet-row"><i className="wf-pulse" />Still looking nearby. There’s no countdown; we’ll let you know when a driver responds.</div>
      <div className="wf-bottom"><button className="wf-button" onClick={advance}>Show driver found state</button><p className="wf-safe-note">You can cancel this request while we look for a driver.</p></div>
    </section>}
    {stage === "found" && <section className="wf-screen wf-found" key={stage}>
      <div className="wf-found-mark" aria-hidden="true">✓</div>
      <div className="wf-kicker">DRIVER FOUND</div>
      <h1>Your ride is confirmed.</h1>
      <p>Lerato has accepted your request and is heading to your pickup.</p>
      <div className="wf-card wf-driver-card">
        <div className="wf-avatar">LM</div><div><div className="wf-driver-name">Lerato Mokoena <span style={{ color: "#607861" }}>· verified</span></div><div className="wf-meta">4.9 rating · 218 Vhaya rides</div><div className="wf-meta">Silver Toyota Corolla · CA 482-917</div></div><i className="wf-car-thumb" aria-label="Silver sedan illustration" />
      </div>
      <div className="wf-card" style={{ marginTop: 11 }}>
        <div className="wf-route-item"><label>PICKUP</label><strong>The Garden, 11 Hope Street, Gardens</strong></div>
        <div className="wf-fare-row"><span>Agreed fare · Everyday</span><strong>R 184.50</strong></div>
      </div>
      <div className="wf-bottom"><button className="wf-button" onClick={advance}>Continue to card authorization</button><button className="wf-button secondary" onClick={() => setStage("matching")}>Back to matching</button></div>
    </section>}
    {stage === "authorize" && <section className="wf-screen" key={stage}>
      <div className="wf-kicker">BEFORE YOUR RIDE</div>
      <h1>Authorize your<br />card hold.</h1>
      <p>A temporary hold confirms your payment method. You won’t be charged now.</p>
      <div className="wf-card" style={{ marginTop: 20 }}>
        <div className="wf-card-line"><span className="wf-card-icon">▤</span><div><strong>Visa ending in 4821</strong><span>Default payment method</span></div><span style={{ marginLeft: "auto", fontSize: 11, color: "#66766a" }}>Selected</span></div>
        <div className="wf-authorization-amount">R 184.50</div><div className="wf-meta">Maximum fare hold · ZAR</div>
      </div>
      <div className="wf-hold-note">This is a card authorization, not a charge. The final fare is captured only after the ride is complete. If the ride does not go ahead, the hold is released.</div>
      <ul className="wf-checklist"><li><b>✓</b><span>Protected payment handled securely</span></li><li><b>✓</b><span>Final amount is based on your agreed fare</span></li><li><b>✓</b><span>No fare captured before ride completion</span></li></ul>
      <div className="wf-bottom"><button className="wf-button" onClick={advance}>Authorize R 184.50</button><button className="wf-button secondary" onClick={() => setStage("found")}>Back to driver details</button></div>
    </section>}
    {stage === "tracking" && <section className="wf-screen" key={stage}>
      <div className="wf-kicker">{authorized ? "CARD HOLD AUTHORIZED" : "LIVE RIDE"}</div>
      <h1>{authorized ? "You’re all set." : "Your ride is underway."}</h1>
      <p>{authorized ? "Your fare is reserved on your card, not charged. It will be captured after your ride is complete." : "Lerato is on the way to your pickup."}</p>
      <div className="wf-map wf-tracking-map"><span className="wf-map-label one">Gardens</span><span className="wf-map-label two">Airport</span><span className="wf-map-point" style={{ left: "58%", top: "55%" }} /></div>
      <div className="wf-card">
        <div className="wf-driver-mini"><div className="wf-avatar">LM</div><div><div className="wf-driver-name">Lerato Mokoena · verified</div><div className="wf-meta">Silver Toyota Corolla · CA 482-917</div></div><span className="wf-live-label">ON THE WAY</span></div>
        <div className="wf-progress-line"><span /></div>
        <div className="wf-mini-route"><span>Gardens<br /><span style={{ color: "#727970", fontWeight: 500 }}>Pickup</span></span><span>Cape Town International Airport<br /><span style={{ color: "#727970", fontWeight: 500 }}>Destination</span></span></div>
        <div className="wf-fare-row"><span>Fare · Everyday</span><strong>R 184.50</strong></div>
      </div>
      <div className="wf-bottom"><button className="wf-button" onClick={reset}>Start another preview</button><p className="wf-safe-note">Your driver and vehicle details stay here while you travel.</p></div>
    </section>}
  </section></main>;
}
