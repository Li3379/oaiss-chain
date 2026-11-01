"""
OAISS Chain ML Service - Prometheus Metrics

Exposes /metrics endpoint for Prometheus scraping using
prometheus-fastapi-instrumentator.
"""

from prometheus_client import Counter, Histogram
from prometheus_fastapi_instrumentator import Instrumentator

# Custom business metrics
PREDICTION_REQUESTS = Counter(
    "ml_prediction_requests_total",
    "Total number of prediction requests",
    ["model_type", "status"],
)

PREDICTION_LATENCY = Histogram(
    "ml_prediction_latency_seconds",
    "Prediction request latency in seconds",
    ["model_type"],
    buckets=[0.01, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0],
)

MODEL_TRAINING_REQUESTS = Counter(
    "ml_model_training_requests_total",
    "Total number of model training requests",
    ["model_type", "status"],
)


def setup_metrics(app):
    """Configure Prometheus metrics instrumentation for the FastAPI app."""
    instrumentator = Instrumentator(
        should_group_status_codes=True,
        should_ignore_untemplated=True,
        excluded_handlers=["/health", "/metrics"],
    )
    instrumentator.instrument(app)
    instrumentator.expose(app, endpoint="/metrics", include_in_schema=True)
