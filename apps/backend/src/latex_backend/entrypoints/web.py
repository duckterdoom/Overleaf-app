"""Local browser application; no pywebview/native GUI dependency."""
import argparse
import json
import socket
import threading
import time
import webbrowser
from pathlib import Path

import uvicorn
from latex_backend.core.application import create_web_app
from latex_backend.core.config import REPO, data_directory
from latex_backend.storage.mongo import MongoSetupError


def main():
    parser = argparse.ArgumentParser(description='Latex Local Web — Python + Next.js')
    parser.add_argument('--home', type=Path, default=data_directory(), help='Persistent server data directory (operator only). Also accepts LATEX_DATA_DIR.')
    parser.add_argument('--frontend', type=Path, default=REPO / 'apps/frontend/out')
    parser.add_argument('--host', choices=['127.0.0.1', 'localhost'], default='127.0.0.1')
    parser.add_argument('--port', type=int, default=8000)
    parser.add_argument('--latexmk', default='')
    parser.add_argument('--no-browser', action='store_true')
    args = parser.parse_args()
    if not 0 <= args.port <= 65535:
        parser.error('Port must be between 0 and 65535.')
    if not (args.frontend / 'index.html').is_file():
        parser.error('Build the UI first: scripts/run-python-web.bat --build (Windows) or sh scripts/run-python-web.sh --build')
    try:
        app = create_web_app(args.home, args.frontend, args.latexmk)
    except MongoSetupError as error:
        parser.error(str(error))
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        try:
            sock.bind((args.host, args.port))
        except OSError as error:
            parser.error(f'Cannot bind {args.host}:{args.port}: {error}. Try --port 8001.')
        address = f'http://{args.host}:{sock.getsockname()[1]}/'
        server = uvicorn.Server(uvicorn.Config(app, log_level='warning', access_log=False))

        def ready():
            while not server.started and not server.should_exit:
                time.sleep(0.05)
            if not server.started:
                return
            print('OVERLEAF_WEB_READY ' + json.dumps({'url': address}), flush=True)
            print('Latex local web is running. Ctrl+C stops the server.', flush=True)
            if not args.no_browser:
                webbrowser.open(address)

        threading.Thread(target=ready, daemon=True).start()
        try:
            server.run(sockets=[sock])
        except KeyboardInterrupt:
            pass


if __name__ == '__main__':
    main()
