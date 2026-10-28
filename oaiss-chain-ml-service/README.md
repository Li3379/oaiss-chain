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
