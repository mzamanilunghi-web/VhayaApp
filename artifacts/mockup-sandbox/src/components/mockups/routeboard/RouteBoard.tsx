import { useEffect, useState, type ReactNode } from 'react';
import './RouteBoard.css';

type IconName = 'route' | 'pin' | 'search' | 'crosshair' | 'home' | 'briefcase' | 'clock' | 'arrow' | 'map' | 'rides' | 'close' | 'car' | 'shield' | 'chevron' | 'check';

function Icon({ name, size = 16 }: { name: IconName; size?: number }) {
  const paths: Record<IconName, ReactNode> = {
    route: <><path d="M5 18a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z"/><path d="M19 10a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z"/><path d="M7 16h4a3 3 0 0 0 3-3V9a3 3 0 0 1 3-3h.5"/><path d="m16 4 2 2-2 2"/></>,
    pin: <><path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="2.4"/></>,
    search: <><circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 4 4"/></>,
    crosshair: <><circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="2.5"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2"/></>,
    home: <><path d="m3 10 9-7 9 7"/><path d="M5 9v11h14V9"/><path d="M9 20v-6h6v6"/></>,
    briefcase: <><rect x="3" y="7" width="18" height="14" rx="2"/><path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 12h18m-11 0v2h4v-2"/></>,
    clock: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
    arrow: <><path d="M5 12h14m-6-6 6 6-6 6"/></>,
    map: <><path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3V6Z"/><path d="M9 3v15m6-12v15"/></>,
    rides: <><path d="M5 17h14l1-6-2-5H6l-2 5 1 6Z"/><path d="M5 11h14M7 17v2m10-2v2"/><circle cx="7.5" cy="14" r=".8"/><circle cx="16.5" cy="14" r=".8"/></>,
    close: <><path d="m6 6 12 12M18 6 6 18"/></>,
    car: <><path d="m5 11 1.5-5h11l1.5 5"/><path d="M4 11h16l1 3v4h-2v-2H5v2H3v-4l1-3Z"/><circle cx="6.5" cy="14" r=".8"/><circle cx="17.5" cy="14" r=".8"/></>,
    shield: <><path d="M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11Z"/><path d="m9 12 2 2 4-4"/></>,
    chevron: <path d="m9 18 6-6-6-6"/>,
    check: <path d="m5 12 4 4L19 6"/>,
  };
  return (
    <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      {paths[name]}
    </svg>
  );
}

const places = [
  { name: 'Home', detail: 'Bendor, Polokwane', icon: 'home' as const, address: 'Bendor, Polokwane' },
  { name: 'Mall of the North', detail: 'R81, Bendor', icon: 'briefcase' as const, address: 'Mall of the North, Polokwane' },
  { name: 'Recent', detail: 'Platinum Park', icon: 'clock' as const, address: 'Platinum Park, Polokwane' },
];

export function RouteBoard() {
  const [pickup, setPickup] = useState('');
  const [destination, setDestination] = useState('');
  const [activeTab, setActiveTab] = useState<'book' | 'trips'>('book');
  const [showOptions, setShowOptions] = useState(false);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    if (!notice) return;
    const timeout = window.setTimeout(() => setNotice(''), 2800);
    return () => window.clearTimeout(timeout);
  }, [notice]);

  const choosePlace = (address: string) => {
    setDestination(address);
    setActiveTab('book');
    setNotice(`${address} added as your destination`);
  };

  return (
    <main className="wayfare-board">
      <div className="wb-scroll">
        <header className="wb-topbar">
          <div className="wb-brand">
            <div className="wb-mark"><Icon name="route" size={21} /></div>
            <div>
              <div className="wb-wordmark">Vhaya</div>
              <div className="wb-tagline">CITY RIDES, YOUR WAY</div>
            </div>
          </div>
          <div className="wb-top-actions">
            <div className="wb-market"><Icon name="pin" size={12} /> POLOKWANE <span>· ZAR</span></div>
            <div className="wb-avatar" aria-label="Nia's profile">N</div>
          </div>
        </header>

        {activeTab === 'book' ? (
          <>
            <section className="wb-intro">
              <div className="wb-greeting">GOOD TO GO, NIA</div>
              <h1>Where to?</h1>
              <p>Pick your stops. We’ll find the ride that fits.</p>
            </section>

            <section className="wb-route-card" aria-label="Plan your ride">
              <div className="wb-route-heading">
                <strong>Your route</strong>
                <span>Polokwane · ZAR</span>
              </div>
              <div className="wb-route-field">
                <div className="wb-field-icon" style={{ color: '#657b63' }}><Icon name="crosshair" size={16} /></div>
                <div className="wb-field-copy">
                  <label className="wb-field-label" htmlFor="wb-pickup">PICKUP</label>
                  <input
                    id="wb-pickup"
                    className="wb-field-input"
                    value={pickup}
                    onChange={(event) => setPickup(event.target.value)}
                    placeholder="Set pickup address"
                    aria-label="Pickup address"
                  />
                </div>
                <button className="wb-location-button" aria-label="Use current location" onClick={() => { setPickup('Current location, Polokwane'); setNotice('Pickup set to your current location'); }}>
                  <Icon name="crosshair" size={17} />
                </button>
              </div>
              <div className="wb-route-connector" aria-hidden="true" />
              <div className="wb-route-field">
                <div className="wb-field-icon" style={{ color: '#e96b43' }}><Icon name="pin" size={17} /></div>
                <div className="wb-field-copy">
                  <label className="wb-field-label" htmlFor="wb-destination">DESTINATION</label>
                  <input
                    id="wb-destination"
                    className="wb-field-input"
                    value={destination}
                    onChange={(event) => setDestination(event.target.value)}
                    placeholder="Where are you going?"
                    aria-label="Destination"
                  />
                </div>
                <button className="wb-location-button" aria-label="Search destinations" onClick={() => document.getElementById('wb-destination')?.focus()}>
                  <Icon name="search" size={16} />
                </button>
              </div>
            </section>

            <section className="wb-quick" aria-label="Quick destinations">
              <div className="wb-section-head">
                <strong>Go somewhere familiar</strong>
                <span>Saved &amp; recent</span>
              </div>
              <div className="wb-chips">
                {places.map((place) => (
                  <button className="wb-place" key={place.name} onClick={() => choosePlace(place.address)} aria-label={`Set destination to ${place.name}`}>
                    <span className="wb-place-mark"><Icon name={place.icon} size={14} /></span>
                    <span className="wb-place-copy"><strong>{place.name}</strong><small>{place.detail}</small></span>
                  </button>
                ))}
              </div>
            </section>

            <section className="wb-map-card" aria-label="Map of Polokwane">
              <svg className="wb-map-art" viewBox="0 0 420 170" preserveAspectRatio="xMidYMid slice" role="img" aria-label="Illustrated local street map">
                <rect width="420" height="170" fill="#e8ede2" />
                <path d="M-30 24 83 58 188 26 286 59 454 14" fill="none" stroke="#d4dfd1" strokeWidth="21" />
                <path d="M-25 143 95 104 191 137 302 96 454 132" fill="none" stroke="#d6dfd3" strokeWidth="24" />
                <path d="M81 -20 103 33 95 87 135 180M210 -15 194 44 225 81 205 180M332 -15 306 44 333 94 315 180" fill="none" stroke="#d4dfd1" strokeWidth="16" />
                <path d="M-20 77 73 82 143 63 212 78 295 65 440 75M23 7 41 61 31 116 54 180M265 0 251 48 271 113 255 176M380 0 370 51 390 102 375 172" fill="none" stroke="#f8f7ef" strokeWidth="8" />
                <path d="M-10 125 91 95 183 119 269 91 425 111" fill="none" stroke="#fffefa" strokeWidth="5" />
                <path d="M64 33 105 49 164 39 206 60 260 47 309 63" fill="none" stroke="#f8f7ef" strokeWidth="5" />
                <path d="m129 17 15-4m90 104 28-5m67-82 28 5M28 151l25-7m244 8 21 4" stroke="#c7d4c6" strokeWidth="2" strokeLinecap="round" />
                <path d="M103 93c24 2 42-6 58-18 19-15 28-15 44-11 23 6 36 8 50-2" fill="none" stroke="#e96b43" strokeWidth="3" strokeDasharray="4 5" strokeLinecap="round" />
                <circle cx="103" cy="93" r="7" fill="#fffefa" stroke="#e96b43" strokeWidth="3" />
                <circle cx="255" cy="62" r="7" fill="#e96b43" stroke="#fffefa" strokeWidth="3" />
                <g fill="#96a38f" opacity=".68">
                  <rect x="144" y="102" width="28" height="14" rx="4" />
                  <rect x="349" y="30" width="34" height="17" rx="4" />
                  <rect x="65" y="31" width="19" height="11" rx="3" />
                  <rect x="288" y="130" width="28" height="13" rx="4" />
                  <rect x="181" y="8" width="22" height="12" rx="3" />
                  <rect x="11" y="104" width="26" height="14" rx="3" />
                </g>
                <g fill="#7e8c78" fontFamily="DM Sans, sans-serif" fontSize="7" fontWeight="600" letterSpacing=".8">
                  <text x="147" y="99">BENDOR</text>
                  <text x="316" y="89">MALL OF THE NORTH</text>
                  <text x="30" y="58">POLOKWANE CENTRAL</text>
                </g>
              </svg>
              <div className="wb-map-sheen" />
              <div className="wb-map-caption">
                <Icon name="map" size={14} />
                <div><strong>LOCAL RIDES</strong><small>Your city, your way</small></div>
              </div>
              <div className="wb-map-location"><span /> POLOKWANE, LIMPOPO</div>
            </section>
          </>
        ) : (
          <>
            <section className="wb-intro">
              <div className="wb-greeting">YOUR VHAYA</div>
              <h1>Your trips.</h1>
              <p>Ride details, all in one place.</p>
            </section>
            <section className="wb-route-card wb-trip-state">
              <div className="wb-trip-icon"><Icon name="rides" size={25} /></div>
              <h2>No rides just yet</h2>
              <p>Your active and recent journeys will appear here. Ready when you are.</p>
              <div className="wb-trip-note"><Icon name="shield" size={14} /> Your ride details stay with you</div>
            </section>
          </>
        )}
      </div>

      <footer className="wb-bottom-dock">
        {activeTab === 'book' && (
          <button className="wb-cta" disabled={!pickup.trim() || !destination.trim()} onClick={() => setShowOptions(true)}>
            See ride options <Icon name="arrow" size={17} />
          </button>
        )}
        <nav className="wb-tabbar" aria-label="Main navigation">
          <button className={`wb-tab ${activeTab === 'book' ? 'is-active' : ''}`} onClick={() => setActiveTab('book')} aria-current={activeTab === 'book' ? 'page' : undefined}>
            <Icon name="map" size={15} /> Book a ride
          </button>
          <button className={`wb-tab ${activeTab === 'trips' ? 'is-active' : ''}`} onClick={() => setActiveTab('trips')} aria-current={activeTab === 'trips' ? 'page' : undefined}>
            <Icon name="clock" size={15} /> Your trips
          </button>
        </nav>
      </footer>

      {notice && <div className="wb-toast" role="status">{notice}</div>}

      {showOptions && (
        <div className="wb-dialog-scrim" role="presentation" onClick={(event) => { if (event.target === event.currentTarget) setShowOptions(false); }}>
          <section className="wb-dialog" role="dialog" aria-modal="true" aria-labelledby="wb-dialog-title">
            <div className="wb-dialog-head">
              <div><small>YOUR ROUTE</small><h2 id="wb-dialog-title">Choose your ride</h2></div>
              <button className="wb-close" aria-label="Close ride options" onClick={() => setShowOptions(false)}><Icon name="close" size={17} /></button>
            </div>
            {[
              { name: 'Standard', detail: 'Everyday city ride · up to 4', price: 'From R 48', icon: 'car' as const },
              { name: 'Comfort', detail: 'More room for your journey', price: 'From R 72', icon: 'shield' as const },
            ].map((option) => (
              <button className="wb-option" key={option.name} onClick={() => { setShowOptions(false); setNotice(`${option.name} selected · driver search started`); }}>
                <span className="wb-option-icon"><Icon name={option.icon} size={19} /></span>
                <span className="wb-option-copy"><strong>{option.name}</strong><small>{option.detail}</small></span>
                <span className="wb-option-price">{option.price}</span>
                <Icon name="chevron" size={15} />
              </button>
            ))}
          </section>
        </div>
      )}
    </main>
  );
}
