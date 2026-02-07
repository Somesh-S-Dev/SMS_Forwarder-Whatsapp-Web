"""
Integration tests for WhatsApp service.

Note: These tests require valid WhatsApp API credentials.
Use mocking for CI/CD pipelines.
"""
import pytest
from unittest.mock import AsyncMock, patch
from whatsapp_service import WhatsAppService


class TestWhatsAppService:
    """Test WhatsApp API integration."""
    
    @pytest.fixture
    def whatsapp_service(self):
        """Create WhatsApp service with test config."""
        class MockSettings:
            whatsapp_api_token = "test_token"
            whatsapp_phone_number_id = "123456789"
            whatsapp_recipient_number = "1234567890"
            whatsapp_template_name = "otp_notification"
        
        import config
        original_settings = config.settings
        config.settings = MockSettings()
        
        service = WhatsAppService()
        
        config.settings = original_settings
        
        return service
    
    @pytest.mark.asyncio
    async def test_send_otp_message_success(self, whatsapp_service):
        """Test successful OTP message sending."""
        with patch('httpx.AsyncClient') as mock_client:
            # Mock successful response
            mock_response = AsyncMock()
            mock_response.status_code = 200
            mock_response.json.return_value = {
                "messages": [{"id": "wamid.test123"}]
            }
            
            mock_client.return_value.__aenter__.return_value.post = AsyncMock(
                return_value=mock_response
            )
            
            # Send message
            message_id = await whatsapp_service.send_otp_message("123456", "TEST-BANK")
            
            assert message_id == "wamid.test123"
    
    @pytest.mark.asyncio
    async def test_send_otp_message_failure(self, whatsapp_service):
        """Test OTP message sending failure."""
        with patch('httpx.AsyncClient') as mock_client:
            # Mock error response
            mock_response = AsyncMock()
            mock_response.status_code = 400
            mock_response.text = "Bad Request"
            
            mock_client.return_value.__aenter__.return_value.post = AsyncMock(
                return_value=mock_response
            )
            
            # Send message
            message_id = await whatsapp_service.send_otp_message("123456", "TEST-BANK")
            
            assert message_id is None
    
    @pytest.mark.asyncio
    async def test_health_check(self, whatsapp_service):
        """Test WhatsApp API health check."""
        with patch('httpx.AsyncClient') as mock_client:
            mock_response = AsyncMock()
            mock_response.status_code = 200
            
            mock_client.return_value.__aenter__.return_value.get = AsyncMock(
                return_value=mock_response
            )
            
            is_healthy = await whatsapp_service.health_check()
            
            assert is_healthy is True
