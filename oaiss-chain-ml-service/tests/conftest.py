"""Shared pytest fixtures for ML service tests."""

import sys
from pathlib import Path

# Ensure the project root is on sys.path so app.* imports work
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest


@pytest.fixture
def sample_dates():
    """Generate 30 sample dates starting 2025-01-01."""
    return [(f"2025-01-{i:02d}") for i in range(1, 31)]


@pytest.fixture
def increasing_emissions():
    """Monotonically increasing emission values."""
    return [float(100 + i * 5) for i in range(30)]


@pytest.fixture
def decreasing_emissions():
    """Monotonically decreasing emission values."""
    return [float(300 - i * 5) for i in range(30)]


@pytest.fixture
def stable_emissions():
    """Flat emission values (constant)."""
    return [200.0] * 30


@pytest.fixture
def sample_prices():
    """30 carbon prices around 50."""
    return [50.0 + (i % 3) * 2.0 for i in range(30)]


@pytest.fixture
def sample_volumes():
    """30 trade volumes around 1000."""
    return [1000.0 + (i % 5) * 50.0 for i in range(30)]


@pytest.fixture
def sample_enterprise_request_data():
    """Valid enterprise inference request data (compliant)."""
    return {
        "enterprise_id": 1,
        "report_count": 15,
        "total_emissions": 500.0,
        "credit_score": 85.0,
        "emission_rating": 3.0,
        "transaction_volume": 200.0,
        "compliance_flags": 0,
        "avg_emission_per_report": 33.3,
        "days_since_last_report": 10,
    }
