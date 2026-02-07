"""
Configuration management using Pydantic Settings.
All secrets are loaded from environment variables.
"""
from pydantic_settings import BaseSettings
from pydantic import Field, validator
from typing import Optional
import sys


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""
    
    # Encryption settings
    aes_encryption_key: str = Field(..., min_length=64, max_length=64)
    hmac_secret_key: str = Field(..., min_length=64, max_length=64)
    
    # WhatsApp Business API settings
    whatsapp_api_token: str = Field(..., min_length=10)
    whatsapp_phone_number_id: str = Field(..., min_length=10)
    whatsapp_recipient_number: str = Field(..., min_length=10)
    
    # WhatsApp Templates (one per message type)
    whatsapp_template_otp: str = Field(default="otp_notification")
    whatsapp_template_transaction: str = Field(default="transaction_alert")
    whatsapp_template_bill: str = Field(default="bill_notification")
    whatsapp_template_security: str = Field(default="security_alert")
    
    # Redis settings (optional)
    redis_url: Optional[str] = Field(default=None)
    
    # API settings
    api_rate_limit: str = Field(default="10/minute")
    cors_origins: str = Field(default="android-app://com.secure.otpforwarder")
    
    # Message Storage TTL (in seconds) - per message type
    ttl_otp: int = Field(default=300, ge=60, le=600)          # 5 minutes (strict)
    ttl_transaction: int = Field(default=600, ge=60, le=900)   # 10 minutes
    ttl_bill: int = Field(default=900, ge=60, le=1800)         # 15 minutes
    ttl_security: int = Field(default=600, ge=60, le=900)      # 10 minutes
    
    # Duplicate detection window (in seconds)
    duplicate_detection_window: int = Field(default=3600, ge=300, le=7200)  # 1 hour
    
    @validator('aes_encryption_key', 'hmac_secret_key')
    def validate_hex_key(cls, v: str) -> str:
        """Validate that keys are valid hexadecimal strings."""
        try:
            bytes.fromhex(v)
        except ValueError:
            raise ValueError("Key must be a valid hexadecimal string")
        return v
    
    @validator('whatsapp_recipient_number')
    def validate_phone_number(cls, v: str) -> str:
        """Validate phone number format (should include country code)."""
        # Remove any spaces or special characters
        cleaned = ''.join(filter(str.isdigit, v))
        if len(cleaned) < 10:
            raise ValueError("Phone number must include country code and be at least 10 digits")
        return cleaned
    
    class Config:
        env_file = ".env"
        case_sensitive = False


def load_settings() -> Settings:
    """
    Load and validate settings from environment variables.
    Exits with error message if validation fails.
    """
    try:
        return Settings()
    except Exception as e:
        print(f"❌ Configuration Error: {e}", file=sys.stderr)
        print("\n💡 Please ensure all required environment variables are set.", file=sys.stderr)
        print("   See .env.example for reference.\n", file=sys.stderr)
        sys.exit(1)


# Global settings instance
settings = load_settings()
