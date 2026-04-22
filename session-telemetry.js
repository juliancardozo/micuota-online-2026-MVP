(function () {
  const API_BASE =
    window.MicuotaConfig?.apiBase ||
    window.__MICUOTA_API_BASE__ ||
    ((window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1")
      ? "http://localhost:8080"
      : "");
  const HEARTBEAT_MS = 60 * 1000;
  const token = localStorage.getItem("micuota.authToken");

  if (!API_BASE || !token) {
    window.MicuotaSessionTelemetry = {
      logout() {}
    };
    return;
  }

  async function post(path, useKeepalive) {
    try {
      await fetch(`${API_BASE}${path}`, {
        method: "POST",
        headers: { "X-Auth-Token": token },
        keepalive: Boolean(useKeepalive)
      });
    } catch (error) {
      // Telemetria no debe romper la UX del producto.
    }
  }

  function heartbeat() {
    return post("/api/auth/heartbeat", false);
  }

  function logout() {
    return post("/api/auth/logout", true);
  }

  heartbeat();
  const intervalId = window.setInterval(heartbeat, HEARTBEAT_MS);

  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
      heartbeat();
    }
  });

  window.addEventListener("beforeunload", () => {
    window.clearInterval(intervalId);
  });

  window.MicuotaSessionTelemetry = { heartbeat, logout };
})();
