"""Private, persistent Google OAuth configuration for developer workstations."""
import argparse
from dataclasses import dataclass, field
import json
import os
from pathlib import Path
import sys
import uuid
from urllib.parse import urlsplit

from latex_backend.core.config import REPO


class GoogleSetupError(RuntimeError):
    pass


@dataclass(frozen=True)
class GoogleSettings:
    client_id: str = field(default='', repr=False)
    client_secret: str = field(default='', repr=False)
    public_url: str = ''
    source: str = 'none'

    @property
    def enabled(self):
        return bool(self.client_id and self.client_secret)


def project_config_path():
    return REPO / 'config/google.local.json'


def user_config_path():
    if os.name == 'nt':
        folder = Path(os.getenv('APPDATA') or Path.home() / 'AppData/Roaming') / 'Latex'
    elif sys.platform == 'darwin':
        folder = Path.home() / 'Library/Application Support/Latex'
    else:
        folder = Path(os.getenv('XDG_CONFIG_HOME') or Path.home() / '.config') / 'latex'
    return folder / 'google.local.json'


def validate(client_id, client_secret, public_url='', source='none'):
    if any(not isinstance(value, str) or len(value) > 8192 for value in (client_id, client_secret, public_url)):
        raise GoogleSetupError('Google OAuth config requires string fields client_id, client_secret and optional public_url.')
    client_id, client_secret, public_url = client_id.strip(), client_secret.strip(), public_url.strip().rstrip('/')
    if bool(client_id) != bool(client_secret):
        raise GoogleSetupError('Google OAuth config needs both client_id and client_secret from the same Web application client.')
    if public_url:
        try:
            parsed = urlsplit(public_url)
            valid = parsed.hostname and (parsed.scheme == 'https' or
                (parsed.scheme == 'http' and parsed.hostname in {'localhost', '127.0.0.1'}))
            valid = valid and not (parsed.username or parsed.password or parsed.query or parsed.fragment)
            parsed.port  # Validate malformed/out-of-range ports without printing input.
        except ValueError:
            valid = False
        if not valid:
            raise GoogleSetupError('Google OAuth public_url must be HTTPS or an HTTP localhost URL, without credentials/query/fragment.')
    return GoogleSettings(client_id, client_secret, public_url, source)


def read_file(path, source):
    try:
        if path.stat().st_size > 65536:
            raise GoogleSetupError('Google OAuth config exceeds 64 KiB.')
        value = json.loads(path.read_text(encoding='utf-8-sig'))
        if not isinstance(value, dict):
            raise ValueError
        # Google Cloud's downloaded Web application JSON can be used directly.
        if 'installed' in value and 'web' not in value:
            raise GoogleSetupError('Use a Google OAuth Web application client, not a Desktop/installed client.')
        credentials = value.get('web', value)
        if not isinstance(credentials, dict):
            raise ValueError
        return validate(credentials.get('client_id', ''), credentials.get('client_secret', ''),
                        value.get('public_url', ''), source)
    except GoogleSetupError:
        raise
    except (OSError, ValueError, UnicodeError, TypeError):
        # Never include JSON values or raw parser exceptions in logs/responses.
        raise GoogleSetupError('Cannot read Google OAuth JSON config. Check the private file and JSON format; no credentials were logged.') from None


def settings():
    client_id = os.getenv('LATEX_GOOGLE_CLIENT_ID', '').strip()
    client_secret = os.getenv('LATEX_GOOGLE_CLIENT_SECRET', '').strip()
    public_url = os.getenv('LATEX_PUBLIC_URL', '').strip()
    if client_id or client_secret:
        return validate(client_id, client_secret, public_url, 'environment')
    explicit = os.getenv('LATEX_GOOGLE_CONFIG', '').strip()
    if explicit:
        path = Path(explicit).expanduser()
        if not path.is_absolute():
            path = REPO / path
        candidates = [(path, 'explicit-file')]
    else:
        candidates = [(project_config_path(), 'project-file'), (user_config_path(), 'user-file')]
    for path, source in candidates:
        if not path.exists() and not explicit:
            continue
        value = read_file(path, source)
        if value.enabled:
            return validate(value.client_id, value.client_secret, public_url or value.public_url, source)
    return validate('', '', public_url)


def import_file(source, *, project=False, replace=False):
    value = read_file(Path(source).expanduser(), 'import')
    return save_private(value, project=project, replace=replace)


def save_private(value, *, project=False, replace=False):
    if not value.enabled:
        raise GoogleSetupError('The supplied JSON does not contain Google Web client credentials.')
    target = project_config_path() if project else user_config_path()
    if target.exists() and not replace:
        raise GoogleSetupError('Private Google config already exists. Use --replace only when you intend to replace it.')
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name('.google-' + uuid.uuid4().hex + '.tmp')
    try:
        with os.fdopen(os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'w', encoding='utf-8') as handle:
            json.dump({'client_id': value.client_id, 'client_secret': value.client_secret,
                       'public_url': value.public_url}, handle, indent=2)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, target)
    finally:
        if temporary.exists():
            temporary.unlink()
    return target


def main():
    parser = argparse.ArgumentParser(description='Import private Google Web OAuth JSON once; all local clones can reuse it. Credentials are never printed.')
    parser.add_argument('file', nargs='?', type=Path, help='Private JSON downloaded from Google Cloud or supplied by your team')
    parser.add_argument('--project', action='store_true', help='Import to this clone instead of the shared user config directory')
    parser.add_argument('--replace', action='store_true', help='Explicitly replace an existing private config')
    parser.add_argument('--check', action='store_true', help='Check configuration without displaying credentials or contacting Google/MongoDB')
    parser.add_argument('--from-env', action='store_true', help='Save the Google OAuth variables already set in this terminal; no credentials are printed')
    args = parser.parse_args()
    try:
        if sum(bool(value) for value in (args.file, args.from_env, args.check)) > 1:
            parser.error('Choose a JSON file, --from-env or --check.')
        if args.file or args.from_env:
            if args.file:
                target = import_file(args.file, project=args.project, replace=args.replace)
            else:
                value = validate(os.getenv('LATEX_GOOGLE_CLIENT_ID', ''), os.getenv('LATEX_GOOGLE_CLIENT_SECRET', ''),
                                 os.getenv('LATEX_PUBLIC_URL', ''), 'environment')
                target = save_private(value, project=args.project, replace=args.replace)
            print('Private Google OAuth config saved: ' + str(target))
            print('Run scripts/run-python-web.bat normally. No OAuth environment commands are needed.')
        else:
            value = settings()
            print('Google OAuth: ' + ('configured (' + value.source + ')' if value.enabled else 'not configured'))
            print('Callback base: ' + (value.public_url or 'automatic: current browser host/port'))
            return 0 if value.enabled else 1
        return 0
    except GoogleSetupError as error:
        print('Google OAuth setup failed: ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
