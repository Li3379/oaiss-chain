"""
OAISS CHAIN ML Service - Configuration

Reads configuration from environment variables using pydantic-settings.
"""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """ML service configuration from environment variables."""

    model_dir: str = "/app/models"
    log_level: str = "INFO"
    app_name: str = "OAISS Chain ML Service"
    ml_service_secret: str = "oaiss-ml-shared-secret-key-2026"

    model_config = {"env_prefix": "", "case_sensitive": False}


settings = Settings()
