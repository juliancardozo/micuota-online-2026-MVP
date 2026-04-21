from http.server import SimpleHTTPRequestHandler, HTTPServer
from pathlib import Path
import os


class DocsHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(Path("site").resolve()), **kwargs)


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8000"))
    server = HTTPServer(("0.0.0.0", port), DocsHandler)
    print(f"Serving docs on http://0.0.0.0:{port}", flush=True)
    server.serve_forever()
