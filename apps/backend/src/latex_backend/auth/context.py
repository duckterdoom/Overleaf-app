"""Request identity follows async tasks and FastAPI worker threads."""
from contextvars import ContextVar

request_user = ContextVar('latex_request_user', default=None)
authorized_project = ContextVar('latex_authorized_project', default=None)
