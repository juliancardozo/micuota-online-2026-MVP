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
  // Produccion: backend oficial. Localhost: backend de pruebas desplegado (CORS permite http://localhost:*).
  // Para usar un backend local: abri cualquier pagina con ?apiBase=http://localhost:8080 (se recuerda; ?apiBase=clear lo borra).
  const PRODUCTION_API_BASE = "https://micuota-online-2026-backend.onrender.com";
  const LOCAL_DEV_API_BASE = "https://micuota-backend-dev.onrender.com";
  const defaultApiBase = isLocalhost ? LOCAL_DEV_API_BASE : PRODUCTION_API_BASE;
  const apiBase = (configuredApiBase || defaultApiBase).replace(/\/$/, "");

  window.__MICUOTA_API_BASE__ = apiBase;
  window.MicuotaConfig = {
    apiBase,
    hasApiBase: Boolean(apiBase),
    // Cuando exista OAuth de Mercado Pago en el backend, completar esta URL y la app
    // muestra el boton "Conectar con Mercado Pago" en lugar de pegar el Access Token.
    mercadoPagoOAuthUrl: window.__MICUOTA_MP_OAUTH_URL__ || ""
  };

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
      const networkError = new Error("No pudimos conectar con MiCuota. Revisa tu conexion e intenta de nuevo.");
      networkError.network = true;
      throw networkError;
    }

    const data = await parseBody(response);
    if (!response.ok) {
      const apiError = new Error((data && data.error) || `Error de API (${response.status})`);
      apiError.status = response.status;
      throw apiError;
    }
    return data || {};
  }

  window.MicuotaApi = { request };
})();
