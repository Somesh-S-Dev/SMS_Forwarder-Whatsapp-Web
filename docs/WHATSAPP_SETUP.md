# WhatsApp Business Cloud API Setup Guide

This guide walks you through setting up WhatsApp Business Cloud API for OTP forwarding.

## Prerequisites

- Meta Business Account
- Phone number (not currently on WhatsApp)
- Valid credit card (for verification, free tier available)

## Step 1: Create Meta Business Account

1. Go to [Meta Business Suite](https://business.facebook.com/)
2. Click "Create Account"
3. Enter business name and details
4. Verify your email

## Step 2: Create WhatsApp Business App

1. Go to [Meta for Developers](https://developers.facebook.com/)
2. Click "My Apps" → "Create App"
3. Select "Business" as app type
4. Fill in app details:
   - **App Name**: "OTP Forwarder" (or your choice)
   - **App Contact Email**: Your email
   - **Business Account**: Select your business account
5. Click "Create App"

## Step 3: Add WhatsApp Product

1. In your app dashboard, find "WhatsApp" in the products list
2. Click "Set Up"
3. Select your Meta Business Account
4. Click "Continue"

## Step 4: Configure Phone Number

### Option A: Use Test Number (for development)

1. Meta provides a test number automatically
2. You can send messages to up to 5 test recipients
3. Add your personal WhatsApp number as a test recipient:
   - Go to "API Setup" tab
   - Under "To", click "Manage phone number list"
   - Add your WhatsApp number with country code (e.g., +1234567890)

### Option B: Add Your Own Number (for production)

1. Go to "API Setup" tab
2. Click "Add phone number"
3. Enter your business phone number
4. Verify via SMS or voice call
5. Complete two-factor authentication setup

**⚠️ Important**: This number will be used to SEND messages. It cannot be your personal WhatsApp number.

## Step 5: Get API Credentials

1. Go to "API Setup" tab
2. Copy the following values:

   **Temporary Access Token** (24 hours):
   ```
   Click "Copy" next to "Temporary access token"
   ```

   **Phone Number ID**:
   ```
   Click "Copy" next to "Phone number ID"
   ```

3. For production, generate a permanent token:
   - Go to "Business Settings" → "System Users"
   - Create a system user
   - Generate a token with `whatsapp_business_messaging` permission
   - Save this token securely

## Step 6: Create Message Template

WhatsApp requires pre-approved templates for business-initiated messages.

1. Go to "WhatsApp" → "Message Templates"
2. Click "Create Template"
3. Fill in template details:

   **Template Name**: `otp_notification`
   
   **Category**: `AUTHENTICATION` (important for OTP messages)
   
   **Languages**: English
   
   **Header**: None
   
   **Body**:
   ```
   Your OTP from {{1}} is {{2}}. Valid for 5 minutes.
   ```
   
   **Footer**: None
   
   **Buttons**: None

4. Click "Submit"

### Template Variables

- `{{1}}`: SMS sender name (e.g., "BANK-ALERT")
- `{{2}}`: OTP value (e.g., "123456")

### Template Approval

- **Review Time**: 24-48 hours
- **Status**: Check "Message Templates" for approval status
- **Rejection**: If rejected, modify and resubmit

**Common Rejection Reasons**:
- Vague or misleading content
- Promotional language in authentication template
- Missing required information

## Step 7: Configure Backend

Update your backend `.env` file:

```bash
# From Step 5
WHATSAPP_API_TOKEN=your_permanent_token_here
WHATSAPP_PHONE_NUMBER_ID=your_phone_number_id_here

# Your personal WhatsApp number (where OTPs will be sent)
WHATSAPP_RECIPIENT_NUMBER=1234567890  # Include country code, no + or spaces

# From Step 6
WHATSAPP_TEMPLATE_NAME=otp_notification
```

## Step 8: Test the Integration

### Using cURL

```bash
curl -X POST "https://graph.facebook.com/v18.0/YOUR_PHONE_NUMBER_ID/messages" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "messaging_product": "whatsapp",
    "to": "YOUR_RECIPIENT_NUMBER",
    "type": "template",
    "template": {
      "name": "otp_notification",
      "language": {
        "code": "en"
      },
      "components": [
        {
          "type": "body",
          "parameters": [
            {
              "type": "text",
              "text": "TEST-SENDER"
            },
            {
              "type": "text",
              "text": "123456"
            }
          ]
        }
      ]
    }
  }'
```

### Expected Response

```json
{
  "messaging_product": "whatsapp",
  "contacts": [
    {
      "input": "1234567890",
      "wa_id": "1234567890"
    }
  ],
  "messages": [
    {
      "id": "wamid.HBgNMTIzNDU2Nzg5MABCQUI..."
    }
  ]
}
```

### Check WhatsApp

You should receive a message on your personal WhatsApp:

```
Your OTP from TEST-SENDER is 123456. Valid for 5 minutes.
```

## Step 9: Monitor Usage

1. Go to "WhatsApp" → "Analytics"
2. Monitor:
   - Messages sent
   - Delivery rate
   - Read rate
   - Errors

## Pricing

### Free Tier
- **1,000 conversations/month** (free)
- Conversation = 24-hour window with a user

### Paid Tier
- After 1,000 conversations: ~$0.005 - $0.09 per conversation
- Varies by country
- See [WhatsApp Pricing](https://developers.facebook.com/docs/whatsapp/pricing)

### For OTP Forwarding
- Each OTP = 1 conversation
- If you receive <1,000 OTPs/month, it's FREE
- Most users will stay in free tier

## Troubleshooting

### Error: "Template not found"

**Cause**: Template not approved or wrong name

**Solution**:
1. Check template status in "Message Templates"
2. Ensure `WHATSAPP_TEMPLATE_NAME` matches exactly
3. Wait for approval if still pending

### Error: "Recipient phone number not valid"

**Cause**: Invalid phone number format

**Solution**:
1. Include country code (e.g., 1 for US)
2. Remove `+`, spaces, and dashes
3. Example: `+1 (234) 567-8900` → `12345678900`

### Error: "Access token expired"

**Cause**: Using temporary token (24-hour expiry)

**Solution**:
1. Generate permanent system user token
2. Update `WHATSAPP_API_TOKEN` in `.env`
3. Restart backend

### Error: "Insufficient permissions"

**Cause**: Token missing `whatsapp_business_messaging` permission

**Solution**:
1. Regenerate token with correct permissions
2. Ensure system user has WhatsApp app access

### Messages not delivering

**Possible Causes**:
1. Recipient hasn't opted in (for test numbers)
2. Recipient blocked business number
3. Rate limit exceeded
4. Phone number not verified

**Solution**:
1. Check "Analytics" for delivery status
2. Ensure recipient is in test recipient list (for test numbers)
3. Verify phone number ownership

## Production Checklist

- [ ] Business account verified
- [ ] Permanent access token generated
- [ ] Production phone number added and verified
- [ ] Message template approved
- [ ] Test message sent successfully
- [ ] Recipient number verified
- [ ] Monitoring set up
- [ ] Billing configured (if needed)

## Security Best Practices

1. **Never commit tokens to git**
2. **Use environment variables**
3. **Rotate tokens every 90 days**
4. **Monitor for unauthorized usage**
5. **Restrict token permissions** (only `whatsapp_business_messaging`)
6. **Use system users** (not personal accounts)

## Additional Resources

- [WhatsApp Business Platform Documentation](https://developers.facebook.com/docs/whatsapp)
- [Message Templates Guide](https://developers.facebook.com/docs/whatsapp/message-templates)
- [API Reference](https://developers.facebook.com/docs/whatsapp/cloud-api/reference/messages)
- [Pricing](https://developers.facebook.com/docs/whatsapp/pricing)

## Support

- [WhatsApp Business Support](https://business.whatsapp.com/support)
- [Developer Community](https://developers.facebook.com/community/)

---

**Setup Time**: ~30 minutes + 24-48 hours for template approval
