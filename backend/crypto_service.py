"""
Cryptographic operations for OTP decryption and HMAC verification.

Security considerations:
- Uses AES-256-GCM for authenticated encryption
- Constant-time comparison for HMAC to prevent timing attacks
- Keys are loaded from environment variables only
- No OTP values are logged or stored
"""
import hmac
import hashlib
import base64
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.exceptions import InvalidTag
from config import settings


class CryptoService:
    """Handles decryption and signature verification."""
    
    def __init__(self):
        # Load encryption key from environment (32 bytes for AES-256)
        self.aes_key = bytes.fromhex(settings.aes_encryption_key)
        self.hmac_key = bytes.fromhex(settings.hmac_secret_key)
        
        # Initialize AES-GCM cipher
        self.cipher = AESGCM(self.aes_key)
    
    def verify_hmac(self, payload: str, signature: str) -> bool:
        """
        Verify HMAC-SHA256 signature using constant-time comparison.
        
        Args:
            payload: The data that was signed (base64 encoded)
            signature: The HMAC signature (hex encoded)
        
        Returns:
            True if signature is valid, False otherwise
        
        Security: Uses hmac.compare_digest to prevent timing attacks
        """
        try:
            expected_signature = hmac.new(
                self.hmac_key,
                payload.encode('utf-8'),
                hashlib.sha256
            ).hexdigest()
            
            # Constant-time comparison
            return hmac.compare_digest(expected_signature, signature)
        except Exception:
            # Never log the exception details (could leak signature info)
            return False
    
    def decrypt_otp(self, encrypted_payload: str) -> str:
        """
        Decrypt AES-256-GCM encrypted OTP.
        
        Args:
            encrypted_payload: Base64 encoded (IV + ciphertext + auth_tag)
        
        Returns:
            Decrypted OTP as string
        
        Raises:
            ValueError: If decryption fails (wrong key, tampered data, etc.)
        
        Security notes:
        - IV is prepended to ciphertext (first 12 bytes)
        - Auth tag is appended (last 16 bytes)
        - GCM mode provides both confidentiality and authenticity
        """
        try:
            # Decode base64 payload
            encrypted_data = base64.b64decode(encrypted_payload)
            
            # Extract IV (first 12 bytes for GCM)
            iv = encrypted_data[:12]
            
            # Extract ciphertext + auth_tag (remaining bytes)
            ciphertext_with_tag = encrypted_data[12:]
            
            # Decrypt and verify auth tag
            plaintext = self.cipher.decrypt(iv, ciphertext_with_tag, None)
            
            # Return as UTF-8 string
            return plaintext.decode('utf-8')
            
        except InvalidTag:
            # Auth tag verification failed - data was tampered with
            raise ValueError("Decryption failed: Invalid authentication tag")
        except Exception as e:
            # Other decryption errors (wrong key, corrupted data, etc.)
            # Don't log the actual error to avoid leaking crypto details
            raise ValueError("Decryption failed")
    
    def validate_and_decrypt(self, encrypted_payload: str, hmac_signature: str) -> str:
        """
        Validate HMAC signature and decrypt OTP in one operation.
        
        Args:
            encrypted_payload: Base64 encoded encrypted data
            hmac_signature: Hex encoded HMAC signature
        
        Returns:
            Decrypted OTP string
        
        Raises:
            ValueError: If HMAC verification or decryption fails
        """
        # Step 1: Verify HMAC signature
        if not self.verify_hmac(encrypted_payload, hmac_signature):
            raise ValueError("HMAC verification failed")
        
        # Step 2: Decrypt OTP
        return self.decrypt_otp(encrypted_payload)


# Global crypto service instance
crypto_service = CryptoService()
