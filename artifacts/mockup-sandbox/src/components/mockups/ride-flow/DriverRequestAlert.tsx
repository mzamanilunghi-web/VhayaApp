import React, { useState } from "react";
import "./_group.css";

export function DriverRequestAlert() {
  const [response, setResponse] = useState<"accepted" | "declined" | null>(null);
  const [showCounter, setShowCounter] = useState(true);
  const reset = () => { setResponse(null); setShowCounter(true); };
  return <main className="wf-driver"><section className="wf-device">
    <div className="wf-statusbar"><span>9:41</span><span>•••　 4G　 ▰</span></div>
    <header className="wf-brandbar"><div className="wf-wordmark">Vhaya<b>.</b> <span style={{ fontSize: 10, color: "#727970", letterSpacing: ".2px", fontWeight: 600 }}>DRIVER</span></div><button className="wf-icon-button" aria-label="Close alert" onClick={reset}>×</button></header>
    <section className="wf-screen" key={response ?? "incoming"}>
      <div className="wf-kicker">INCOMING RIDE REQUEST</div>
      <h1>New trip<br />near you.</h1>
      <div className="wf-request-urgent"><span>Request fare</span><strong>R 86.50</strong></div>
      <div className="wf-card wf-driver-route">
        <div className="wf-route-card" style={{ padding: 0 }}>
          <div className="wf-route-symbols" aria-hidden="true"><i /><i /><i /></div>
          <div className="wf-route-data">
            <div className="wf-route-item"><label>PICKUP · SEA POINT</label><strong>21 Regent Road, Sea Point</strong></div>
            <div className="wf-route-item"><label>DESTINATION · CAPE TOWN CBD</label><strong>Long Street, City Centre</strong></div>
          </div>
        </div>
        <div className="wf-driver-facts">
          <div className="wf-driver-fact"><label>Category</label><strong>Everyday</strong></div>
          <div className="wf-driver-fact"><label>Route</label><strong>5.8 km</strong></div>
          <div className="wf-driver-fact"><label>Pickup</label><strong>Sea Point</strong></div>
        </div>
      </div>
      {showCounter && <div className="wf-counter"><div><small>Rider’s counteroffer</small><strong>R 94.00</strong></div><em>+ R 7.50</em></div>}
      {!showCounter && <div className="wf-counter"><div><small>Rider’s counteroffer</small><strong>No counteroffer</strong></div><span style={{ color: "#727970", fontSize: 10 }}>Original fare R 86.50</span></div>}
      {response ? <div className="wf-confirmed" role="status">{response === "accepted" ? "Request accepted. The trip details are ready in your active rides." : "Request declined. You’ll remain available for nearby requests."}</div> :
        <div className="wf-driver-actions">
          <button className="wf-button" onClick={() => setResponse("accepted")}>Accept ride</button>
          <button className="wf-button secondary" onClick={() => setResponse("declined")}>Decline</button>
        </div>}
      <button className="wf-button secondary" style={{ width: "100%", marginTop: 9 }} onClick={() => setShowCounter((value) => !value)}>{showCounter ? "View original fare" : "View rider counteroffer"}</button>
      <p className="wf-compact-note">Review pickup, destination and fare before accepting. No countdown is shown.</p>
      {response && <button className="wf-button" style={{ marginTop: 14 }} onClick={reset}>Preview request again</button>}
    </section>
  </section></main>;
}
