# Running Tests

This document explains how to run the backend tests.

## Setup

1. **Install dependencies** (including test dependencies):
   ```bash
   cd backend
   pip install -r requirements.txt
   ```

2. **Use test environment**:
   ```bash
   # Copy test environment file
   cp .env.test .env
   ```
   
   **⚠️ Important**: The `.env.test` file contains **dummy values** for testing only. These are NOT real credentials and will NOT work with actual WhatsApp API or production systems.

## Running Tests

```bash
# Run all tests
pytest tests/

# Run with verbose output
pytest tests/ -v

# Run specific test file
pytest tests/test_crypto_service.py
pytest tests/test_whatsapp_service.py
pytest tests/test_api.py

# Run with coverage report
pytest tests/ --cov=. --cov-report=html
```

## Test Files

- **`test_crypto_service.py`**: Tests encryption, decryption, and HMAC verification
- **`test_whatsapp_service.py`**: Tests WhatsApp API integration (uses mocking)
- **`test_api.py`**: Tests FastAPI endpoints (health check, validation, etc.)

## Important Notes

### Dummy Test Values

The tests use **dummy/mock values** and do NOT require:
- ❌ Real WhatsApp Business API credentials
- ❌ Real encryption keys
- ❌ Redis server
- ❌ Internet connection (for most tests)

### What Tests Cover

✅ Encryption/decryption logic  
✅ HMAC signature verification  
✅ API endpoint validation  
✅ Timestamp validation  
✅ Error handling  

### What Tests DON'T Cover

❌ Actual WhatsApp message delivery (requires real API)  
❌ Real SMS reception (Android-only)  
❌ End-to-end integration (requires full setup)  

## For Production Testing

To test with **real credentials**:

1. Create a separate `.env` file with real values
2. Run specific integration tests manually
3. **Never commit** real credentials to git

## Continuous Integration

For CI/CD pipelines, the tests will automatically use mocked values and don't require external services.

## Troubleshooting

### "Config validation failed"
- Make sure `.env.test` exists or create `.env` with dummy values

### "Module not found"
- Run `pip install -r requirements.txt` to install dependencies

### Tests fail with "Connection refused"
- Tests should NOT require external connections
- Check that mocking is working correctly in test files
