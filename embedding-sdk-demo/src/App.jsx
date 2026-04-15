import {
  InteractiveDashboard,
  MetabaseProvider,
  defineMetabaseAuthConfig,
} from "@metabase/embedding-sdk-react";

const metabaseInstanceUrl = import.meta.env.VITE_METABASE_URL || "http://localhost:3001";
const apiKey = import.meta.env.VITE_METABASE_API_KEY || "";

const dashboardViews = [
  {
    key: "tenants",
    label: "Tenants",
    dashboardId: Number(import.meta.env.VITE_METABASE_DASHBOARD_ID_TENANTS || 1),
  },
  {
    key: "profesores-alumnos",
    label: "Profesores y Alumnos",
    dashboardId: Number(import.meta.env.VITE_METABASE_DASHBOARD_ID_PROFESORES_ALUMNOS || 2),
  },
  {
    key: "pagos",
    label: "Pagos",
    dashboardId: Number(import.meta.env.VITE_METABASE_DASHBOARD_ID_PAGOS || 3),
  },
];

function getViewFromUrl() {
  const params = new URLSearchParams(window.location.search);
  const view = params.get("view");
  return dashboardViews.find((item) => item.key === view) || dashboardViews[0];
}

const authConfig = defineMetabaseAuthConfig({
  metabaseInstanceUrl,
  apiKey,
});

function MissingConfig() {
  return (
    <div className="mc-card">
      <h1>Metabase SDK Demo</h1>
      <p>Missing API key.</p>
      <p>
        Create a <strong>.env.local</strong> file using <strong>.env.example</strong> and set
        <strong> VITE_METABASE_API_KEY</strong>.
      </p>
    </div>
  );
}

export default function App() {
  const selectedView = getViewFromUrl();

  if (!apiKey) {
    return <MissingConfig />;
  }

  function setView(key) {
    const params = new URLSearchParams(window.location.search);
    params.set("view", key);
    window.location.search = params.toString();
  }

  return (
    <main className="mc-shell">
      <header className="mc-header">
        <h1>MiCuota - Dashboards embebidos</h1>
        <p>
          Metabase URL: <strong>{metabaseInstanceUrl}</strong> | Vista: <strong>{selectedView.label}</strong> |
          Dashboard ID: <strong>{selectedView.dashboardId}</strong>
        </p>

        <div className="mc-tabs" role="tablist" aria-label="Selector de dashboard">
          {dashboardViews.map((view) => (
            <button
              key={view.key}
              type="button"
              className={view.key === selectedView.key ? "mc-tab is-active" : "mc-tab"}
              onClick={() => setView(view.key)}
            >
              {view.label}
            </button>
          ))}
        </div>
      </header>

      <section className="mc-panel">
        <MetabaseProvider authConfig={authConfig}>
          <InteractiveDashboard dashboardId={selectedView.dashboardId} />
        </MetabaseProvider>
      </section>
    </main>
  );
}
