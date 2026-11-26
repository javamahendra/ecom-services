FROM python:3.13-alpine

WORKDIR /app
COPY exporter.py .
EXPOSE 9400
ENTRYPOINT ["python", "/app/exporter.py"]
