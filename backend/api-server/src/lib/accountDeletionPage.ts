export const accountDeletionPage = `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="description" content="Request deletion of your Wayfare rider or driver account and personal profile data.">
    <title>Delete your Wayfare account</title>
    <style>
      :root { color-scheme: light; font-family: Inter, ui-sans-serif, system-ui, sans-serif; color: #183438; background: #eef4ef; }
      * { box-sizing: border-box; }
      body { margin: 0; min-height: 100vh; padding: 32px 18px; display: grid; place-items: center; }
      main { width: min(100%, 680px); background: #fff; border: 1px solid #dce7df; border-radius: 18px; padding: clamp(24px, 6vw, 48px); box-shadow: 0 20px 60px #18343812; }
      .brand { display: flex; align-items: center; gap: 11px; color: #155d57; font-weight: 800; letter-spacing: -.03em; }
      .mark { width: 34px; height: 34px; display: grid; place-items: center; background: #155d57; color: #fff; border-radius: 10px; }
      .eyebrow { margin: 34px 0 9px; color: #64837a; font-size: 12px; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; }
      h1 { margin: 0; font-size: clamp(30px, 6vw, 44px); letter-spacing: -.045em; line-height: 1.08; }
      .intro { margin: 16px 0 28px; color: #526a66; line-height: 1.65; }
      .note { margin: 20px 0; padding: 16px; border: 1px solid #dce9e1; border-radius: 11px; background: #f5faf6; color: #526a66; font-size: 14px; line-height: 1.6; }
      .note strong { color: #183438; }
      label { display: block; margin-bottom: 8px; font-size: 14px; font-weight: 700; }
      input { width: 100%; min-height: 48px; border: 1px solid #cbd9d0; border-radius: 9px; padding: 0 13px; color: #183438; font: inherit; }
      input:focus { outline: 3px solid #155d5726; border-color: #155d57; }
      button { width: 100%; min-height: 49px; margin-top: 14px; border: 0; border-radius: 9px; background: #155d57; color: #fff; font: inherit; font-weight: 700; cursor: pointer; }
      button:hover { background: #104e49; }
      button:disabled { opacity: .65; cursor: wait; }
      .status { margin-top: 18px; padding: 14px; border-radius: 9px; background: #f3f6f4; color: #365c55; line-height: 1.55; overflow-wrap: anywhere; }
      .status:empty { display: none; }
      .status.error { background: #fff4f1; color: #923d30; }
      .footer { margin: 23px 0 0; color: #748782; font-size: 12px; line-height: 1.6; }
      @media (max-width: 480px) { body { padding: 16px 12px; } main { border-radius: 14px; } }
    </style>
  </head>
  <body>
    <main>
      <div class="brand"><span class="mark" aria-hidden="true">W</span><span>Wayfare</span></div>
      <p class="eyebrow">Privacy controls</p>
      <h1>Request account deletion</h1>
      <p class="intro">Use this page to request deletion of your Wayfare rider or driver account if you no longer have the app installed. We review account ownership before processing a web request.</p>
      <div class="note"><strong>What is removed:</strong> your sign-in account, profile, driver application details and stored documents, device tokens, and personal messages. <strong>What may remain:</strong> ride, payment, safety, dispute, and audit records may be kept in pseudonymized form for reconciliation, safety, or legal requirements.</div>
      <form id="request-form">
        <label for="email">Email used for your Wayfare account</label>
        <input id="email" name="email" type="email" autocomplete="email" maxlength="254" required>
        <button id="submit" type="submit">Submit deletion request</button>
      </form>
      <div id="status" class="status" role="status" aria-live="polite"></div>
      <p class="footer">Requests made here are reviewed before deletion, including checks for active rides and subscriptions. Keep the private reference shown after submitting to check your request later.</p>
    </main>
    <script>
      const api = "/api/account-deletion/requests";
      const form = document.getElementById("request-form");
      const button = document.getElementById("submit");
      const statusBox = document.getElementById("status");
      const reference = new URLSearchParams(location.search).get("request");

      async function showStatus(id) {
        const response = await fetch(api + "/" + encodeURIComponent(id), { headers: { Accept: "application/json" } });
        if (!response.ok) throw new Error("The request status is temporarily unavailable. Try again later.");
        const result = await response.json();
        statusBox.textContent = result.message + " Private reference: " + id;
        statusBox.classList.remove("error");
        form.hidden = result.status === "completed";
      }

      if (reference) {
        showStatus(reference).catch((error) => {
          statusBox.textContent = error.message;
          statusBox.classList.add("error");
        });
      }

      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        statusBox.textContent = "";
        statusBox.classList.remove("error");
        button.disabled = true;
        button.textContent = "Submitting…";
        try {
          const response = await fetch(api, {
            method: "POST",
            headers: { "Content-Type": "application/json", Accept: "application/json" },
            body: JSON.stringify({ email: document.getElementById("email").value.trim() })
          });
          const result = await response.json();
          if (!response.ok) throw new Error(result.error || "The request could not be submitted.");
          history.replaceState({}, "", location.pathname + "?request=" + encodeURIComponent(result.requestId));
          await showStatus(result.requestId);
        } catch (error) {
          statusBox.textContent = error instanceof Error ? error.message : "The request could not be submitted.";
          statusBox.classList.add("error");
        } finally {
          button.disabled = false;
          button.textContent = "Submit deletion request";
        }
      });
    </script>
  </body>
</html>`;
