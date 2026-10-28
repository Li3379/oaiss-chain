"""
Enterprise inference service using IsolationForest anomaly detection
and XGBoost compliance risk classification.
"""

import logging

import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler
import xgboost as xgb

from app.schemas.enterprise import (
    ComplianceStatus,
    EnterpriseInferenceRequest,
    EnterpriseInferenceResponse,
)

logger = logging.getLogger(__name__)

# Feature column order for model input
_FEATURE_NAMES = [
    "report_count",
    "total_emissions",
    "credit_score",
    "emission_rating",
    "transaction_volume",
    "compliance_flags",
    "avg_emission_per_report",
    "days_since_last_report",
]


class EnterpriseService:
    """Enterprise compliance inference and anomaly detection service.

    Uses IsolationForest for anomaly detection and XGBoost for
    compliance risk classification (compliant / at_risk / non_compliant).
    Models are pre-trained on synthetic data at initialization so the
    service is always ready to serve predictions.
    """

    def __init__(self) -> None:
        self.model_version = "1.0.0"
        self._scaler: StandardScaler
        self._iso_model: IsolationForest
        self._xgb_model: xgb.XGBClassifier
        self._train_models()

    def _train_models(self) -> None:
        """Pre-train IsolationForest and XGBoost on synthetic normal data."""
        rng = np.random.RandomState(42)
        n_samples = 200

        # Generate synthetic normal enterprise data
        synthetic = np.column_stack([
            rng.normal(10, 3, n_samples),      # report_count
            rng.normal(500, 100, n_samples),   # total_emissions
            rng.normal(75, 10, n_samples),     # credit_score
            rng.normal(3, 1, n_samples),       # emission_rating
            rng.normal(200, 50, n_samples),    # transaction_volume
            rng.normal(0.5, 0.5, n_samples),   # compliance_flags
            rng.normal(50, 15, n_samples),     # avg_emission_per_report
            rng.normal(30, 15, n_samples),     # days_since_last_report
        ])
        # Clip to physically valid ranges
        synthetic = np.clip(synthetic, [
            0, 0, 0, 0, 0, 0, 0, 0
        ], [
            1000, 10000, 100, 10, 10000, 20, 5000, 365
        ])

        # Fit scaler and IsolationForest
        self._scaler = StandardScaler()
        scaled = self._scaler.fit_transform(synthetic)

        self._iso_model = IsolationForest(
            contamination=0.1,
            random_state=42,
            n_estimators=100,
        )
        self._iso_model.fit(scaled)

        # Generate labels for XGBoost classifier
        credit_scores = synthetic[:, 2]
        compliance_flags = synthetic[:, 5]
        labels = np.zeros(n_samples, dtype=int)
        for i in range(n_samples):
            if credit_scores[i] > 60 and compliance_flags[i] < 2:
                labels[i] = 0  # compliant
            elif credit_scores[i] > 40:
                labels[i] = 1  # at_risk
            else:
                labels[i] = 2  # non_compliant

        self._xgb_model = xgb.XGBClassifier(
            n_estimators=100,
            max_depth=3,
            learning_rate=0.1,
            objective="multi:softmax",
            num_class=3,
            random_state=42,
            use_label_encoder=False,
            eval_metric="mlogloss",
        )
        self._xgb_model.fit(scaled, labels)

        logger.info(
            "EnterpriseService models trained: IsolationForest + XGBoost (v%s)",
            self.model_version,
        )

    def infer(self, request: EnterpriseInferenceRequest) -> EnterpriseInferenceResponse:
        """Run enterprise compliance inference and anomaly detection.

        Args:
            request: Enterprise feature data for inference.

        Returns:
            EnterpriseInferenceResponse with compliance status, confidence,
            anomaly detection results, and risk factors.

        Raises:
            ValueError: If feature vector construction fails.
        """
        features = np.array([[
            request.report_count,
            request.total_emissions,
            request.credit_score,
            request.emission_rating,
            request.transaction_volume,
            request.compliance_flags,
            request.avg_emission_per_report,
            request.days_since_last_report,
        ]])

        scaled = self._scaler.transform(features)

        # Anomaly detection
        anomaly_score = float(self._iso_model.decision_function(scaled)[0])
        is_anomaly = self._iso_model.predict(scaled)[0] == -1

        # Compliance classification
        class_pred = int(self._xgb_model.predict(scaled)[0])
        status_map = {0: ComplianceStatus.COMPLIANT, 1: ComplianceStatus.AT_RISK, 2: ComplianceStatus.NON_COMPLIANT}
        compliance_status = status_map.get(class_pred, ComplianceStatus.AT_RISK)

        # Confidence from predict_proba
        proba = self._xgb_model.predict_proba(scaled)[0]
        confidence = float(np.max(proba))

        # Rule-based risk factor extraction
        risk_factors: list[str] = []
        if request.credit_score < 50:
            risk_factors.append("Low credit score")
        if request.compliance_flags >= 3:
            risk_factors.append("High compliance flag count")
        if request.days_since_last_report > 90:
            risk_factors.append("Extended reporting gap")
        if is_anomaly:
            risk_factors.append("Anomalous emission pattern detected")
        if request.avg_emission_per_report > 100:
            risk_factors.append("High average emissions per report")

        response = EnterpriseInferenceResponse(
            enterprise_id=request.enterprise_id,
            compliance_status=compliance_status,
            confidence=confidence,
            anomaly_score=anomaly_score,
            is_anomaly=is_anomaly,
            risk_factors=risk_factors,
            model_version=self.model_version,
        )

        logger.info(
            "Enterprise inference: id=%d status=%s confidence=%.3f anomaly=%s",
            request.enterprise_id,
            compliance_status.value,
            confidence,
            is_anomaly,
        )

        return response
