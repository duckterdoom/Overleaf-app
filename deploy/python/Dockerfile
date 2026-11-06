FROM python:3.12-slim
WORKDIR /opt/overleaf
COPY apps/desktop ./desktop
RUN pip install --no-cache-dir ./desktop && useradd --system --uid 10001 overleaf
USER overleaf
EXPOSE 8080
ENTRYPOINT ["python", "-m", "overleaf_desktop.network"]
