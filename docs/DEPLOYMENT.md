# Deployment Guide

This guide covers deploying the OTP Forwarder system to production.

## Overview

Deployment involves:
1. Backend deployment with HTTPS
2. Android APK building and signing
3. Configuration and testing
4. Monitoring setup

## Backend Deployment

### Option 1: VPS (Recommended)

**Requirements**:
- Ubuntu 20.04+ or similar
- 1GB RAM minimum
- Python 3.9+
- Redis (optional)

#### Step 1: Prepare Server

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Python and dependencies
sudo apt install python3 python3-pip python3-venv redis-server -y

# Create app user
sudo useradd -m -s /bin/bash otpforwarder
sudo su - otpforwarder
```

#### Step 2: Deploy Code

```bash
# Clone or upload code
git clone https://github.com/yourusername/otp-forwarder.git
cd otp-forwarder/backend

# Create virtual environment
python3 -m venv venv
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt
```

#### Step 3: Configure Environment

```bash
# Create .env file
nano .env
```

Add your configuration:
```bash
AES_ENCRYPTION_KEY=your_64_char_hex_key
HMAC_SECRET_KEY=your_64_char_hex_key
WHATSAPP_API_TOKEN=your_whatsapp_token
WHATSAPP_PHONE_NUMBER_ID=your_phone_number_id
WHATSAPP_RECIPIENT_NUMBER=your_whatsapp_number
WHATSAPP_TEMPLATE_NAME=otp_notification
REDIS_URL=redis://localhost:6379/0
API_RATE_LIMIT=10/minute
OTP_TTL_SECONDS=300
```

```bash
# Secure the file
chmod 600 .env
```

#### Step 4: Create Systemd Service

```bash
# Exit otpforwarder user
exit

# Create service file
sudo nano /etc/systemd/system/otpforwarder.service
```

Add:
```ini
[Unit]
Description=OTP Forwarder Backend
After=network.target redis.service

[Service]
Type=simple
User=otpforwarder
WorkingDirectory=/home/otpforwarder/otp-forwarder/backend
Environment="PATH=/home/otpforwarder/otp-forwarder/backend/venv/bin"
ExecStart=/home/otpforwarder/otp-forwarder/backend/venv/bin/python main.py
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
# Start service
sudo systemctl daemon-reload
sudo systemctl start otpforwarder
sudo systemctl enable otpforwarder

# Check status
sudo systemctl status otpforwarder
```

#### Step 5: Setup Cloudflare Tunnel

Follow [CLOUDFLARE_TUNNEL.md](CLOUDFLARE_TUNNEL.md) to expose backend via HTTPS.

### Option 2: Docker (Alternative)

Create `Dockerfile` in backend directory:

```dockerfile
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000

CMD ["python", "main.py"]
```

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  backend:
    build: ./backend
    ports:
      - "8000:8000"
    env_file:
      - ./backend/.env
    depends_on:
      - redis
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    restart: unless-stopped
```

Deploy:
```bash
docker-compose up -d
```

## Android APK Building

### Step 1: Generate Signing Key

```bash
cd android

# Generate keystore
keytool -genkey -v -keystore otp-forwarder.keystore \
  -alias otp-forwarder -keyalg RSA -keysize 2048 -validity 10000
```

**⚠️ Important**: 
- Save keystore password securely
- Backup keystore file (losing it means you can't update the app)

### Step 2: Configure Signing

Create `android/keystore.properties`:

```properties
storePassword=your_keystore_password
keyPassword=your_key_password
keyAlias=otp-forwarder
storeFile=../otp-forwarder.keystore
```

Update `android/app/build.gradle.kts`:

```kotlin
// Add before android block
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    // ... existing config ...
    
    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### Step 3: Build Release APK

```bash
cd android

# Build release APK
./gradlew assembleRelease

# APK location:
# android/app/build/outputs/apk/release/app-release.apk
```

### Step 4: Test APK

```bash
# Install on test device
adb install app/build/outputs/apk/release/app-release.apk

# Check logs
adb logcat | grep OtpForwarder
```

## Configuration

### Generate Encryption Keys

```bash
# On your development machine
python -c "import secrets; print('AES:', secrets.token_hex(32))"
python -c "import secrets; print('HMAC:', secrets.token_hex(32))"
```

**⚠️ Critical**: 
- Use the SAME keys in backend and Android app
- Store keys securely (password manager)
- Never commit to git

### Configure Android App

1. Install APK on phone
2. Open app
3. Enter configuration:
   - **Backend URL**: `https://otp-forwarder.yourdomain.com`
   - **AES Key**: From above
   - **HMAC Key**: From above
   - **Allowed Senders**: `BANK,AMAZON,GOOGLE` (example)
   - **Office Hours**: `9` to `18` (9 AM to 6 PM)
4. Grant SMS permission
5. Save configuration

## Testing

### End-to-End Test

1. **Send Test SMS**:
   ```bash
   # Using Android Debug Bridge
   adb shell am broadcast -a android.provider.Telephony.SMS_RECEIVED \
     --es pdus "..." # (complex, use real SMS instead)
   ```

2. **Or send real SMS** from another phone:
   ```
   Your OTP is 123456 for login.
   ```

3. **Check Android logs**:
   ```bash
   adb logcat | grep -i "otp\|sms"
   ```

4. **Check backend logs**:
   ```bash
   sudo journalctl -u otpforwarder -f
   ```

5. **Verify WhatsApp message** received on personal WhatsApp

### Security Verification

```bash
# 1. Verify no OTP in Android logs
adb logcat | grep -E "[0-9]{4,8}"  # Should not show OTPs

# 2. Verify HTTPS enforcement
curl http://otp-forwarder.yourdomain.com/health
# Should fail or redirect to HTTPS

# 3. Verify rate limiting
for i in {1..15}; do curl https://otp-forwarder.yourdomain.com/health; done
# Should get 429 after 10 requests

# 4. Verify timestamp validation
# Send request with old timestamp (should fail)
```

## Monitoring

### Backend Monitoring

#### Logs

```bash
# View logs
sudo journalctl -u otpforwarder -f

# Search for errors
sudo journalctl -u otpforwarder | grep ERROR
```

#### Health Check

```bash
# Add to cron for monitoring
*/5 * * * * curl -f https://otp-forwarder.yourdomain.com/health || echo "Backend down" | mail -s "OTP Forwarder Alert" your@email.com
```

#### Metrics (Optional)

Add Prometheus metrics to backend:

```python
# In main.py
from prometheus_client import Counter, Histogram, generate_latest

otp_forwarded = Counter('otp_forwarded_total', 'Total OTPs forwarded')
otp_errors = Counter('otp_errors_total', 'Total OTP errors')

@app.get("/metrics")
async def metrics():
    return Response(generate_latest(), media_type="text/plain")
```

### Android Monitoring

- Check "Last forwarded" timestamp in app
- Monitor battery usage (should be minimal)
- Check for crash reports

## Backup and Recovery

### Backup

```bash
# Backup encryption keys
cp backend/.env backend/.env.backup

# Backup keystore
cp android/otp-forwarder.keystore ~/secure-backup/

# Backup tunnel credentials
cp ~/.cloudflared/*.json ~/secure-backup/
```

### Recovery

```bash
# Restore backend
cp backend/.env.backup backend/.env
sudo systemctl restart otpforwarder

# Restore Android signing
cp ~/secure-backup/otp-forwarder.keystore android/
```

## Updating

### Backend Update

```bash
# Pull latest code
cd /home/otpforwarder/otp-forwarder
git pull

# Restart service
sudo systemctl restart otpforwarder
```

### Android Update

```bash
# Increment versionCode in app/build.gradle.kts
versionCode = 2
versionName = "1.1.0"

# Build new APK
./gradlew assembleRelease

# Install update
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Production Checklist

### Backend
- [ ] Deployed on VPS or cloud
- [ ] HTTPS configured (Cloudflare Tunnel)
- [ ] Environment variables set
- [ ] Redis running
- [ ] Systemd service enabled
- [ ] Health check passing
- [ ] Logs monitored
- [ ] Backups configured

### Android
- [ ] Release APK built and signed
- [ ] Installed on production phone
- [ ] Configuration entered
- [ ] SMS permission granted
- [ ] Allowed senders configured
- [ ] Office hours set
- [ ] Test OTP forwarded successfully

### Security
- [ ] Encryption keys generated and secured
- [ ] No keys in git
- [ ] HTTPS enforced
- [ ] Rate limiting active
- [ ] No OTP logging verified
- [ ] WhatsApp template approved
- [ ] Sender allowlist configured

### Monitoring
- [ ] Backend health check automated
- [ ] Logs accessible
- [ ] Alert system configured
- [ ] Backup strategy in place

## Troubleshooting

### Backend won't start

```bash
# Check logs
sudo journalctl -u otpforwarder -n 50

# Common issues:
# - Missing .env file
# - Invalid environment variables
# - Port already in use
# - Redis not running
```

### Android app crashes

```bash
# Check crash logs
adb logcat | grep -i "crash\|exception"

# Common issues:
# - Missing permissions
# - Invalid encryption keys
# - Network connectivity
```

### OTPs not forwarding

1. Check Android logs for errors
2. Verify backend is reachable (curl health endpoint)
3. Check sender is in allowlist
4. Verify within office hours (or override enabled)
5. Check WhatsApp template is approved

## Support

For production issues:
1. Check logs (Android + backend)
2. Verify configuration
3. Test each component individually
4. Review [SECURITY.md](../SECURITY.md) for security issues

---

**Deployment Time**: ~2 hours (including WhatsApp approval wait)
