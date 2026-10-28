import sys
import unittest
from pathlib import Path
from typing import Annotated

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient

from app.config import settings
from app.security import require_ml_service_secret


class MlServiceSecurityTest(unittest.TestCase):
    TEST_SECRET = "test-secret-for-unit-tests-only"

    def setUp(self) -> None:
        self.original_secret = settings.ml_service_secret
        settings.ml_service_secret = self.TEST_SECRET

        app = FastAPI()

        @app.get("/health")
        def health() -> dict[str, str]:
            return {"status": "healthy"}

        @app.post("/secured")
        def secured_route(
            _authorized: Annotated[None, Depends(require_ml_service_secret)],
        ) -> dict[str, str]:
            return {"status": "ok"}

        self.client = TestClient(app)

    def tearDown(self) -> None:
        settings.ml_service_secret = self.original_secret

    def test_health_endpoint_remains_public(self) -> None:
        response = self.client.get("/health")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["status"], "healthy")

    def test_secured_route_rejects_missing_secret(self) -> None:
        response = self.client.post("/secured")

        self.assertEqual(response.status_code, 401)
        self.assertEqual(response.json()["detail"], "Invalid ML service secret")

    def test_secured_route_accepts_correct_secret(self) -> None:
        response = self.client.post(
            "/secured",
            headers={"X-ML-Service-Secret": self.TEST_SECRET},
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["status"], "ok")


if __name__ == "__main__":
    unittest.main()
