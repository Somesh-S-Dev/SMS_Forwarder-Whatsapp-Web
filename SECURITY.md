# 🔒 Security Architecture

This document details the security design of the OTP Forwarder system.

## Threat Model

### Assets to Protect
1. **OTP Values**: One-time passwords from SMS
2. **Encryption Keys**: AES and HMAC keys
3. **WhatsApp Credentials**: API tokens and phone numbers
4. **User Privacy**: SMS content and sender information

### Threat Actors
1. **Network Attackers**: Intercepting traffic between Android and backend
2. **Malicious Apps**: Reading OTPs from logs or memory
3. **Backend Compromise**: Accessing stored OTPs or keys
4. **Replay Attacks**: Reusing captured encrypted payloads

### Out of Scope
- Physical device compromise (rooted/jailbroken phones)
- Compromised WhatsApp Business account
- Social engineering attacks
- Supply chain attacks on dependencies

## Security Controls

### 1. Encryption (AES-256-GCM)

**Purpose**: Protect OTP confidentiality and integrity in transit

**Implementation**:
- **Algorithm**: AES-256-GCM (Galois/Counter Mode)
- **Key Size**: 256 bits (32 bytes)
- **IV Size**: 96 bits (12 bytes), randomly generated per message
- **Auth Tag**: 128 bits (16 bytes)

**Security Properties**:
- **Confidentiality**: OTP encrypted, unreadable without key
- **Authenticity**: GCM auth tag prevents tampering
- **Uniqueness**: Random IV ensures same OTP encrypts differently each time

**Payload Structure**:
```
[12 bytes IV][variable ciphertext][16 bytes auth tag]
└─────────────────────────────────────────────────┘
              Base64 encoded
```

**Why GCM?**
- Authenticated encryption (AEAD)
- Faster than CBC+HMAC
- Detects tampering automatically
- Industry standard (TLS 1.3, etc.)

### 2. Message Authentication (HMAC-SHA256)

**Purpose**: Additional layer of authentication, prevent replay attacks

**Implementation**:
- **Algorithm**: HMAC-SHA256
- **Key Size**: 256 bits (32 bytes), separate from AES key
- **Input**: Base64-encoded encrypted payload
- **Output**: 64-character hex string

**Security Properties**:
- **Authentication**: Verifies message from legitimate sender
- **Integrity**: Detects any modification
- **Timing-Safe**: Constant-time comparison prevents timing attacks

**Why separate HMAC?**
- Defense in depth (two independent keys)
- Allows key rotation independently
- Additional validation layer

### 3. Timestamp Validation

**Purpose**: Prevent replay attacks

**Implementation**:
- Android includes Unix timestamp in request
- Backend validates timestamp within ±5 minutes
- Requests outside window are rejected

**Security Properties**:
- **Freshness**: Ensures request is recent
- **Replay Prevention**: Old captured requests won't work
- **Clock Skew Tolerance**: 5-minute window handles minor time differences

### 4. TTL Storage

**Purpose**: Minimize OTP exposure window

**Implementation**:
- OTPs stored in Redis or in-memory dict
- Automatic expiration after 5 minutes
- No disk persistence
- Deduplication prevents double-forwarding

**Security Properties**:
- **Minimal Exposure**: OTPs exist only briefly
- **No Persistence**: Never written to disk
- **Automatic Cleanup**: No manual deletion needed

### 5. HTTPS Enforcement

**Purpose**: Protect against network eavesdropping

**Implementation**:
- Android: `cleartextTrafficPermitted="false"` in network config
- Backend: Deployed behind HTTPS (Cloudflare Tunnel)
- Optional: Certificate pinning for additional security

**Security Properties**:
- **Confidentiality**: Encrypted transport layer
- **Integrity**: TLS prevents tampering
- **Authentication**: Server certificate validation

### 6. Sender Allowlist

**Purpose**: Prevent spam and unauthorized OTP forwarding

**Implementation**:
- User configures allowed SMS senders
- Android checks sender before forwarding
- Case-insensitive matching
- Empty list = allow all (for initial setup)

**Security Properties**:
- **Authorization**: Only trusted senders
- **Spam Prevention**: Blocks unwanted forwards
- **User Control**: User decides who to trust

### 7. Office Hours Enforcement

**Purpose**: Limit attack window, prevent after-hours abuse

**Implementation**:
- Configurable start/end hours (0-23)
- Checked on Android before forwarding
- Manual override available for emergencies

**Security Properties**:
- **Temporal Access Control**: Restricts when OTPs can be forwarded
- **Reduced Attack Surface**: Limits exposure to business hours
- **User Override**: Flexibility for legitimate use

**⚠️ Limitation**: Enforced on Android only. If attacker has backend URL and keys, they can bypass. Consider adding backend-side time validation for defense-in-depth.

### 8. Rate Limiting

**Purpose**: Prevent abuse and DoS attacks

**Implementation**:
- 10 requests per minute per IP
- Enforced by SlowAPI middleware
- Returns 429 Too Many Requests

**Security Properties**:
- **DoS Prevention**: Limits request flood
- **Abuse Prevention**: Prevents automated attacks
- **Resource Protection**: Protects backend resources

### 9. No Logging of Sensitive Data

**Purpose**: Prevent OTP leakage through logs

**Implementation**:
- **Android**: No OTP values in Log statements
- **Backend**: OTPs never logged, only metadata
- **Errors**: Generic error messages, no sensitive details

**Security Properties**:
- **Privacy**: OTPs don't appear in log files
- **Forensics-Safe**: Logs can be shared without exposing OTPs
- **Compliance**: Meets data minimization requirements

### 10. Encrypted Storage (Android)

**Purpose**: Protect keys at rest on Android device

**Implementation**:
- **Library**: AndroidX Security Crypto
- **Encryption**: AES-256-GCM for SharedPreferences
- **Key Storage**: Android Keystore (hardware-backed if available)

**Security Properties**:
- **At-Rest Protection**: Keys encrypted on disk
- **Hardware Backing**: Uses TEE if available
- **OS-Level Security**: Leverages Android security features

## Encryption Flow

### Android → Backend

```
1. SMS Received
   ↓
2. Extract OTP (regex)
   ↓
3. Check Sender Allowlist
   ↓
4. Check Office Hours
   ↓
5. Generate Random IV (12 bytes)
   ↓
6. Encrypt OTP with AES-256-GCM
   ↓
7. Combine: IV + Ciphertext + Auth Tag
   ↓
8. Base64 Encode
   ↓
9. Generate HMAC-SHA256 of Base64 payload
   ↓
10. Send HTTPS POST:
    {
      "encrypted_payload": "base64...",
      "hmac_signature": "hex...",
      "sender": "BANK-ALERT",
      "timestamp": 1707287400
    }
```

### Backend Processing

```
1. Receive HTTPS POST
   ↓
2. Validate Timestamp (±5 min)
   ↓
3. Verify HMAC Signature (constant-time)
   ↓
4. Base64 Decode Payload
   ↓
5. Extract IV (first 12 bytes)
   ↓
6. Extract Ciphertext + Auth Tag (remaining)
   ↓
7. Decrypt with AES-256-GCM
   ↓
8. Verify Auth Tag (automatic in GCM)
   ↓
9. Validate OTP Format (4-8 digits)
   ↓
10. Store in Redis/Memory (TTL: 5 min)
    ↓
11. Send via WhatsApp Template
    ↓
12. Return Success Response
```

## Key Management

### Key Generation

```bash
# Generate AES-256 key (64 hex chars = 32 bytes)
python -c "import secrets; print(secrets.token_hex(32))"

# Generate HMAC key (64 hex chars = 32 bytes)
python -c "import secrets; print(secrets.token_hex(32))"
```

### Key Storage

**Backend**:
- Environment variables (`.env` file)
- Never committed to git
- Restricted file permissions (chmod 600)

**Android**:
- Encrypted SharedPreferences
- Android Keystore for master key
- Never in source code

### Key Distribution

**⚠️ Critical**: Keys must be securely shared between Android and backend.

**Recommended Methods**:
1. **In-Person**: QR code or manual entry
2. **Secure Channel**: Signal, encrypted email
3. **Password Manager**: 1Password, Bitwarden shared vault

**Never**:
- ❌ Plain SMS
- ❌ Unencrypted email
- ❌ Public chat
- ❌ Committed to git

### Key Rotation

**When to Rotate**:
- Every 90 days (recommended)
- After suspected compromise
- When team member leaves
- After security incident

**How to Rotate**:
1. Generate new keys
2. Update backend `.env`
3. Restart backend
4. Update Android app configuration
5. Test with sample OTP
6. Securely delete old keys

## Attack Scenarios & Mitigations

### 1. Network Eavesdropping

**Attack**: Attacker intercepts traffic between Android and backend

**Mitigations**:
- ✅ HTTPS enforced (TLS 1.2+)
- ✅ OTP encrypted with AES-256-GCM
- ✅ Optional certificate pinning

**Result**: Attacker sees encrypted payload only, cannot decrypt without keys

### 2. Replay Attack

**Attack**: Attacker captures valid request and replays it

**Mitigations**:
- ✅ Timestamp validation (±5 min window)
- ✅ Deduplication in storage
- ✅ HMAC prevents modification of timestamp

**Result**: Old requests rejected, duplicate requests ignored

### 3. Man-in-the-Middle (MITM)

**Attack**: Attacker intercepts and modifies requests

**Mitigations**:
- ✅ HTTPS with certificate validation
- ✅ HMAC signature verification
- ✅ GCM auth tag verification

**Result**: Modified requests fail HMAC/auth tag verification

### 4. Malicious App Reading Logs

**Attack**: Malicious app reads Android logs to steal OTPs

**Mitigations**:
- ✅ No OTP values in logs
- ✅ Only metadata logged (sender, timestamp)

**Result**: Logs contain no sensitive data

### 5. Backend Compromise

**Attack**: Attacker gains access to backend server

**Mitigations**:
- ✅ OTPs stored in memory only (TTL)
- ✅ Keys in environment variables (not in code)
- ✅ No database persistence of OTPs

**Result**: Limited exposure (only OTPs from last 5 minutes)

### 6. Brute Force Decryption

**Attack**: Attacker tries to brute force AES key

**Mitigations**:
- ✅ AES-256 (2^256 key space)
- ✅ Cryptographically secure random keys

**Result**: Computationally infeasible (would take billions of years)

### 7. Timing Attack on HMAC

**Attack**: Attacker measures HMAC verification time to guess signature

**Mitigations**:
- ✅ Constant-time comparison (`hmac.compare_digest`)

**Result**: Timing information doesn't leak signature validity

## Compliance Considerations

### GDPR (EU)
- ✅ Data minimization (OTPs deleted after 5 min)
- ✅ Purpose limitation (OTP forwarding only)
- ✅ Encryption at rest and in transit
- ⚠️ User consent required for SMS processing

### PCI DSS (if handling payment OTPs)
- ✅ Strong cryptography (AES-256)
- ✅ No storage of sensitive auth data
- ✅ Encrypted transmission
- ⚠️ Ensure compliance with full PCI requirements

### Google Play Policies
- ✅ Minimal permissions (RECEIVE_SMS, INTERNET)
- ✅ No READ_SMS permission
- ✅ Encrypted storage
- ✅ Privacy policy required

## Security Audit Checklist

- [ ] Encryption keys are randomly generated (64 hex chars)
- [ ] Keys are not committed to git
- [ ] HTTPS is enforced (no HTTP fallback)
- [ ] OTPs are never logged
- [ ] Timestamp validation is enabled
- [ ] HMAC uses constant-time comparison
- [ ] TTL storage is configured (≤5 minutes)
- [ ] Rate limiting is active
- [ ] Sender allowlist is configured
- [ ] Office hours are configured
- [ ] Android uses encrypted SharedPreferences
- [ ] Network security config disables cleartext
- [ ] WhatsApp template is approved by Meta
- [ ] Backend is behind HTTPS (Cloudflare Tunnel or similar)
- [ ] Redis is password-protected (if used)

## Incident Response

### If Encryption Keys Are Compromised

1. **Immediate**: Generate new keys
2. **Update**: Backend and Android app
3. **Revoke**: Old keys (delete from all systems)
4. **Audit**: Check logs for suspicious activity
5. **Notify**: Users if data may be exposed

### If Backend Is Compromised

1. **Immediate**: Take backend offline
2. **Investigate**: Check for data exfiltration
3. **Rotate**: All keys and credentials
4. **Patch**: Security vulnerabilities
5. **Restore**: From clean backup
6. **Monitor**: For continued compromise

### If WhatsApp API Token Is Leaked

1. **Immediate**: Revoke token in Meta Developer Portal
2. **Generate**: New token
3. **Update**: Backend configuration
4. **Audit**: WhatsApp message logs for abuse
5. **Monitor**: For unauthorized messages

## Recommendations for Production

1. **Certificate Pinning**: Implement in Android app
2. **Backend Time Validation**: Add office hours check on backend
3. **Key Rotation**: Automate every 90 days
4. **Monitoring**: Set up alerts for failed HMAC verifications
5. **Audit Logs**: Log all forwarding attempts (without OTP values)
6. **Backup Keys**: Securely store backup keys offline
7. **Penetration Testing**: Annual security audit
8. **Bug Bounty**: Consider bug bounty program

---

**Last Updated**: 2026-02-07  
**Security Contact**: [Your security email]
