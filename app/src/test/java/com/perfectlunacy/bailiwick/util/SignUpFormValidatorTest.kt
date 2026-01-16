package com.perfectlunacy.bailiwick.util

import com.perfectlunacy.bailiwick.util.SignUpFormValidator.ValidationResult
import org.junit.Assert.*
import org.junit.Test

class SignUpFormValidatorTest {

    // =====================
    // Username Validation
    // =====================

    @Test
    fun `validateUsername - empty username returns Empty`() {
        val result = SignUpFormValidator.validateUsername("")

        assertTrue(result is ValidationResult.Empty)
        assertFalse(result.isValid)
    }

    @Test
    fun `validateUsername - too short username returns TooShort`() {
        val result = SignUpFormValidator.validateUsername("abc") // 3 chars, need 4

        assertTrue(result is ValidationResult.TooShort)
        assertEquals(SignUpFormValidator.MIN_USERNAME_LENGTH, (result as ValidationResult.TooShort).minLength)
        assertFalse(result.isValid)
    }

    @Test
    fun `validateUsername - minimum length username returns Valid`() {
        val result = SignUpFormValidator.validateUsername("abcd") // exactly 4 chars

        assertTrue(result is ValidationResult.Valid)
        assertTrue(result.isValid)
    }

    @Test
    fun `validateUsername - long username returns Valid`() {
        val result = SignUpFormValidator.validateUsername("verylongusername123")

        assertTrue(result is ValidationResult.Valid)
        assertTrue(result.isValid)
    }

    // =====================
    // Password Validation
    // =====================

    @Test
    fun `validatePassword - empty password returns Empty`() {
        val result = SignUpFormValidator.validatePassword("")

        assertTrue(result is ValidationResult.Empty)
        assertFalse(result.isValid)
    }

    @Test
    fun `validatePassword - too short password returns TooShort`() {
        val result = SignUpFormValidator.validatePassword("short") // 5 chars, need 8

        assertTrue(result is ValidationResult.TooShort)
        assertEquals(SignUpFormValidator.MIN_PASSWORD_LENGTH, (result as ValidationResult.TooShort).minLength)
        assertFalse(result.isValid)
    }

    @Test
    fun `validatePassword - 7 chars is too short`() {
        val result = SignUpFormValidator.validatePassword("1234567") // 7 chars, need 8

        assertTrue(result is ValidationResult.TooShort)
        assertFalse(result.isValid)
    }

    @Test
    fun `validatePassword - minimum length password returns Valid`() {
        val result = SignUpFormValidator.validatePassword("12345678") // exactly 8 chars

        assertTrue(result is ValidationResult.Valid)
        assertTrue(result.isValid)
    }

    @Test
    fun `validatePassword - long password returns Valid`() {
        val result = SignUpFormValidator.validatePassword("verylongsecurepassword123!")

        assertTrue(result is ValidationResult.Valid)
        assertTrue(result.isValid)
    }

    // =====================
    // Confirm Password Validation
    // =====================

    @Test
    fun `validateConfirmPassword - empty confirm password returns Empty`() {
        val result = SignUpFormValidator.validateConfirmPassword("password", "")

        assertTrue(result is ValidationResult.Empty)
        assertFalse(result.isValid)
    }

    @Test
    fun `validateConfirmPassword - mismatched passwords returns Mismatch`() {
        val result = SignUpFormValidator.validateConfirmPassword("password1", "password2")

        assertTrue(result is ValidationResult.Mismatch)
        assertFalse(result.isValid)
    }

    @Test
    fun `validateConfirmPassword - matching passwords returns Valid`() {
        val result = SignUpFormValidator.validateConfirmPassword("mypassword", "mypassword")

        assertTrue(result is ValidationResult.Valid)
        assertTrue(result.isValid)
    }

    @Test
    fun `validateConfirmPassword - case sensitive comparison`() {
        val result = SignUpFormValidator.validateConfirmPassword("Password", "password")

        assertTrue(result is ValidationResult.Mismatch)
        assertFalse(result.isValid)
    }

    // =====================
    // Full Form Validation
    // =====================

    @Test
    fun `isFormValid - all valid returns true`() {
        val result = SignUpFormValidator.isFormValid(
            username = "validuser",
            password = "validpassword",
            confirmPassword = "validpassword"
        )

        assertTrue(result)
    }

    @Test
    fun `isFormValid - invalid username returns false`() {
        val result = SignUpFormValidator.isFormValid(
            username = "abc", // too short
            password = "validpassword",
            confirmPassword = "validpassword"
        )

        assertFalse(result)
    }

    @Test
    fun `isFormValid - invalid password returns false`() {
        val result = SignUpFormValidator.isFormValid(
            username = "validuser",
            password = "short", // too short
            confirmPassword = "short"
        )

        assertFalse(result)
    }

    @Test
    fun `isFormValid - mismatched passwords returns false`() {
        val result = SignUpFormValidator.isFormValid(
            username = "validuser",
            password = "validpassword",
            confirmPassword = "differentpassword"
        )

        assertFalse(result)
    }

    @Test
    fun `isFormValid - all empty returns false`() {
        val result = SignUpFormValidator.isFormValid(
            username = "",
            password = "",
            confirmPassword = ""
        )

        assertFalse(result)
    }

    @Test
    fun `isFormValid - minimum valid values`() {
        val result = SignUpFormValidator.isFormValid(
            username = "user", // exactly 4 chars
            password = "password", // exactly 8 chars
            confirmPassword = "password"
        )

        assertTrue(result)
    }

    // =====================
    // Edge Cases
    // =====================

    @Test
    fun `validateUsername - username with spaces is valid if long enough`() {
        val result = SignUpFormValidator.validateUsername("ab cd") // 5 chars including space

        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validatePassword - password with special characters is valid`() {
        val result = SignUpFormValidator.validatePassword("p@ssw0rd!")

        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `validateConfirmPassword - both empty passwords - confirm is Empty`() {
        val result = SignUpFormValidator.validateConfirmPassword("", "")

        // Even though they match, empty confirm should be Empty
        assertTrue(result is ValidationResult.Empty)
    }
}
