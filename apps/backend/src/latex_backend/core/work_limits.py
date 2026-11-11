"""Per-process CPU job limits shared by all account workspaces."""
import asyncio
import os
import threading
from contextlib import asynccontextmanager, contextmanager
from latex_backend.storage.store import Problem


def limit(name, default):
    try:
        value = int(os.environ.get(name, default))
        if not 1 <= value <= 16:
            raise ValueError()
        return value
    except ValueError:
        raise Problem(f'{name} must be an integer from 1 to 16.', 503) from None


class WorkLimits:
    def __init__(self):
        self.compile = threading.BoundedSemaphore(limit('LATEX_MAX_COMPILE_JOBS', 2))
        self.word = threading.BoundedSemaphore(limit('LATEX_MAX_WORD_JOBS', 1))

    @asynccontextmanager
    async def compiling(self):
        while not self.compile.acquire(blocking=False):
            await asyncio.sleep(.05)
        try:
            yield
        finally:
            self.compile.release()

    @contextmanager
    def converting(self):
        if not self.word.acquire(timeout=180):
            raise Problem('Word conversion queue is busy. Try again shortly.', 503)
        try:
            yield
        finally:
            self.word.release()


_limits = None
_lock = threading.Lock()


def work_limits():
    global _limits
    with _lock:
        if _limits is None:
            _limits = WorkLimits()
        return _limits
