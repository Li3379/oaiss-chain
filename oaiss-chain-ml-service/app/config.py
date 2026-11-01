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

    # ---------------- LLM（可选，默认关闭） ----------------
    # 未配置 llm_api_key 且未配置 ollama_base_url 时，Copilot 一律走内置
    # GB/T 32150-2015 规则引擎，响应体 model_used 会如实标注为「内置规则引擎」，
    # 不会把规则拼接文本伪装成大模型输出。
    # 启用方式：设置环境变量 LLM_API_KEY（OpenAI 兼容端点），或在本地起 Ollama 后设置
    # OLLAMA_BASE_URL。密钥只从环境变量注入，不写入任何代码或仓库文件。
    llm_api_base: str = (
        "https://api.deepseek.com/v1"  # OpenAI 兼容端点 base，需包含 /v1
    )
    llm_api_key: str = ""  # 空 = 不启用外部 LLM
    llm_model: str = "deepseek-chat"
    llm_timeout_seconds: float = 45.0
    ollama_base_url: str = ""  # 空 = 不探测本地 Ollama
    ollama_model: str = "qwen2.5:7b"

    model_config = {"env_prefix": "", "case_sensitive": False}


settings = Settings()
