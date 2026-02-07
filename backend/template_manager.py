"""
WhatsApp template manager for multiple message types.

Manages different WhatsApp Business API templates for:
- OTP messages (Authentication category)
- Transaction messages (Utility category)
- Bill messages (Utility category)
- Security alerts (Utility category)

Security: Template selection based on message type, no sensitive data in logs.
"""
from typing import Dict, List, Any
from message_classifier import MessageType
from config import settings
import logging

logger = logging.getLogger(__name__)


class TemplateManager:
    """
    Manages WhatsApp message templates for different message types.
    
    Each message type uses a category-appropriate template approved by Meta.
    """
    
    def __init__(self):
        """Initialize template configurations from settings."""
        self.templates = {
            MessageType.OTP: {
                "name": settings.whatsapp_template_otp,
                "category": "AUTHENTICATION",
                "language": "en"
            },
            MessageType.TRANSACTION: {
                "name": settings.whatsapp_template_transaction,
                "category": "UTILITY",
                "language": "en"
            },
            MessageType.BILL: {
                "name": settings.whatsapp_template_bill,
                "category": "UTILITY",
                "language": "en"
            },
            MessageType.SECURITY_ALERT: {
                "name": settings.whatsapp_template_security,
                "category": "UTILITY",
                "language": "en"
            }
        }
    
    def get_template_name(self, message_type: MessageType) -> str:
        """
        Get template name for message type.
        
        Args:
            message_type: The classified message type
        
        Returns:
            Template name string
        """
        template = self.templates.get(message_type)
        if not template:
            logger.warning(f"No template configured for message type: {message_type}")
            # Fallback to OTP template
            return self.templates[MessageType.OTP]["name"]
        
        return template["name"]
    
    def render_template_params(
        self,
        message_type: MessageType,
        sender: str,
        message_content: str
    ) -> List[Dict[str, str]]:
        """
        Render template parameters based on message type.
        
        Args:
            message_type: The classified message type
            sender: SMS sender name
            message_content: The decrypted message content
        
        Returns:
            List of template parameters
        
        Security: Masks sensitive data in transaction/bill messages
        """
        if message_type == MessageType.OTP:
            return self._render_otp_params(sender, message_content)
        elif message_type == MessageType.TRANSACTION:
            return self._render_transaction_params(sender, message_content)
        elif message_type == MessageType.BILL:
            return self._render_bill_params(sender, message_content)
        elif message_type == MessageType.SECURITY_ALERT:
            return self._render_security_params(sender, message_content)
        else:
            # Fallback: generic message
            return self._render_generic_params(sender, message_content)
    
    def _render_otp_params(self, sender: str, message_content: str) -> List[Dict[str, str]]:
        """
        Render OTP template parameters.
        
        Template: "Your OTP from {{1}} is {{2}}. Valid for 5 minutes."
        """
        # Extract OTP (4-8 digits)
        import re
        otp_match = re.search(r'\b(\d{4,8})\b', message_content)
        otp_value = otp_match.group(1) if otp_match else "******"
        
        return [
            {"type": "text", "text": sender},      # {{1}} - sender
            {"type": "text", "text": otp_value}    # {{2}} - OTP
        ]
    
    def _render_transaction_params(self, sender: str, message_content: str) -> List[Dict[str, str]]:
        """
        Render transaction template parameters.
        
        Template: "{{1}} from {{2}}: {{3}}"
        
        Security: Masks amounts and account numbers
        """
        # Determine transaction type
        message_lower = message_content.lower()
        if 'debit' in message_lower or 'withdrawn' in message_lower:
            transaction_type = "Debit Alert"
        elif 'credit' in message_lower or 'deposit' in message_lower:
            transaction_type = "Credit Alert"
        else:
            transaction_type = "Transaction Alert"
        
        # Mask sensitive data in summary
        summary = self._mask_sensitive_data(message_content)
        
        return [
            {"type": "text", "text": transaction_type},  # {{1}} - type
            {"type": "text", "text": sender},            # {{2}} - sender
            {"type": "text", "text": summary}            # {{3}} - summary
        ]
    
    def _render_bill_params(self, sender: str, message_content: str) -> List[Dict[str, str]]:
        """
        Render bill template parameters.
        
        Template: "Bill notification from {{1}}: {{2}}"
        
        Security: Masks amounts
        """
        summary = self._mask_sensitive_data(message_content)
        
        return [
            {"type": "text", "text": sender},    # {{1}} - sender
            {"type": "text", "text": summary}    # {{2}} - summary
        ]
    
    def _render_security_params(self, sender: str, message_content: str) -> List[Dict[str, str]]:
        """
        Render security alert template parameters.
        
        Template: "Security alert from {{1}}: {{2}}"
        """
        # Truncate if too long
        summary = message_content[:200] if len(message_content) > 200 else message_content
        
        return [
            {"type": "text", "text": sender},    # {{1}} - sender
            {"type": "text", "text": summary}    # {{2}} - alert text
        ]
    
    def _render_generic_params(self, sender: str, message_content: str) -> List[Dict[str, str]]:
        """Fallback generic parameters."""
        summary = message_content[:200] if len(message_content) > 200 else message_content
        
        return [
            {"type": "text", "text": sender},
            {"type": "text", "text": summary}
        ]
    
    def _mask_sensitive_data(self, text: str) -> str:
        """
        Mask sensitive data in message text.
        
        Masks:
        - Amounts (Rs 5000 → Rs ****)
        - Account numbers (XX1234 → XX****)
        - Card numbers (similar)
        - Balances
        
        Returns masked text suitable for WhatsApp template.
        """
        import re
        
        masked = text
        
        # Mask amounts
        masked = re.sub(
            r'(?:Rs\.?|INR|₹)\s*[\d,]+(?:\.\d{2})?',
            'Rs ****',
            masked
        )
        
        # Mask account numbers (keep first 2 chars)
        masked = re.sub(
            r'\bXX\d+\b',
            'XX****',
            masked
        )
        
        # Mask standalone large numbers (likely amounts or account refs)
        masked = re.sub(
            r'\b\d{4,}\b',
            '****',
            masked
        )
        
        # Truncate if still too long
        if len(masked) > 200:
            masked = masked[:197] + "..."
        
        return masked


# Global template manager instance
template_manager = TemplateManager()
