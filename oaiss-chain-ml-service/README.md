# OAISS CHAIN ML Service

AI-powered prediction microservice for carbon emission analysis, credit scoring, and trade volume prediction.

## Quick Start

```bash
# Install dependencies
pip install -r requirements.txt

# Run locally
uvicorn app.main:app --host 0.0.0.0 --port 8001

# Optional: require backend shared secret on prediction endpoints
export ML_SERVICE_SECRET=your-shared-secret
uvicorn app.main:app --host 0.0.0.0 --port 8001

# Run with Docker
docker-compose up ml-service
```

## Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/docs` | GET | OpenAPI documentation |

When `ML_SERVICE_SECRET` is configured, prediction endpoints require the
`X-ML-Service-Secret` header. `/health` remains unauthenticated for probes.

## Copilot LLM (optional)

The `/copilot/*` endpoints work without any LLM: with no LLM configured they run
the built-in GB/T 32150-2015 rule engine and report `model_used` as
`builtin-rule-engine (…, 未调用大模型)` — rule-based text is never labelled as a
model's output.

To enable a real model, set environment variables (never commit a key):

| Variable | Default | Meaning |
|----------|---------|---------|
| `LLM_API_BASE` | `https://api.deepseek.com/v1` | OpenAI-compatible base URL |
| `LLM_API_KEY` | empty | empty = LLM disabled |
| `LLM_MODEL` | `deepseek-chat` | model name at that endpoint |
| `LLM_TIMEOUT_SECONDS` | `45` | request timeout |
| `OLLAMA_BASE_URL` | empty | e.g. `http://host.docker.internal:11434/v1`; empty = not probed |
| `OLLAMA_MODEL` | `qwen2.5:7b` | model served by that Ollama |

When a report has no usable emission data, `/copilot/report-analysis` returns
`insufficient_data: true` with an explanation instead of inventing figures.

## Project Structure

```
oaiss-chain-ml-service/
├── app/
│   ├── main.py          # FastAPI entry point
│   ├── models/          # ML model artifacts
│   ├── routers/         # API route handlers
│   ├── schemas/         # Pydantic request/response schemas
│   └── services/        # Prediction service logic
├── requirements.txt     # Python dependencies
├── Dockerfile           # Container image
└── README.md
```
