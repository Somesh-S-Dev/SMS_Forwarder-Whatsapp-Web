"""
TTL-based storage for sensitive messages with automatic expiration.

Supports Redis (preferred) or in-memory dictionary fallback.
No disk persistence - all data is ephemeral.
Configurable TTL per message type (OTP, transaction, bill, security alert).
Duplicate detection using message hashes.
"""
from config import settings
from message_classifier import MessageType
import logging
import time
from typing import Optional
import redis

logger = logging.getLogger(__name__)


class StorageService:
    """
    Manages temporary message storage with TTL.
    
    Uses Redis if available, falls back to in-memory dict.
    Supports different TTL values per message type.
    """
    
    def __init__(self):
        """Initialize storage backend (Redis or in-memory)."""
        self.redis = None
        self.memory_store = {}
        
        # Try to connect to Redis
        if settings.redis_url:
            try:
                self.redis = redis.from_url(
                    settings.redis_url,
                    decode_responses=True,
                    socket_connect_timeout=2
                )
                # Test connection
                self.redis.ping()
                logger.info("✅ Connected to Redis for message storage")
            except Exception as e:
                logger.warning(f"⚠️  Redis connection failed, using in-memory storage: {e}")
                self.redis = None
        else:
            logger.info("📝 Using in-memory storage (Redis not configured)")
    
    async def store_message(self, message_hash: str, sender: str, message_type: MessageType) -> bool:
        """
        Store message hash temporarily to prevent duplicate forwarding.
        
        Args:
            message_hash: SHA-256 hash of the message content
            sender: SMS sender (for logging context)
            message_type: Type of message (OTP, TRANSACTION, etc.)
        
        Returns:
            True if stored successfully, False if already exists (duplicate)
        
        Security: Only hash is stored, not the message content itself
        """
        # Check if already exists (duplicate)
        if await self.exists(message_hash):
            logger.info(f"Duplicate {message_type} message detected from {sender}, skipping")
            return False
        
        # Get TTL based on message type
        ttl = self._get_ttl_for_type(message_type)
        
        try:
            if self.redis:
                # Store in Redis with automatic expiration
                await self.redis.setex(message_hash, ttl, sender)
                logger.info(f"✅ {message_type} message stored with {ttl}s TTL from {sender}")
            else:
                # Store in memory with expiration timestamp
                self._cleanup_expired()
                self.memory_store[message_hash] = {
                    "sender": sender,
                    "message_type": message_type.value,
                    "expires_at": time.time() + ttl
                }
                logger.info(f"✅ {message_type} message stored in memory with {ttl}s TTL from {sender}")
            
            return True
            
        except Exception as e:
            logger.error(f"❌ Failed to store message: {e}")
            return False
    
    async def exists(self, key: str) -> bool:
        """
        Check if key exists in storage.
        
        Args:
            key: The key to check (message hash)
        
        Returns:
            True if exists and not expired, False otherwise
        """
        if self.redis:
            try:
                return await self.redis.exists(key) > 0
            except Exception as e:
                logger.error(f"❌ Redis exists check failed: {e}")
                return False
        else:
            # Clean up expired entries first
            self._cleanup_expired()
            return key in self.memory_store
    
    def _get_ttl_for_type(self, message_type: MessageType) -> int:
        """
        Get TTL duration for message type.
        
        Args:
            message_type: The message type
        
        Returns:
            TTL in seconds
        """
        ttl_map = {
            MessageType.OTP: settings.ttl_otp,
            MessageType.TRANSACTION: settings.ttl_transaction,
            MessageType.BILL: settings.ttl_bill,
            MessageType.SECURITY_ALERT: settings.ttl_security,
            MessageType.UNKNOWN: settings.ttl_otp  # Default to OTP TTL for unknown
        }
        return ttl_map.get(message_type, settings.ttl_otp)
    
    def _cleanup_expired(self) -> None:
        """
        Remove expired entries from in-memory storage.
        Only called in non-Redis mode.
        """
        current_time = time.time()
        expired_keys = [
            key for key, data in self.memory_store.items()
            if current_time >= data["expires_at"]
        ]
        for key in expired_keys:
            del self.memory_store[key]
            logger.debug(f"🗑️  Removed expired message hash: {key[:16]}...")
    
    def health_check(self) -> bool:
        """Check if storage backend is healthy."""
        if self.redis:
            try:
                self.redis.ping()
                return True
            except Exception:
                return False
        return True  # In-memory is always "healthy"


# Global storage service instance
storage_service = StorageService()
