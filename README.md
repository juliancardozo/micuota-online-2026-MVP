# MiCuota.online - Web MVP para Netlify

Aplicacion web estatica para presentar y validar el flujo inicial de MiCuota.online:

- Creacion de pago unico
- Creacion de suscripcion
- Generacion visual de link + QR
- Base lista para evolucionar a backend Spring Boot

## Stack

- HTML
- CSS
- JavaScript
- Netlify (deploy)

## Estructura

- `index.html`: interfaz principal
- `styles.css`: estilos y layout responsive
- `app.js`: logica de formularios y QR demo
- `netlify.toml`: configuracion de deploy y redirects

## Ejecutar local

Puedes abrir `index.html` directamente en el navegador.

Opcional con servidor local:

```bash
npx serve .
```

## Deploy en Netlify

1. Crear un repositorio en GitHub y subir el contenido.
2. En Netlify, seleccionar "Add new site" -> "Import from Git".
3. Elegir el repositorio.
4. Build command: vacio.
5. Publish directory: `.`

## Flujo de ramas sugerido

- `develop`: desarrollo diario
- `test`: validacion funcional
- `produccion`: rama estable para deploy

Estrategia recomendada:

1. Trabajar features en `develop`.
2. Merge a `test` para QA.
3. Merge a `produccion` para release.
