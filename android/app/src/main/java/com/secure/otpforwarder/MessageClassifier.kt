package com.secure.otpforwarder

import java.util.regex.Pattern

/**
 * Port of backend classification logic to Android for local processing.
 * 
 * Supports categories: OTP, TRANSACTION, BILL, SECURITY_ALERT.
 */
enum class MessageType {
    OTP, TRANSACTION, BILL, SECURITY_ALERT, UNKNOWN
}

data class ClassificationResult(
    val type: MessageType,
    val confidence: Float,
    val metadata: Map<String, Any> = emptyMap()
)

class MessageClassifier {
    
    companion object {
        // OTP patterns (Simplified but effective for Android)
        private val OTP_PATTERNS = listOf(
            Pattern.compile("\\b(?:OTP|otp|code|verification|PIN|pin)\\b.*?(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(\\d{4,8})\\b.*?(?:OTP|otp|code|verification|PIN|pin)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:your|the)\\s+(?:OTP|code|PIN)\\s+(?:is|:)\\s*(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE)
        )
        
        private val GENERIC_OTP_PATTERN = Pattern.compile("\\b\\d{4,8}\\b")
        private val OTP_CONTEXT_WORDS = listOf("otp", "code", "verification", "pin", "password")

        // Transaction patterns
        private val TRANSACTION_KEYWORDS = listOf(
            Pattern.compile("\\b(?:debited|credited|withdrawn|deposited|transferred)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:debit|credit|withdrawal|deposit|transfer)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bA/c\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\baccount\\b.*?\\bXX\\d+\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bcard\\b.*?\\bXX\\d+\\b", Pattern.CASE_INSENSITIVE)
        )

        private val AMOUNT_PATTERNS = listOf(
            Pattern.compile("(?:Rs\\.?|INR|₹)\\s*[\\d,]+(?:\\.\\d{2})?"),
            Pattern.compile("[\\d,]+(?:\\.\\d{2})?\\s*(?:Rs\\.?|INR|₹)")
        )

        // Bill patterns
        private val BILL_KEYWORDS = listOf(
            Pattern.compile("\\b(?:bill|invoice|payment|due|overdue)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bpay\\s+(?:by|before)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bdue\\s+(?:date|on)\\b", Pattern.CASE_INSENSITIVE)
        )

        // Security patterns
        private val SECURITY_KEYWORDS = listOf(
            Pattern.compile("\\b(?:alert|security|suspicious|unauthorized|blocked|locked)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:fraud|scam|phishing)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bnew\\s+(?:device|location|login)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:verify|confirm)\\s+(?:identity|account)\\b", Pattern.CASE_INSENSITIVE)
        )
    }

    fun classify(messageBody: String): ClassificationResult {
        val lowBody = messageBody.lowercase()

        // 1. OTP (Highest priority)
        if (checkOtp(messageBody, lowBody)) {
            return ClassificationResult(MessageType.OTP, 0.95f, mapOf("urgency" to "high"))
        }

        // 2. Transaction
        val transScore = calculateScore(messageBody, TRANSACTION_KEYWORDS)
        val hasAmount = checkPatterns(messageBody, AMOUNT_PATTERNS)
        if (transScore >= 1 && hasAmount) {
            return ClassificationResult(MessageType.TRANSACTION, 0.9f, mapOf("urgency" to "medium"))
        }

        // 3. Security
        if (calculateScore(messageBody, SECURITY_KEYWORDS) >= 1) {
            return ClassificationResult(MessageType.SECURITY_ALERT, 0.85f, mapOf("urgency" to "high"))
        }

        // 4. Bill
        val billScore = calculateScore(messageBody, BILL_KEYWORDS)
        if (billScore >= 1 && hasAmount) {
            return ClassificationResult(MessageType.BILL, 0.8f, mapOf("urgency" to "low"))
        }

        return ClassificationResult(MessageType.UNKNOWN, 0f)
    }

    private fun checkOtp(message: String, lowBody: String): Boolean {
        for (pattern in OTP_PATTERNS) {
            if (pattern.matcher(message).find()) return true
        }
        
        if (GENERIC_OTP_PATTERN.matcher(message).find()) {
            if (OTP_CONTEXT_WORDS.any { lowBody.contains(it) }) return true
        }
        return false
    }

    private fun calculateScore(message: String, patterns: List<Pattern>): Int {
        var score = 0
        for (pattern in patterns) {
            if (pattern.matcher(message).find()) score++
        }
        return score
    }

    private fun checkPatterns(message: String, patterns: List<Pattern>): Boolean {
        for (pattern in patterns) {
            if (pattern.matcher(message).find()) return true
        }
        return false
    }
}
