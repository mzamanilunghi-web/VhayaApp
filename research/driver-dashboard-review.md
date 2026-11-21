# Wayfare driver dashboard — product review

**Reviewed:** 24 October 2026  
**Market focus:** Polokwane, South Africa  
**Scope:** Driver app workflow and dashboard interaction patterns. This is desk research, not direct driver research or legal advice.

## What established driver apps make clear

Both Uber and Lyft organize the working ride around a small number of explicit stages rather than a single generic “active ride” state:

1. **Review and accept a request.** Drivers need enough trip context to decide whether to take it.
2. **Navigate to pickup.** The pickup is the current destination, and the driver can contact the rider if needed.
3. **Confirm arrival.** The driver explicitly marks that they are at the pickup.
4. **Verify and start.** Uber describes sliding to start after pickup; Lyft describes a pickup action and PIN verification for certain ride types.
5. **Drive to drop-off.** The app clearly switches the current destination from pickup to drop-off.
6. **Complete the trip.** Drivers explicitly confirm arrival at the destination; post-trip rating or earnings information follows.

Uber's driver app guide also separates Home, Earnings, Inbox, and Menu, so the active ride remains the primary task while secondary account and earnings tools are still discoverable.

## Wayfare baseline and design decision

Wayfare already has server-backed ride states, driver request acceptance, foreground location, GPS-checked arrival within 150 metres, a six-digit pickup PIN, destination route guidance, and trip completion. The earlier driver screen exposed some of this inline and made stage changes feel like generic buttons.

The dashboard now makes the three operational stages visible—**To pickup → At pickup → On trip**—and opens a stage-specific dialog before each transition. The dialog shows the relevant pickup or destination, preserves the existing GPS and PIN checks, and keeps action errors visible without dismissing the dialog. Completion on the dedicated drive screen also requires confirmation.

## Keep capability claims accurate

- Route guidance is a map preview, not in-app turn-by-turn or voice navigation.
- Route duration is an estimate; the app does not provide a real driver-arrival ETA.
- Driver location is shared only while the app is open and the trip is in progress.
- The emergency action opens the device dialler for 112; it is not emergency dispatch.
- The app does not yet provide staffed in-app driver support.

The interface should continue to label these limits honestly. Before a public launch, validate the full driver/rider sequence on iOS and Android, including denied location permission, poor GPS accuracy, incorrect PINs, network failures, trip cancellation, and completion refresh failures.

## Sources

- [Uber — Your guide to Uber’s Driver app](https://www.uber.com/us/en/drive/driver-app/) — driver app sections, online state, trip requests, and trip stages.
- [Uber — Information about taking trips](https://www.uber.com/pl/en/drive/basics/how-to-take-trips/) — upfront request context, starting a trip, and completing it.
- [Lyft Help — How to navigate a ride](https://help.lyft.com/hc/en-ca/all/articles/115012926147-How-to-navigate-a-ride) — request notification, arrival, pickup, navigation, drop-off, and PIN examples.

Competitor guides describe their own products and may vary by market. They are workflow references, not commitments about availability in South Africa or requirements for Wayfare.
