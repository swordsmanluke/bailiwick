package com.perfectlunacy.bailiwick.util

/**
 * Validation logic for the sign-up form.
 * Extracted for testability.
 */
object SignUpFormValidator {
    const val MIN_USERNAME_LENGTH = 4
    const val MIN_PASSWORD_LENGTH = 8

    /**
     * Result of a validation check.
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        object Empty : ValidationResult()
        data class TooShort(val minLength: Int) : ValidationResult()
        object Mismatch : ValidationResult()

        val isValid: Boolean get() = this is Valid
    }

    /**
     * Validate a username.
     * @param username The username to validate
     * @return ValidationResult indicating success or failure reason
     */
    fun validateUsername(username: String): ValidationResult {
        return when {
            username.isEmpty() -> ValidationResult.Empty
            username.length < MIN_USERNAME_LENGTH -> ValidationResult.TooShort(MIN_USERNAME_LENGTH)
            else -> ValidationResult.Valid
        }
    }

    /**
     * Validate a password.
     * @param password The password to validate
     * @return ValidationResult indicating success or failure reason
     */
    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isEmpty() -> ValidationResult.Empty
            password.length < MIN_PASSWORD_LENGTH -> ValidationResult.TooShort(MIN_PASSWORD_LENGTH)
            else -> ValidationResult.Valid
        }
    }

    /**
     * Validate that confirm password matches password.
     * @param password The original password
     * @param confirmPassword The confirmation password
     * @return ValidationResult indicating success or failure reason
     */
    fun validateConfirmPassword(password: String, confirmPassword: String): ValidationResult {
        return when {
            confirmPassword.isEmpty() -> ValidationResult.Empty
            confirmPassword != password -> ValidationResult.Mismatch
            else -> ValidationResult.Valid
        }
    }

    /**
     * Check if the entire form is valid.
     * @param username The username
     * @param password The password
     * @param confirmPassword The confirmation password
     * @return true if all fields are valid
     */
    fun isFormValid(username: String, password: String, confirmPassword: String): Boolean {
        return validateUsername(username).isValid &&
                validatePassword(password).isValid &&
                validateConfirmPassword(password, confirmPassword).isValid
    }
}
