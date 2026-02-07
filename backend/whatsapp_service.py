"""
WhatsApp Business Cloud API integration with multi-template support.

Security requirements:
- Template-based messaging only (no free-form messages)
- No sensitive values (OTPs, amounts, balances) in logs
- Proper error handling
- API token from environment variables only
"""
import httpx
import logging
from typing import Optional, List, Dict, Any
from config import settings
from message_classifier import MessageType
from template_manager import template_manager

logger = logging.getLogger(__name__)


class WhatsAppService:
    """
    Handles WhatsApp Business Cloud API communication.
    
    Uses template-based messaging to comply with WhatsApp policies.
    Supports multiple templates for different message types.
    """
    
    def __init__(self):
        self.api_url = f"https://graph.facebook.com/v18.0/{settings.whatsapp_phone_number_id}/messages"
        self.headers = {
            "Authorization": f"Bearer {settings.whatsapp_api_token}",
            "Content-Type": "application/json"
        }
    
    async def send_message(
        self,
        message_content: str,
        sender: str,
        message_type: MessageType
    ) -> Optional[str]:
        """
        Send message via WhatsApp using appropriate template.
        
        Args:
            message_content: The decrypted message content
            sender: The SMS sender name
            message_type: Classified message type (OTP, TRANSACTION, etc.)
        
        Returns:
            WhatsApp message ID if successful, None otherwise
        
        Security notes:
        - Uses template-based messaging (must be pre-approved by Meta)
        - Sensitive data is masked in transaction/bill templates
        - No sensitive values are logged
        """
        # Get template name for message type
        template_name = template_manager.get_template_name(message_type)
        
        # Render template parameters
        template_params = template_manager.render_template_params(
            message_type,
            sender,
            message_content
        )
        
        # Construct template message payload
        payload = {
            "messaging_product": "whatsapp",
            "to": settings.whatsapp_recipient_number,
            "type": "template",
            "template": {
                "name": template_name,
                "language": {
                    "code": "en"
                },
                "components": [
                    {
                        "type": "body",
                        "parameters": template_params
                    }
                ]
            }
        }
        
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.post(
                    self.api_url,
                    headers=self.headers,
                    json=payload
                )
                
                if response.status_code == 200:
                    data = response.json()
                    message_id = data.get("messages", [{}])[0].get("id")
                    logger.info(
                        f"✅ WhatsApp {message_type} message sent successfully "
                        f"(ID: {message_id}, template: {template_name})"
                    )
                    return message_id
                else:
                    # Log error without exposing sensitive data
                    logger.error(
                        f"❌ WhatsApp API error: {response.status_code} - "
                        f"{response.text[:200]}"  # Truncate to avoid logging sensitive data
                    )
                    return None
                    
        except httpx.TimeoutException:
            logger.error(f"❌ WhatsApp API timeout for {message_type} message")
            return None
        except Exception as e:
            # Don't log the full exception (might contain sensitive data)
            logger.error(f"❌ WhatsApp API error for {message_type}: {type(e).__name__}")
            return None
    
    async def health_check(self) -> bool:
        """
        Check if WhatsApp API is configured and accessible.
        
        Returns:
            True if API is reachable, False otherwise
        """
        try:
            # Simple check: verify phone number ID is accessible
            url = f"https://graph.facebook.com/v18.0/{settings.whatsapp_phone_number_id}"
            async with httpx.AsyncClient(timeout=5.0) as client:
                response = await client.get(
                    url,
                    headers={"Authorization": f"Bearer {settings.whatsapp_api_token}"}
                )
                return response.status_code == 200
        except Exception:
            return False


# Global WhatsApp service instance
whatsapp_service = WhatsAppService()
