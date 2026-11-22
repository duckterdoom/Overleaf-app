"""Isolated loopback backend for the browser UI regression; not a product server."""

import json
import secrets
import socket
import sys
import threading
import time
from pathlib import Path
import uvicorn
from latex_backend.http.api import create_api
from latex_backend.compilation.compiler import Compiler
from latex_backend.storage.store import REPO, Store

home = Path(sys.argv[1]).absolute()
if home.exists() and "--existing" not in sys.argv:
    raise RuntimeError("Use a new test home")
store = Store(home)
token = secrets.token_urlsafe(32)
sock = socket.socket()
sock.bind(("127.0.0.1", 0))
port = sock.getsockname()[1]
server = uvicorn.Server(
    uvicorn.Config(
        create_api(store, Compiler(store), token, REPO / "apps/frontend/out"),
        log_level="warning",
        access_log=False,
    )
)


def ready():
    while not server.started:
        time.sleep(0.05)
    (home / "ready.json").write_text(
        json.dumps({"url": f"http://127.0.0.1:{port}/#session={token}"}),
        encoding="utf-8",
    )


threading.Thread(target=ready, daemon=True).start()
server.run(sockets=[sock])
