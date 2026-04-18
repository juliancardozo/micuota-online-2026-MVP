#!/usr/bin/env python3
import argparse
import json
import sys
import urllib.error
import urllib.request
from datetime import datetime


def post_json(base_url: str, path: str, payload: dict, token: str | None = None) -> dict:
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(base_url + path, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("X-Auth-Token", token)

    with urllib.request.urlopen(req, timeout=30) as response:
        body = response.read().decode("utf-8")
        return json.loads(body)


def get_json(base_url: str, path: str, token: str | None = None) -> dict:
    req = urllib.request.Request(base_url + path, method="GET")
    if token:
        req.add_header("X-Auth-Token", token)

    with urllib.request.urlopen(req, timeout=30) as response:
        body = response.read().decode("utf-8")
        return json.loads(body)


def get_status(base_url: str, path: str) -> int:
    req = urllib.request.Request(base_url + path, method="GET")
    with urllib.request.urlopen(req, timeout=30) as response:
        return response.status


def main() -> int:
    parser = argparse.ArgumentParser(description="Smoke API onboarding MiCuota")
    parser.add_argument("--base-url", default="http://localhost:8080", help="URL base del backend")
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")

    try:
        docs_status = get_status(base_url, "/v3/api-docs")
        if docs_status != 200:
            print(f"[api][error] /v3/api-docs devolvio {docs_status}")
            return 1
    except Exception as exc:
        print(f"[api][error] Backend no responde en {base_url}: {exc}")
        return 1

    stamp = datetime.now().strftime("%Y%m%d%H%M%S")
    tenant_slug = f"tenant-maestro-{stamp}"
    admin_email = f"admin.maestro+{stamp}@micuota.online"
    prof_email = f"profesor.demo+{stamp}@micuota.online"
    alum_email = f"alumno.demo+{stamp}@micuota.online"

    admin_pass = "DemoMicuota123!"
    prof_pass = "DemoProfesor123!"
    alum_pass = "DemoAlumno123!"

    try:
        reg = post_json(
            base_url,
            "/api/auth/register-tenant",
            {
                "tenantName": "Tenant Maestro",
                "tenantSlug": tenant_slug,
                "fullName": "Admin Maestro",
                "email": admin_email,
                "password": admin_pass,
                "mpAccessToken": "TEST-MP-TOKEN-MAESTRO",
            },
        )

        token = reg["token"]

        prof = post_json(
            base_url,
            "/api/backoffice/users",
            {
                "fullName": "Profesor Demo",
                "email": prof_email,
                "password": prof_pass,
                "role": "TEACHER",
            },
            token,
        )

        alum = post_json(
            base_url,
            "/api/backoffice/users",
            {
                "fullName": "Alumno Demo",
                "email": alum_email,
                "password": alum_pass,
                "role": "STUDENT",
            },
            token,
        )

        course = post_json(
            base_url,
            "/api/backoffice/courses",
            {
                "name": "Curso Demo",
                "description": "Curso de onboarding MVP",
                "teacherUserId": prof["id"],
            },
            token,
        )

        post_json(
            base_url,
            "/api/backoffice/enrollments",
            {
                "courseId": course["id"],
                "studentUserId": alum["id"],
            },
            token,
        )

        one = post_json(
            base_url,
            "/api/backoffice/payments/one-time",
            {
                "provider": "MERCADOPAGO",
                "description": "Pago unica vez Demo",
                "amount": 900,
                "currency": "UYU",
                "payerEmail": alum_email,
                "studentUserId": alum["id"],
                "courseId": course["id"],
            },
            token,
        )

        sub = post_json(
            base_url,
            "/api/backoffice/payments/subscriptions",
            {
                "provider": "MERCADOPAGO",
                "description": "Pago suscripcion Demo",
                "amount": 2500,
                "currency": "UYU",
                "payerEmail": alum_email,
                "studentUserId": alum["id"],
                "courseId": course["id"],
            },
            token,
        )

        launchpad = get_json(base_url, "/api/backoffice/launchpad", token)

    except urllib.error.HTTPError as exc:
        try:
            detail = exc.read().decode("utf-8")
        except Exception:
            detail = "<sin detalle>"
        print(f"[api][error] HTTP {exc.code}: {detail}")
        return 1
    except Exception as exc:
        print(f"[api][error] Excepcion no controlada: {exc}")
        return 1

    summary = {
        "tenant": {"id": reg["tenantId"], "slug": reg["tenantSlug"]},
        "entities": {
            "profesorId": prof["id"],
            "alumnoId": alum["id"],
            "cursoId": course["id"],
            "pagoUnicaVezId": one["id"],
            "pagoSuscripcionId": sub["id"],
        },
        "links": {
            "backoffice": reg.get("backofficeUrl"),
            "pagoUnicaVez": f"/pago.html?operationId={one['id']}",
            "pagoSuscripcion": f"/pago.html?operationId={sub['id']}",
        },
        "credentials": {
            "admin": {"email": admin_email, "password": admin_pass},
            "profesor": {"email": prof_email, "password": prof_pass},
            "alumno": {"email": alum_email, "password": alum_pass},
        },
        "launchpad": {
            "plan": launchpad["planName"],
            "stage": launchpad["stage"],
            "activationScore": launchpad["activationScore"],
            "nextBestAction": launchpad["nextBestAction"],
        },
    }

    print("[api] Smoke OK")
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
