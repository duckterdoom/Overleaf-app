FROM python:3.12-slim
WORKDIR /opt/overleaf
COPY apps/backend ./backend
RUN pip install --no-cache-dir ./backend && useradd --system --uid 10001 overleaf
USER overleaf
EXPOSE 8080
ENTRYPOINT ["python", "-m", "latex_backend.collaboration.network"]
