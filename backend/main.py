"""
FastAPI backend for secure sensitive SMS forwarding.

Security features:
- HTTPS only (enforced by deployment)
- Rate limiting (10 requests/minute)
- HMAC signature verification
- AES-256-GCM encryption
- No sensitive data logging (OTPs, amounts, balances, etc.)
- TTL-based storage with configurable durations per message type
- Duplicate detection using message hashes
"""
from fastapi import FastAPI, HTTPException, Request, status
from fastapi.middleware.cors import CORSMiddleware
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded
import logging
import time
import hashlib
import secrets
import random

from models import (
    EncryptedMessageRequest, 
    ForwardResponse, 
    HealthResponse, 
    MessageType,
    VerificationRequest,
    OtpVerifyRequest,
    UserRegistrationRequest,
    StatusResponse
)
from crypto_service import crypto_service
from storage_service import storage_service
from whatsapp_service import whatsapp_service
from message_classifier import message_classifier
from config import settings

# Configure logging (ensure no sensitive values are logged)
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Initialize FastAPI app
app = FastAPI(
    title="Secure Sensitive SMS Forwarder",
    description="Backend service for encrypted sensitive SMS forwarding via WhatsApp Business API",
    version="2.0.0",
    docs_url=None,  # Disable Swagger UI in production
    redoc_url=None   # Disable ReDoc in production
)

# Rate limiting
limiter = Limiter(key_func=get_remote_address)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# CORS configuration (restrict to Android app only)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins.split(","),
    allow_credentials=True,
    allow_methods=["POST"],
    allow_headers=["Content-Type"],
)


@app.get("/health", response_model=HealthResponse)
async def health_check():
    """
    Health check endpoint.
    
    Returns service status and backend connectivity.
    """
    redis_healthy = storage_service.health_check()
    whatsapp_healthy = await whatsapp_service.health_check()
    
    return HealthResponse(
        status="healthy" if (redis_healthy and whatsapp_healthy) else "degraded",
        redis_connected=redis_healthy,
        whatsapp_api_configured=whatsapp_healthy
    )


@app.post("/send-verification-otp", response_model=StatusResponse)
@limiter.limit("5/minute")
async def send_verification_otp(request: VerificationRequest):
    """
    Send verification OTP to WhatsApp number.
    
    Used for signup and number changes.
    """
    # Generate 6-digit OTP
    otp = ''.join([str(random.randint(0, 9)) for _ in range(6)])
    
    # Store in temporary storage (10 min TTL)
    await storage_service.store_verification_otp(request.whatsapp_number, otp)
    
    # Send via WhatsApp (using the OTP template or a general one)
    # For now, we use a custom message if possible, or the OTP template
    # Here we simulate sending a verification message
    # In production, you'd have a specific template for 'verification_code'
    success = await whatsapp_service.send_message(
        message_content=f"Your SMS Forwarder verification code is: {otp}",
        sender="System",
        message_type=MessageType.OTP
    )
    
    if not success:
        logger.error(f"❌ Failed to send verification OTP to {request.whatsapp_number}")
        return StatusResponse(success=False, message="Failed to send OTP via WhatsApp")
    
    logger.info(f"✅ Verification OTP sent to {request.whatsapp_number}")
    return StatusResponse(success=True, message="Verification OTP sent")


@app.post("/verify-otp", response_model=StatusResponse)
@limiter.limit("10/minute")
async def verify_otp(request: OtpVerifyRequest):
    """
    Verify the OTP sent to WhatsApp.
    """
    stored_otp = await storage_service.get_verification_otp(request.whatsapp_number)
    
    if not stored_otp:
        return StatusResponse(success=False, message="OTP expired or not found")
    
    if stored_otp == request.otp:
        # Generate a temporary verification token (32 chars)
        token = secrets.token_hex(16)
        # Store token with short TTL (e.g., 5 mins) to allow registration
        await storage_service.store_verification_otp(f"token:{token}", request.whatsapp_number)
        
        # Delete OTP after successful verification
        await storage_service.delete_verification_otp(request.whatsapp_number)
        
        logger.info(f"✅ OTP verified for {request.whatsapp_number}")
        return StatusResponse(success=True, message="OTP verified", token=token)
    else:
        logger.warning(f"⚠️  Invalid OTP attempt for {request.whatsapp_number}")
        return StatusResponse(success=False, message="Invalid OTP")


@app.post("/register-user", response_model=StatusResponse)
@limiter.limit("5/minute")
async def register_user(request: UserRegistrationRequest):
    """
    Register user profile after OTP verification.
    """
    # Verify the registration token
    stored_number = await storage_service.get_verification_otp(f"token:{request.verification_token}")
    
    if not stored_number or stored_number != request.whatsapp_number:
        return StatusResponse(success=False, message="Invalid or expired registration token")
    
    # In a real app, you'd save this to a database
    # For now, we just acknowledge the registration
    # Since we want a stateless backend for now, we just return success
    logger.info(f"👤 User registered: {request.name} ({request.whatsapp_number})")
    
    # Cleanup token
    await storage_service.delete_verification_otp(f"token:{request.verification_token}")
    
    return StatusResponse(success=True, message="User registered successfully")


@app.post("/forward-message", response_model=ForwardResponse)
@limiter.limit(settings.api_rate_limit)
async def forward_message(request: Request, message_request: EncryptedMessageRequest):
    """
    Forward encrypted sensitive SMS to WhatsApp.
    
    Supports multiple message types:
    - OTP (one-time passwords)
    - TRANSACTION (debit/credit notifications)
    - BILL (payment confirmations)
    - SECURITY_ALERT (account security alerts)
    
    Security flow:
    1. Verify HMAC signature
    2. Check for duplicate (using message hash)
    3. Decrypt message using AES-256-GCM
    4. Classify message type (if not provided)
    5. Store message hash temporarily (TTL based on type)
    6. Send via WhatsApp Business API with appropriate template
    7. Clear message from memory
    
    Args:
        message_request: Encrypted message request with HMAC signature
    
    Returns:
        Success response with WhatsApp message ID
    
    Raises:
        HTTPException: If validation, decryption, or sending fails
    """
    try:
        # Step 1: Validate timestamp (prevent replay attacks)
        current_time = int(time.time())
        time_diff = abs(current_time - message_request.timestamp)
        
        # More lenient for non-OTP messages (network delays)
        max_time_diff = 300 if message_request.message_type == MessageType.OTP else 600
        
        if time_diff > max_time_diff:
            logger.warning(f"⚠️  Request rejected: timestamp too old ({time_diff}s)")
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Request timestamp is invalid"
            )
        
        # Step 2: Verify HMAC and decrypt message
        try:
            decrypted_message = crypto_service.validate_and_decrypt(
                message_request.encrypted_payload,
                message_request.hmac_signature
            )
        except ValueError as e:
            logger.warning(f"⚠️  Crypto validation failed: {e}")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Authentication failed"
            )
        
        # Step 3: Classify message type (if not provided or UNKNOWN)
        message_type = message_request.message_type
        if message_type == MessageType.UNKNOWN:
            classification = message_classifier.classify(
                decrypted_message,
                message_request.sender
            )
            message_type = classification.message_type
            logger.info(
                f"📋 Message classified as {message_type} "
                f"(confidence: {classification.confidence:.2f})"
            )
        
        # Step 4: Generate message hash for duplicate detection
        if message_request.message_hash:
            message_hash = message_request.message_hash
        else:
            message_hash = hashlib.sha256(decrypted_message.encode()).hexdigest()
        
        # Step 5: Check for duplicates and store
        is_new = await storage_service.store_message(
            message_hash,
            message_request.sender,
            message_type
        )
        
        if not is_new:
            logger.info(f"ℹ️  Duplicate {message_type} message detected, skipping")
            return ForwardResponse(
                success=True,
                message=f"{message_type} message already forwarded",
                whatsapp_message_id=None
            )
        
        # Step 6: Send via WhatsApp with appropriate template
        whatsapp_message_id = await whatsapp_service.send_message(
            decrypted_message,
            message_request.sender,
            message_type
        )
        
        if not whatsapp_message_id:
            logger.error(f"❌ Failed to send {message_type} WhatsApp message")
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="Failed to send WhatsApp message"
            )
        
        logger.info(
            f"✅ {message_type} message forwarded successfully from {message_request.sender}"
        )
        
        return ForwardResponse(
            success=True,
            message=f"{message_type} message forwarded successfully",
            whatsapp_message_id=whatsapp_message_id
        )
        
    except HTTPException:
        raise
    except Exception as e:
        # Catch-all for unexpected errors (don't leak details)
        logger.error(f"❌ Unexpected error: {type(e).__name__}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Internal server error"
        )


# Backward compatibility endpoint (redirects to new endpoint)
@app.post("/forward-otp", response_model=ForwardResponse)
@limiter.limit(settings.api_rate_limit)
async def forward_otp_legacy(request: Request, message_request: EncryptedMessageRequest):
    """
    Legacy OTP forwarding endpoint (backward compatibility).
    Redirects to /forward-message with OTP type.
    """
    # Force message type to OTP
    message_request.message_type = MessageType.OTP
    return await forward_message(request, message_request)


@app.on_event("startup")
async def startup_event():
    """Log startup information."""
    logger.info("🚀 Secure Sensitive SMS Forwarder started")
    logger.info(f"📊 Rate limit: {settings.api_rate_limit}")
    logger.info(f"⏱️  TTL - OTP: {settings.ttl_otp}s, Transaction: {settings.ttl_transaction}s")
    logger.info(f"⏱️  TTL - Bill: {settings.ttl_bill}s, Security: {settings.ttl_security}s")
    logger.info(f"🔄 Duplicate detection window: {settings.duplicate_detection_window}s")


@app.on_event("shutdown")
async def shutdown_event():
    """Cleanup on shutdown."""
    logger.info("👋 Secure Sensitive SMS Forwarder shutting down")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=False,  # Disable in production
        log_level="info"
    )
