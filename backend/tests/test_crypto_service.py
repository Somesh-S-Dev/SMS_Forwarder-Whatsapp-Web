"""
Unit tests for crypto_service.py

Tests encryption, decryption, and HMAC verification.
"""
import pytest
from crypto_service import CryptoService


class TestCryptoService:
    """Test cryptographic operations."""
    
    @pytest.fixture
    def crypto_service(self):
        """Create crypto service with test keys."""
        # Use test keys (64 hex chars = 32 bytes)
        aes_key = "0" * 64
        hmac_key = "1" * 64
        
        # Mock settings
        class MockSettings:
            aes_encryption_key = aes_key
            hmac_secret_key = hmac_key
        
        # Temporarily replace settings
        import config
        original_settings = config.settings
        config.settings = MockSettings()
        
        service = CryptoService()
        
        # Restore original settings
        config.settings = original_settings
        
        return service
    
    def test_encrypt_decrypt_otp(self, crypto_service):
        """Test that encryption and decryption work correctly."""
        otp = "123456"
        
        # Encrypt
        encrypted = crypto_service.cipher.encrypt(
            b"\x00" * 12,  # IV
            otp.encode('utf-8'),
            None
        )
        
        # Decrypt
        decrypted = crypto_service.cipher.decrypt(
            b"\x00" * 12,
            encrypted,
            None
        )
        
        assert decrypted.decode('utf-8') == otp
    
    def test_hmac_verification_valid(self, crypto_service):
        """Test HMAC verification with valid signature."""
        payload = "test_payload"
        
        # Generate signature
        import hmac
        import hashlib
        signature = hmac.new(
            bytes.fromhex("1" * 64),
            payload.encode('utf-8'),
            hashlib.sha256
        ).hexdigest()
        
        # Verify
        assert crypto_service.verify_hmac(payload, signature) is True
    
    def test_hmac_verification_invalid(self, crypto_service):
        """Test HMAC verification with invalid signature."""
        payload = "test_payload"
        invalid_signature = "0" * 64
        
        assert crypto_service.verify_hmac(payload, invalid_signature) is False
    
    def test_decrypt_otp_invalid_data(self, crypto_service):
        """Test decryption with invalid data."""
        with pytest.raises(ValueError):
            crypto_service.decrypt_otp("invalid_base64_data")
