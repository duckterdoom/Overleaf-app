"""Interactive networking demo, equivalent to the Java console client."""

import argparse
from pathlib import Path
from .store import Store
from .network import NetworkSession


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--room", default="DEMO")
    parser.add_argument("--name", default="Console User")
    parser.add_argument("--tls", action="store_true")
    parser.add_argument("--ca-file", default="")
    parser.add_argument(
        "--home", type=Path, default=Path("~/OverleafDesktopConsole").expanduser()
    )
    args = parser.parse_args()
    import os

    store = Store(args.home)
    project = next(iter(store.list()), None) or store.create("Console demo")
    session = NetworkSession(store)
    session.connect(
        project["id"],
        host=args.host,
        port=args.port,
        room=args.room,
        name=args.name,
        token=os.getenv("OVERLEAF_TOKEN", ""),
        tls=args.tls,
        caFile=args.ca_file,
    )
    print(
        "/show /status /offline /reconnect /quit; other input appends a line to main.tex"
    )
    try:
        while True:
            text = input("> ")
            if text == "/quit":
                break
            if text == "/show":
                print(session.document)
            elif text == "/status":
                print(
                    {
                        key: value
                        for key, value in session.status().items()
                        if key != "document"
                    }
                )
            elif text == "/offline":
                session.offline()
            elif text == "/reconnect":
                session.reconnect()
            else:
                try:
                    session.edit(session.document + "\n" + text)
                    store.save(project["id"], "main.tex", session.document)
                except Exception as error:
                    print(str(error))
    except (EOFError, KeyboardInterrupt):
        pass
    finally:
        session.leave()


if __name__ == "__main__":
    main()
