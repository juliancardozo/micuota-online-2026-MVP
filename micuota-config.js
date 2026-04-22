(function () {
  const isLocalhost = window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1";
  const apiBaseParam = new URLSearchParams(window.location.search).get("apiBase");
  if (apiBaseParam === "clear") {
    localStorage.removeItem("micuota.apiBase");
  } else if (apiBaseParam) {
    localStorage.setItem("micuota.apiBase", apiBaseParam);
  }

  const configuredApiBase =
    window.__MICUOTA_API_BASE__ ||
    (apiBaseParam && apiBaseParam !== "clear" ? apiBaseParam : "") ||
    localStorage.getItem("micuota.apiBase") ||
    document.querySelector('meta[name="micuota-api-base"]')?.getAttribute("content");
  const defaultApiBase = isLocalhost ? "http://localhost:8080" : "https://micuota-online-2026-backend.onrender.com";
  const apiBase = (configuredApiBase || defaultApiBase).replace(/\/$/, "");

  window.__MICUOTA_API_BASE__ = apiBase;
  window.MicuotaConfig = { apiBase, hasApiBase: Boolean(apiBase) };

  async function parseBody(response) {
    const text = await response.text();
    if (!text) return null;

    const contentType = response.headers.get("content-type") || "";
    if (contentType.includes("application/json")) {
      try {
        return JSON.parse(text);
      } catch (_error) {
        throw new Error("La API devolvio JSON incompleto. Intenta nuevamente.");
      }
    }

    return { error: `La API devolvio una respuesta no JSON (${response.status}).` };
  }

  async function request(path, options = {}) {
    if (!apiBase) {
      throw new Error(
        "Backend de produccion no configurado. Abre esta pagina con ?apiBase=https://tu-backend o configura micuota.apiBase."
      );
    }

    let response;
    try {
      response = await fetch(`${apiBase}${path}`, options);
    } catch (_error) {
      throw new Error("No pudimos conectar con el backend de MiCuota. Revisa la configuracion de API.");
    }

    const data = await parseBody(response);
    if (!response.ok) {
      throw new Error((data && data.error) || `Error de API (${response.status})`);
    }
    return data || {};
  }

  window.MicuotaApi = { request };
})();
