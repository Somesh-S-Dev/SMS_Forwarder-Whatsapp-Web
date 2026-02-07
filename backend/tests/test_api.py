"""
API endpoint tests.

Tests the FastAPI endpoints with security validations.
"""
import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch, AsyncMock
import time


@pytest.fixture
def client():
    """Create test client."""
    # Mock settings before importing main
    with patch('config.settings') as mock_settings:
        mock_settings.aes_encryption_key = "0" * 64
        mock_settings.hmac_secret_key = "1" * 64
        mock_settings.whatsapp_api_token = "test_token"
        mock_settings.whatsapp_phone_number_id = "123456"
        mock_settings.whatsapp_recipient_number = "1234567890"
        mock_settings.whatsapp_template_name = "otp_notification"
        mock_settings.redis_url = None
        mock_settings.api_rate_limit = "10/minute"
        mock_settings.cors_origins = "test-origin"
        mock_settings.otp_ttl_seconds = 300
        
        from main import app
        return TestClient(app)


def test_health_check(client):
    """Test health check endpoint."""
    with patch('storage_service.storage_service.health_check', return_value=True), \
         patch('whatsapp_service.whatsapp_service.health_check', new_callable=AsyncMock, return_value=True):
        
        response = client.get("/health")
        
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "healthy"
        assert data["redis_connected"] is True
        assert data["whatsapp_api_configured"] is True


def test_forward_otp_invalid_timestamp(client):
    """Test OTP forwarding with invalid timestamp."""
    old_timestamp = int(time.time()) - 400  # 6+ minutes ago
    
    payload = {
        "encrypted_payload": "test_payload",
        "hmac_signature": "0" * 64,
        "sender": "TEST",
        "timestamp": old_timestamp
    }
    
    response = client.post("/forward-otp", json=payload)
    
    assert response.status_code == 400
    assert "timestamp" in response.json()["detail"].lower()


def test_forward_otp_invalid_hmac(client):
    """Test OTP forwarding with invalid HMAC."""
    payload = {
        "encrypted_payload": "test_payload",
        "hmac_signature": "0" * 64,  # Invalid signature
        "sender": "TEST",
        "timestamp": int(time.time())
    }
    
    response = client.post("/forward-otp", json=payload)
    
    assert response.status_code == 401
    assert "authentication" in response.json()["detail"].lower()


def test_rate_limiting(client):
    """Test rate limiting enforcement."""
    # This test would need to be adjusted based on actual rate limit config
    # For now, just verify the endpoint exists
    response = client.get("/health")
    assert response.status_code == 200
