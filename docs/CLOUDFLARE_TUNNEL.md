# Cloudflare Tunnel Setup Guide

This guide shows how to expose your backend to the internet securely using Cloudflare Tunnel (formerly Argo Tunnel).

## Why Cloudflare Tunnel?

- **No Port Forwarding**: No need to open ports on your router
- **Free HTTPS**: Automatic SSL/TLS certificates
- **DDoS Protection**: Cloudflare's network protects your backend
- **No Public IP Required**: Works behind NAT/firewall
- **Easy Setup**: Simple CLI tool

## Prerequisites

- Cloudflare account (free tier works)
- Domain name (can use Cloudflare's free subdomain)
- Backend running locally

## Step 1: Install Cloudflared

### Windows

```powershell
# Download installer
Invoke-WebRequest -Uri "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe" -OutFile "cloudflared.exe"

# Move to a permanent location
Move-Item cloudflared.exe C:\Windows\System32\cloudflared.exe
```

### Linux

```bash
wget https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
sudo mv cloudflared-linux-amd64 /usr/local/bin/cloudflared
sudo chmod +x /usr/local/bin/cloudflared
```

### macOS

```bash
brew install cloudflared
```

## Step 2: Authenticate Cloudflared

```bash
cloudflared tunnel login
```

This will:
1. Open your browser
2. Ask you to select a domain
3. Save authentication credentials

## Step 3: Create a Tunnel

```bash
cloudflared tunnel create otp-forwarder
```

This creates a tunnel and saves credentials to:
- Windows: `C:\Users\<user>\.cloudflared\<tunnel-id>.json`
- Linux/macOS: `~/.cloudflared/<tunnel-id>.json`

**⚠️ Important**: Save the tunnel ID shown in the output.

## Step 4: Create Configuration File

Create `~/.cloudflared/config.yml`:

```yaml
tunnel: <your-tunnel-id>
credentials-file: /path/to/<tunnel-id>.json

ingress:
  - hostname: otp-forwarder.yourdomain.com
    service: http://localhost:8000
  - service: http_status:404
```

**Replace**:
- `<your-tunnel-id>`: From Step 3
- `<tunnel-id>.json`: From Step 3
- `otp-forwarder.yourdomain.com`: Your desired subdomain
- `http://localhost:8000`: Your backend URL (default FastAPI port)

## Step 5: Create DNS Record

```bash
cloudflared tunnel route dns otp-forwarder otp-forwarder.yourdomain.com
```

This creates a CNAME record pointing your subdomain to the tunnel.

## Step 6: Start the Tunnel

### Test Run

```bash
cloudflared tunnel run otp-forwarder
```

You should see:
```
INF Connection registered connIndex=0
INF Connection registered connIndex=1
INF Connection registered connIndex=2
INF Connection registered connIndex=3
```

### Test Access

```bash
curl https://otp-forwarder.yourdomain.com/health
```

Expected response:
```json
{
  "status": "healthy",
  "redis_connected": true,
  "whatsapp_api_configured": true
}
```

## Step 7: Run as a Service

### Windows

```powershell
# Install as service
cloudflared service install

# Start service
sc start cloudflared
```

### Linux (systemd)

```bash
# Install service
sudo cloudflared service install

# Start service
sudo systemctl start cloudflared

# Enable on boot
sudo systemctl enable cloudflared
```

### macOS (launchd)

```bash
# Install service
sudo cloudflared service install

# Start service
sudo launchctl start com.cloudflare.cloudflared
```

## Step 8: Configure Android App

In your Android app, set the backend URL to:

```
https://otp-forwarder.yourdomain.com
```

**⚠️ Important**: Use `https://`, not `http://`

## Alternative: Quick Tunnel (Development Only)

For quick testing without domain setup:

```bash
cloudflared tunnel --url http://localhost:8000
```

This gives you a temporary URL like:
```
https://random-words-1234.trycloudflare.com
```

**⚠️ Warning**: 
- URL changes every time
- Not suitable for production
- Use only for testing

## Troubleshooting

### Error: "tunnel credentials file not found"

**Solution**:
```bash
# Check credentials location
ls ~/.cloudflared/

# Update config.yml with correct path
```

### Error: "connection refused"

**Cause**: Backend not running

**Solution**:
```bash
# Start backend first
cd backend
python main.py

# Then start tunnel
cloudflared tunnel run otp-forwarder
```

### Error: "DNS record already exists"

**Solution**:
```bash
# Delete existing record in Cloudflare dashboard
# Or use different subdomain
```

### Tunnel connects but requests fail

**Possible Causes**:
1. Backend not listening on correct port
2. Firewall blocking localhost connections
3. Wrong service URL in config.yml

**Solution**:
```bash
# Check backend is running
curl http://localhost:8000/health

# Check config.yml service URL matches
```

## Security Considerations

### 1. Tunnel Credentials

**⚠️ Critical**: Protect tunnel credentials file

```bash
# Set restrictive permissions
chmod 600 ~/.cloudflared/<tunnel-id>.json
```

### 2. Backend Binding

Bind backend to localhost only:

```python
# In main.py
uvicorn.run("main:app", host="127.0.0.1", port=8000)
```

**Why?** Prevents direct access bypassing Cloudflare.

### 3. Rate Limiting

Cloudflare provides DDoS protection, but also configure backend rate limiting (already implemented).

### 4. Access Control

Consider adding Cloudflare Access for additional authentication:

1. Go to Cloudflare Dashboard → Zero Trust → Access
2. Create an application
3. Add authentication rules

## Monitoring

### Check Tunnel Status

```bash
cloudflared tunnel info otp-forwarder
```

### View Logs

```bash
# Linux/macOS
sudo journalctl -u cloudflared -f

# Windows
# Check Windows Event Viewer → Application logs
```

### Cloudflare Analytics

1. Go to Cloudflare Dashboard
2. Select your domain
3. Click "Analytics & Logs"
4. View traffic, requests, and errors

## Production Checklist

- [ ] Tunnel created and configured
- [ ] DNS record created
- [ ] HTTPS working (test with curl)
- [ ] Tunnel running as service
- [ ] Service starts on boot
- [ ] Credentials file secured (chmod 600)
- [ ] Backend bound to localhost only
- [ ] Monitoring configured
- [ ] Android app updated with HTTPS URL
- [ ] End-to-end test completed

## Cost

**Free Tier**:
- Unlimited bandwidth
- Unlimited tunnels
- DDoS protection
- SSL/TLS certificates

**Paid Features** (optional):
- Cloudflare Access (authentication)
- Advanced DDoS protection
- Load balancing

For OTP forwarding, **free tier is sufficient**.

## Alternative: Self-Hosted HTTPS

If you prefer not to use Cloudflare Tunnel:

### Option 1: Let's Encrypt + Nginx

```bash
# Install certbot
sudo apt install certbot python3-certbot-nginx

# Get certificate
sudo certbot --nginx -d otp-forwarder.yourdomain.com

# Configure nginx as reverse proxy
```

### Option 2: Caddy (Automatic HTTPS)

```bash
# Install Caddy
sudo apt install caddy

# Create Caddyfile
echo "otp-forwarder.yourdomain.com {
    reverse_proxy localhost:8000
}" | sudo tee /etc/caddy/Caddyfile

# Start Caddy
sudo systemctl start caddy
```

**Requirements**:
- Public IP address
- Port 80 and 443 open
- Domain pointing to your IP

## Additional Resources

- [Cloudflare Tunnel Documentation](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/)
- [Tunnel Configuration Reference](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/install-and-setup/tunnel-guide/)
- [Cloudflare Zero Trust](https://developers.cloudflare.com/cloudflare-one/)

---

**Setup Time**: ~15 minutes
