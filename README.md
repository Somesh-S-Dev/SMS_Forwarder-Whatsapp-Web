# 🔐 Secure OTP Forwarder

Automatically forward OTPs from your Android phone to WhatsApp with end-to-end encryption.

## ⚡ Features

- **🔒 End-to-End Encryption**: AES-256-GCM encryption with HMAC authentication
- **📱 Smart Message Classification**: Automatically detects OTPs, Transactions, Bills, and Security Alerts
- **👤 Secure Onboarding**: WhatsApp-based OTP verification for new users
- **🔐 PIN Security**: 4-6 digit PIN required to access the app and manage services
- **📡 Service Management**: Enable/disable forwarding instantly from the dashboard
- **✅ Sender Allowlist**: Only forward messages from trusted senders
- **⏰ Office Hours Control**: Restrict forwarding to specific hours
- **🔓 Manual Override**: Bypass office hours when needed
- **🚫 No Sensitive Data Logging**: OTPs, amounts, and account details never stored or logged
- **🔐 HTTPS Only**: All communication encrypted in transit
- **⚡ TTL-Based Ephemeral Storage**: Messages auto-expire using configurable TTLs (5-15 mins)
- **📊 WhatsApp Business API**: Official API with multiple templates for different categories

## 🏗️ Architecture

```
┌─────────────────┐         HTTPS          ┌──────────────────┐
│  Android Phone  │────────────────────────▶│  FastAPI Backend │
│                 │  Encrypted OTP + HMAC   │                  │
│  • SMS Receiver │                         │  • Decrypt OTP   │
│  • Encrypt OTP  │                         │  • Verify HMAC   │
│  • Allowlist    │                         │  • TTL Storage   │
│  • Office Hours │                         │  • WhatsApp API  │
└─────────────────┘                         └──────────────────┘
                                                     │
                                                     │ Template Message
                                                     ▼
                                            ┌──────────────────┐
                                            │  WhatsApp Cloud  │
                                            │       API        │
                                            └──────────────────┘
                                                     │
                                                     ▼
                                            ┌──────────────────┐
                                            │  Your Personal   │
                                            │    WhatsApp      │
                                            └──────────────────┘
```

## 🔒 Security Features

### Encryption

- **AES-256-GCM**: Authenticated encryption with 128-bit auth tag
- **Random IV**: Unique 12-byte IV per message
- **HMAC-SHA256**: Message authentication to prevent tampering
- **Constant-time comparison**: Prevents timing attacks

### Network Security

- **HTTPS Only**: No cleartext traffic allowed
- **Certificate Validation**: System CA trust anchors
- **Optional Certificate Pinning**: Additional security layer

### Data Protection

- **No OTP Logging**: OTPs never appear in logs
- **TTL Storage**: Max 5 minutes, in-memory only
- **Encrypted SharedPreferences**: Keys encrypted at rest on Android
- **No Database Persistence**: OTPs never written to disk

### Access Control

- **👤 User Verification**: WhatsApp OTP verification during signup
- **🔐 App PIN**: Mandatory PIN entry for app access
- **📡 Service Toggle**: Master switch to stop all processing
- **✅ Sender Allowlist**: Whitelist trusted SMS senders
- **⏰ Office Hours**: Time-based access control
- **Rate Limiting**: 10 requests/minute (configurable)
- **Timestamp Validation**: Prevents replay attacks (5-minute window)

## 📋 Prerequisites

### Backend

- Python 3.9+
- Redis (optional, falls back to in-memory)
- HTTPS endpoint (Cloudflare Tunnel recommended)

### Android

- Android 8.0+ (API 26+)
- SMS receive permission
- Internet permission

### WhatsApp Business API

- Meta Business Account
- WhatsApp Business App (Meta Developer Portal)
- Verified phone number
- Approved message template

## 🚀 Quick Start

### 1. Backend Setup

```bash
cd backend

# Install dependencies
pip install -r requirements.txt

# Generate encryption keys
python -c "import secrets; print('AES_KEY:', secrets.token_hex(32))"
python -c "import secrets; print('HMAC_KEY:', secrets.token_hex(32))"

# Configure environment variables
cp .env.example .env
# Edit .env with your keys and WhatsApp credentials

# Run backend
python main.py
```

### 2. WhatsApp Business API Setup

See [`docs/WHATSAPP_SETUP.md`](docs/WHATSAPP_SETUP.md) for detailed instructions.

**Required template format:**

```
Your OTP from {{1}} is {{2}}. Valid for 5 minutes.
```

### 3. Cloudflare Tunnel Setup

See [`docs/CLOUDFLARE_TUNNEL.md`](docs/CLOUDFLARE_TUNNEL.md) for HTTPS setup.

### 4. Android App Setup

```bash
cd android

# Build APK
./gradlew assembleRelease

# Install on device
adb install app/build/outputs/apk/release/app-release.apk
```

**Configure in app:**

1. Enter backend URL (HTTPS)
2. Complete signup with Name and WhatsApp number
3. Verify WhatsApp number via OTP
4. Set up a secure PIN
5. Configure message filters (OTP, Transactions, etc.)
6. Grant SMS permission

## 📖 Documentation

- **[Security Architecture](SECURITY.md)** - Detailed security design
- **[WhatsApp Setup Guide](docs/WHATSAPP_SETUP.md)** - WhatsApp Business API configuration
- **[Cloudflare Tunnel Guide](docs/CLOUDFLARE_TUNNEL.md)** - HTTPS setup
- **[Deployment Guide](docs/DEPLOYMENT.md)** - Production deployment

## 🔧 Configuration

### Backend Environment Variables

| Variable                      | Description                                  | Required |
| ----------------------------- | -------------------------------------------- | -------- |
| `AES_ENCRYPTION_KEY`        | 64-char hex key for AES-256                  | ✅       |
| `HMAC_SECRET_KEY`           | 64-char hex key for HMAC                     | ✅       |
| `WHATSAPP_API_TOKEN`        | WhatsApp Business API token                  | ✅       |
| `WHATSAPP_PHONE_NUMBER_ID`  | Phone number ID from Meta                    | ✅       |
| `WHATSAPP_RECIPIENT_NUMBER` | Your personal WhatsApp number                | ✅       |
| `WHATSAPP_TEMPLATE_OTP`         | Template for OTP messages                    | ✅       |
| `WHATSAPP_TEMPLATE_TRANSACTION` | Template for Bank Transactions               | ✅       |
| `WHATSAPP_TEMPLATE_BILL`        | Template for Bill confirmations              | ✅       |
| `WHATSAPP_TEMPLATE_SECURITY`    | Template for Security Alerts                 | ✅       |
| `REDIS_URL`                     | Redis connection URL                         | ❌       |
| `API_RATE_LIMIT`                | Rate limit (default:`10/minute`)             | ❌       |
| `TTL_OTP`                       | OTP expiration (default:`300`)               | ❌       |
| `TTL_TRANSACTION`               | Transaction expiration (default:`600`)       | ❌       |
| `TTL_BILL`                      | Bill expiration (default:`900`)              | ❌       |
| `TTL_SECURITY`                  | Security alert expiration (default:`600`)    | ❌       |

### Android Configuration

Configure via app UI:

- **Backend URL**: Your HTTPS endpoint
- **Encryption Keys**: Must match backend
- **Allowed Senders**: Comma-separated list (e.g., `BANK-ALERT,AMAZON,GOOGLE`)
- **Office Hours**: Start/end hour (0-23)
- **Manual Override**: Bypass office hours

## 🧪 Testing

### Backend Tests

**⚠️ Note**: Tests use **dummy values** and mocked services. No real WhatsApp API credentials required.

```bash
cd backend

# Use test environment (dummy values)
cp .env.test .env

# Install dependencies (includes pytest)
pip install -r requirements.txt

# Run all tests
pytest tests/

# Run with verbose output
pytest tests/ -v
```

**What's tested**:
- ✅ Encryption/decryption logic
- ✅ HMAC signature verification
- ✅ API endpoint validation
- ✅ Timestamp validation
- ✅ Error handling

**What's NOT tested** (requires real setup):
- ❌ Actual WhatsApp message delivery
- ❌ Real SMS reception
- ❌ End-to-end integration

See [`backend/tests/README.md`](backend/tests/README.md) for detailed testing documentation.

### Android Tests

```bash
cd android
./gradlew test
./gradlew connectedAndroidTest
```

### Manual Testing

1. Send test SMS with OTP to your phone
2. Check Android logs: `adb logcat | grep OtpForwarder`
3. Verify WhatsApp message received
4. Confirm no OTP in logs: `adb logcat | grep -i "otp\|[0-9]{6}"`

## 🛡️ Security Considerations

### ⚠️ Important Warnings

1. **Encryption Keys**: Store securely, never commit to git
2. **Office Hours**: Enforced on Android only - consider backend validation
3. **Certificate Pinning**: Recommended for production
4. **Key Rotation**: Implement periodic key rotation
5. **Sender Allowlist**: Always configure to prevent spam

### 🚨 What This System Does NOT Do

- ❌ Automate your personal WhatsApp
- ❌ Store OTPs permanently
- ❌ Log OTP values anywhere
- ❌ Allow HTTP traffic
- ❌ Send OTPs in plaintext

## 📱 Android Permissions

- **RECEIVE_SMS**: Required to intercept OTP messages
- **INTERNET**: Required to send encrypted OTPs to backend

**Note**: App does NOT request `READ_SMS` permission for better privacy.

## 🤝 Contributing

This is a security-critical application. All contributions must:

1. Pass security review
2. Include tests
3. Follow security best practices
4. Not introduce OTP logging
5. Maintain encryption standards

## 📄 License

MIT License - See LICENSE file for details

## ⚠️ Disclaimer

This software is provided as-is. Users are responsible for:

- Securing encryption keys
- Configuring WhatsApp Business API properly
- Complying with local regulations
- Maintaining HTTPS certificates
- Monitoring for security vulnerabilities

**Use at your own risk. The authors are not responsible for any data breaches or security incidents.**

## 🆘 Support

For issues or questions:

1. Check [SECURITY.md](SECURITY.md) for security concerns
2. Review [docs/](docs/) for setup guides
3. Open an issue on GitHub

---

**Made with 🔒 by security-conscious developers**
