"""Actual TLS sockets and certificate/hostname verification, using an ephemeral test cert."""

import asyncio, os, shutil, ssl, subprocess, threading
from pathlib import Path
import pytest
from overleaf_desktop.store import Store
from overleaf_desktop.network import CollaborationServer, NetworkSession


def test_tls_trust_hostname_and_token(tmp_path):
    openssl = shutil.which("openssl") or (
        "C:/Program Files/Git/usr/bin/openssl.exe" if os.name == "nt" else ""
    )
    if not Path(openssl).is_file():
        pytest.skip("OpenSSL required for ephemeral native TLS certificate")
    cert, key = tmp_path / "cert.pem", tmp_path / "key.pem"
    subprocess.run(
        [
            openssl,
            "req",
            "-x509",
            "-newkey",
            "rsa:2048",
            "-nodes",
            "-keyout",
            str(key),
            "-out",
            str(cert),
            "-days",
            "1",
            "-subj",
            "/CN=localhost",
            "-addext",
            "subjectAltName=DNS:localhost",
        ],
        check=True,
        capture_output=True,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
    )
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(cert, key)
    loop = asyncio.new_event_loop()
    thread = threading.Thread(target=loop.run_forever, daemon=True)
    thread.start()
    server = CollaborationServer("test-token-1234567890123456789")
    port = asyncio.run_coroutine_threadsafe(
        server.start("127.0.0.1", 0, context), loop
    ).result(5)
    store = Store(tmp_path / "home")
    project = store.create("TLS")["id"]
    session = NetworkSession(store)
    try:
        with pytest.raises(ssl.SSLCertVerificationError):
            session.connect(
                project, host="localhost", port=port, tls=True, token=server.token
            )
        with pytest.raises(ssl.SSLCertVerificationError):
            session.connect(
                project,
                host="127.0.0.1",
                port=port,
                tls=True,
                caFile=str(cert),
                token=server.token,
            )
        assert session.connect(
            project,
            host="localhost",
            port=port,
            tls=True,
            caFile=str(cert),
            token=server.token,
        )["online"]
    finally:
        session.leave()
        asyncio.run_coroutine_threadsafe(server.close(), loop).result(5)
        loop.call_soon_threadsafe(loop.stop)
        thread.join(5)
        loop.close()
