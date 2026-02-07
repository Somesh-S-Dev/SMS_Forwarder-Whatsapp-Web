"""
Message classification for sensitive SMS types.

Classifies incoming SMS messages into categories:
- OTP (one-time passwords)
- TRANSACTION (debit/credit notifications)
- BILL (payment confirmations, due dates)
- SECURITY_ALERT (account security alerts)

Security: No sensitive values (OTPs, amounts, balances) are logged.
"""
from enum import Enum
from typing import Optional, Dict, Any
import re
from dataclasses import dataclass


class MessageType(str, Enum):
    """Message type categories."""
    OTP = "OTP"
    TRANSACTION = "TRANSACTION"
    BILL = "BILL"
    SECURITY_ALERT = "SECURITY_ALERT"
    UNKNOWN = "UNKNOWN"


@dataclass
class ClassificationResult:
    """Result of message classification."""
    message_type: MessageType
    confidence: float  # 0.0 to 1.0
    metadata: Dict[str, Any]  # Type-specific metadata (no sensitive values)


class MessageClassifier:
    """
    Classifies SMS messages based on content patterns.
    
    Uses regex patterns to detect message types.
    Does NOT log sensitive values (OTPs, amounts, balances, account numbers).
    """
    
    # OTP patterns
    OTP_PATTERNS = [
        r'\b(?:OTP|otp|code|verification|PIN|pin)\b.*?(\d{4,8})\b',
        r'\b(\d{4,8})\b.*?(?:OTP|otp|code|verification|PIN|pin)\b',
        r'\b(?:your|the)\s+(?:OTP|code|PIN)\s+(?:is|:)\s*(\d{4,8})\b',
    ]
    
    # Transaction patterns
    TRANSACTION_KEYWORDS = [
        r'\b(?:debited|credited|withdrawn|deposited|transferred)\b',
        r'\b(?:debit|credit|withdrawal|deposit|transfer)\b',
        r'\bA/c\b',
        r'\baccount\b.*?\bXX\d+\b',
        r'\bcard\b.*?\bXX\d+\b',
    ]
    
    # Amount patterns (for detection only, value not extracted)
    AMOUNT_PATTERNS = [
        r'(?:Rs\.?|INR|₹)\s*[\d,]+(?:\.\d{2})?',
        r'[\d,]+(?:\.\d{2})?\s*(?:Rs\.?|INR|₹)',
    ]
    
    # Bill patterns
    BILL_KEYWORDS = [
        r'\b(?:bill|invoice|payment|due|overdue)\b',
        r'\bpay\s+(?:by|before)\b',
        r'\bdue\s+(?:date|on)\b',
    ]
    
    # Security alert patterns
    SECURITY_KEYWORDS = [
        r'\b(?:alert|security|suspicious|unauthorized|blocked|locked)\b',
        r'\b(?:fraud|scam|phishing)\b',
        r'\bnew\s+(?:device|location|login)\b',
        r'\b(?:verify|confirm)\s+(?:identity|account)\b',
    ]
    
    def classify(self, message_body: str, sender: str) -> ClassificationResult:
        """
        Classify SMS message into type category.
        
        Args:
            message_body: The SMS text content
            sender: The SMS sender (for context)
        
        Returns:
            ClassificationResult with type, confidence, and metadata
        
        Security: Does NOT extract or return sensitive values
        """
        message_lower = message_body.lower()
        
        # Check each type in priority order
        
        # 1. OTP (highest priority)
        otp_result = self._check_otp(message_body, message_lower)
        if otp_result:
            return otp_result
        
        # 2. Transaction
        transaction_result = self._check_transaction(message_body, message_lower)
        if transaction_result:
            return transaction_result
        
        # 3. Security Alert
        security_result = self._check_security_alert(message_body, message_lower)
        if security_result:
            return security_result
        
        # 4. Bill
        bill_result = self._check_bill(message_body, message_lower)
        if bill_result:
            return bill_result
        
        # 5. Unknown
        return ClassificationResult(
            message_type=MessageType.UNKNOWN,
            confidence=0.0,
            metadata={"reason": "No matching patterns"}
        )
    
    def _check_otp(self, message: str, message_lower: str) -> Optional[ClassificationResult]:
        """Check if message is an OTP."""
        for pattern in self.OTP_PATTERNS:
            if re.search(pattern, message, re.IGNORECASE):
                return ClassificationResult(
                    message_type=MessageType.OTP,
                    confidence=0.95,
                    metadata={
                        "has_otp": True,
                        "urgency": "high"
                    }
                )
        
        # Check for standalone digits (4-8) with OTP context
        if re.search(r'\b\d{4,8}\b', message):
            otp_context_words = ['otp', 'code', 'verification', 'pin', 'password']
            if any(word in message_lower for word in otp_context_words):
                return ClassificationResult(
                    message_type=MessageType.OTP,
                    confidence=0.85,
                    metadata={
                        "has_otp": True,
                        "urgency": "high"
                    }
                )
        
        return None
    
    def _check_transaction(self, message: str, message_lower: str) -> Optional[ClassificationResult]:
        """Check if message is a transaction notification."""
        transaction_score = 0
        has_amount = False
        
        # Check for transaction keywords
        for pattern in self.TRANSACTION_KEYWORDS:
            if re.search(pattern, message, re.IGNORECASE):
                transaction_score += 1
        
        # Check for amount patterns
        for pattern in self.AMOUNT_PATTERNS:
            if re.search(pattern, message):
                has_amount = True
                transaction_score += 1
                break
        
        if transaction_score >= 2:
            return ClassificationResult(
                message_type=MessageType.TRANSACTION,
                confidence=0.9 if has_amount else 0.75,
                metadata={
                    "has_amount": has_amount,
                    "urgency": "medium"
                }
            )
        
        return None
    
    def _check_security_alert(self, message: str, message_lower: str) -> Optional[ClassificationResult]:
        """Check if message is a security alert."""
        security_score = 0
        
        for pattern in self.SECURITY_KEYWORDS:
            if re.search(pattern, message, re.IGNORECASE):
                security_score += 1
        
        if security_score >= 1:
            return ClassificationResult(
                message_type=MessageType.SECURITY_ALERT,
                confidence=0.85,
                metadata={
                    "urgency": "high"
                }
            )
        
        return None
    
    def _check_bill(self, message: str, message_lower: str) -> Optional[ClassificationResult]:
        """Check if message is a bill notification."""
        bill_score = 0
        has_amount = False
        
        # Check for bill keywords
        for pattern in self.BILL_KEYWORDS:
            if re.search(pattern, message, re.IGNORECASE):
                bill_score += 1
        
        # Check for amount patterns
        for pattern in self.AMOUNT_PATTERNS:
            if re.search(pattern, message):
                has_amount = True
                bill_score += 1
                break
        
        if bill_score >= 2:
            return ClassificationResult(
                message_type=MessageType.BILL,
                confidence=0.8,
                metadata={
                    "has_amount": has_amount,
                    "urgency": "low"
                }
            )
        
        return None


# Global classifier instance
message_classifier = MessageClassifier()
