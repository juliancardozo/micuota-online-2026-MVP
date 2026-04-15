function buildDemoLink(type, payload) {
  const base = "https://www.micuota.online/checkout";
  const query = new URLSearchParams({
    type,
    id: crypto.randomUUID().slice(0, 8),
    amount: payload.amount,
    ts: Date.now().toString()
  });
  return `${base}?${query.toString()}`;
}

function createQrMarkup(url) {
  const qr = `https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(url)}`;
  return `
    <p><strong>Link:</strong> <a href="${url}" target="_blank" rel="noopener noreferrer">${url}</a></p>
    <p><strong>QR generado:</strong></p>
    <img src="${qr}" alt="QR de pago" width="180" height="180" loading="lazy" />
  `;
}

function setupSinglePaymentForm() {
  const form = document.getElementById("single-payment-form");
  const result = document.getElementById("single-result");

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const data = new FormData(form);
    const payload = {
      title: String(data.get("title") || ""),
      amount: String(data.get("amount") || "0"),
      currency: String(data.get("currency") || "UYU")
    };

    const link = buildDemoLink("single", payload);
    result.innerHTML = `
      <p><strong>Pago unico:</strong> ${payload.title} - ${payload.currency} ${payload.amount}</p>
      ${createQrMarkup(link)}
    `;
  });
}

function setupSubscriptionForm() {
  const form = document.getElementById("subscription-form");
  const result = document.getElementById("subscription-result");

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const data = new FormData(form);
    const payload = {
      reason: String(data.get("reason") || ""),
      amount: String(data.get("amount") || "0"),
      frequency: String(data.get("frequency") || "1")
    };

    const link = buildDemoLink("subscription", payload);
    result.innerHTML = `
      <p><strong>Suscripcion:</strong> ${payload.reason} - cada ${payload.frequency} mes(es) - UYU ${payload.amount}</p>
      ${createQrMarkup(link)}
    `;
  });
}

setupSinglePaymentForm();
setupSubscriptionForm();
