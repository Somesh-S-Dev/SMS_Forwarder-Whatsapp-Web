"""
Pydantic models for request/response validation.
No sensitive values (OTPs, amounts, balances) are stored in these models - only encrypted payloads.
"""
from pydantic import BaseModel, Field
from typing import Optional
from enum import Enum


class MessageType(str, Enum):
    """Message type categories."""
    OTP = "OTP"
    TRANSACTION = "TRANSACTION"
    BILL = "BILL"
    SECURITY_ALERT = "SECURITY_ALERT"
    UNKNOWN = "UNKNOWN"


class EncryptedMessageRequest(BaseModel):
    """
    Request model for encrypted message forwarding.
    
    Security notes:
    - encrypted_payload contains: IV (12 bytes) + ciphertext + auth_tag (16 bytes)
    - All fields are base64 encoded
    - HMAC signature covers the entire encrypted_payload
    - message_type indicates classification (OTP, TRANSACTION, etc.)
    - message_hash for duplicate detection
    """
    encrypted_payload: str = Field(..., min_length=1, max_length=2048)
    hmac_signature: str = Field(..., min_length=64, max_length=64)
    sender: str = Field(..., min_length=1, max_length=100)
    message_type: MessageType = Field(default=MessageType.UNKNOWN)
    timestamp: int = Field(..., gt=0)
    message_hash: Optional[str] = Field(default=None, min_length=64, max_length=64)
    
    class Config:
        json_schema_extra = {
            "example": {
                "encrypted_payload": "base64_encoded_iv_ciphertext_tag",
                "hmac_signature": "hex_encoded_hmac_sha256",
                "sender": "BANK-ALERT",
                "message_type": "TRANSACTION",
                "timestamp": 1707287400,
                "message_hash": "hex_encoded_sha256_of_message"
            }
        }


class ForwardResponse(BaseModel):
    """Response model for OTP forwarding endpoint."""
    success: bool
    message: str
    whatsapp_message_id: Optional[str] = None
    
    class Config:
        json_schema_extra = {
            "example": {
                "success": True,
                "message": "OTP forwarded successfully",
                "whatsapp_message_id": "wamid.xxx"
            }
        }


class HealthResponse(BaseModel):
    """Health check response."""
    status: str
    redis_connected: bool
    whatsapp_api_configured: bool


class VerificationRequest(BaseModel):
    """Request model for sending verification OTP."""
    whatsapp_number: str = Field(..., min_length=10, max_length=20)


class OtpVerifyRequest(BaseModel):
    """Request model for verifying OTP."""
    whatsapp_number: str = Field(..., min_length=10, max_length=20)
    otp: str = Field(..., min_length=6, max_length=6)


class UserRegistrationRequest(BaseModel):
    """Request model for user registration."""
    name: str = Field(..., min_length=1, max_length=100)
    whatsapp_number: str = Field(..., min_length=10, max_length=20)
    verification_token: str = Field(..., min_length=32)


class StatusResponse(BaseModel):
    """Generic status response."""
    success: bool
    message: str
    token: Optional[str] = None
