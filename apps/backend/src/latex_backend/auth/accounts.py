"""MongoDB is the authoritative identity and browser-session repository."""
import hashlib
import json
import secrets
import threading
import time
import uuid
from datetime import datetime, timedelta, timezone
from pymongo import ReturnDocument
from pymongo.errors import DuplicateKeyError
from latex_backend.storage.state import State
from latex_backend.storage.store import Problem


class Accounts:
    def __init__(self, home, database):
        self.db = database
        self.lock = threading.RLock()
        self.failures = {}
        self.dummy = State.password('unused-password')

    @staticmethod
    def public(user):
        return {'id': user['id'], 'email': user['email'], 'name': user['name'],
                'role': user.get('role'), 'needsRole': user.get('role') is None, 'offline': False,
                'provider': 'google' if user.get('google_sub') else 'password'}

    def user(self, identifier):
        return self.db.users.find_one({'_id': identifier})

    @staticmethod
    def name(value):
        if not isinstance(value, str) or not 1 <= len(value.strip()) <= 40:
            raise Problem('Tên hiển thị cần từ 1 đến 40 ký tự.')
        return value.strip()

    @staticmethod
    def password(value):
        if not isinstance(value, str) or not 8 <= len(value) <= 1024:
            raise Problem('Mật khẩu cần từ 8 đến 1.024 ký tự.')
        return State.password(value)

    def signup(self, name, email, password):
        identifier = uuid.uuid4().hex
        user = {'_id': identifier, 'id': identifier, 'name': self.name(name), 'email': State.email(email),
                'password': self.password(password), 'role': None, 'google_sub': None, 'auth_version': 0}
        try:
            self.db.users.insert_one(user)
        except DuplicateKeyError:
            raise Problem('Email đã được đăng ký. Hãy đăng nhập vào tài khoản hiện có.', 409)
        return user

    def throttle(self, key):
        with self.lock:
            now = time.time()
            self.failures = {k: v for k, v in self.failures.items() if v[1] > now - 900}
            count, _ = self.failures.get(key, (0, now))
            if count >= 12:
                raise Problem('Quá nhiều lần thử. Vui lòng thử lại sau 15 phút.', 429)
            self.failures[key] = (count + 1, now)

    def login(self, email, password):
        user = self.db.users.find_one({'email': State.email(email)})
        hashed = user.get('password') if user else None
        if isinstance(hashed, str):
            hashed = json.loads(hashed)
        if not State.matches(hashed or self.dummy, password) or not user or not user.get('password'):
            raise Problem('Email hoặc mật khẩu không đúng.', 401)
        return user

    def google(self, claims):
        if not claims.get('sub') or claims.get('email_verified') is not True:
            raise Problem('Tài khoản Google chưa có email được xác minh.', 401)
        user = self.db.users.find_one({'google_sub': claims['sub']})
        if user:
            return user
        email = State.email(claims.get('email', ''))
        identifier = uuid.uuid4().hex
        user = {'_id': identifier, 'id': identifier, 'email': email, 'google_sub': claims['sub'],
                'name': str(claims.get('name') or email.split('@')[0])[:40], 'role': None,
                'password': None, 'auth_version': 0}
        try:
            self.db.users.insert_one(user)
        except DuplicateKeyError:
            existing = self.db.users.find_one({'google_sub': claims['sub']})
            if existing:
                return existing
            raise Problem('Email đã có tài khoản Latex. Hãy đăng nhập bằng mật khẩu; không tự động liên kết tài khoản.', 409)
        return user

    def choose_role(self, identifier, role, name):
        if role not in {'student', 'tutor'}:
            raise Problem('Chọn Tutor hoặc Student.')
        user = self.db.users.find_one_and_update({'_id': identifier, 'role': {'$in': [None, role]}},
            {'$set': {'role': role, 'name': self.name(name)}}, return_document=ReturnDocument.AFTER)
        if not user:
            raise Problem('Vai trò chính đã được thiết lập.', 409)
        return user

    def issue(self, identifier, remember=False):
        user = self.user(identifier)
        token, lifetime = secrets.token_urlsafe(32), 30 * 86400 if remember else 12 * 3600
        self.db.sessions.insert_one({'_id': hashlib.sha256(token.encode()).hexdigest(), 'user_id': identifier,
            'version': user.get('auth_version', 0), 'expires_at': datetime.now(timezone.utc) + timedelta(seconds=lifetime)})
        return token, lifetime

    def resolve(self, token):
        if not token:
            return None
        session = self.db.sessions.find_one({'_id': hashlib.sha256(token.encode()).hexdigest(),
            'expires_at': {'$gt': datetime.now(timezone.utc)}})
        if not session:
            return None
        return self.db.users.find_one({'_id': session['user_id'], 'auth_version': session.get('version', 0)})

    def revoke(self, token):
        self.db.sessions.delete_one({'_id': hashlib.sha256(token.encode()).hexdigest()})

    def profile(self, identifier, data):
        user = self.user(identifier)
        values = {'name': self.name(data.get('name', user['name']))}
        if data.get('email') is not None or data.get('password') is not None:
            hashed = user.get('password')
            if isinstance(hashed, str):
                hashed = json.loads(hashed)
            if not hashed or not State.matches(hashed, data.get('currentPassword', '')):
                raise Problem('Mật khẩu hiện tại không đúng hoặc tài khoản sử dụng Google.', 401)
            if data.get('email') is not None:
                values['email'] = State.email(data['email'])
            if data.get('password') is not None:
                values['password'] = self.password(data['password'])
        update = {'$set': values}
        if 'password' in values:
            update['$inc'] = {'auth_version': 1}
        try:
            result = self.db.users.find_one_and_update({'_id': identifier, 'auth_version': user.get('auth_version', 0)},
                update, return_document=ReturnDocument.AFTER)
        except DuplicateKeyError:
            raise Problem('Email đã được dùng.', 409)
        if result is None:
            raise Problem('Hồ sơ đã thay đổi, hãy đăng nhập lại.', 409)
        if 'password' in values:
            self.db.sessions.delete_many({'user_id': identifier})
        return result

    def close(self):
        pass  # The application owns and closes its MongoClient.
