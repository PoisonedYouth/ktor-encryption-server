package com.poisonedyouth.security

import com.poisonedyouth.KtorServerExtension
import com.poisonedyouth.configuration.ApplicationConfiguration
import io.ktor.server.config.ApplicationConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.passay.EnglishCharacterData


@ExtendWith(KtorServerExtension::class)
internal class PasswordManagerTest {

    @Test
    fun `createRandomPassword returns password with all necessary requirements`() {
        // given + when
        val password = PasswordManager.createRandomPassword()

        // then
        password.run {
            assertThat(this).hasSize(ApplicationConfiguration.passwordSettings.minimumLength)
            assertThat(this).matches(".*[0-9]*.+")
            assertThat(this).matches(".*[A-Z]+.*")
            assertThat(this).matches(".*[a-z].*+")
        }
    }

    @Test
    fun `createRandomPassword not returns duplicate password`() {
        // given + when
        repeat(1000) {
            val password1 = PasswordManager.createRandomPassword()
            val password2 = PasswordManager.createRandomPassword()
            assertThat(password1).isNotEqualTo(password2)
        }

    }

    @Test
    fun `validatePassword returns validation error if too short`() {
        // given
        val password = "too short"

        // when
        val actual = PasswordManager.validatePassword(password)

        // then
        assertThat(actual).contains("Password must be 16 or more characters in length.")
    }

    @Test
    fun `validatePassword returns validation error if missing lowercase`() {
        // given
        val password = "1234567890123456"

        // when
        val actual = PasswordManager.validatePassword(password)

        // then
        assertThat(actual).contains("Password must contain 1 or more lowercase characters.")
    }

    @Test
    fun `validatePassword returns validation error if missing uppercase`() {
        // given
        val password = "123456789012345f"

        // when
        val actual = PasswordManager.validatePassword(password)

        // then
        assertThat(actual).contains("Password must contain 1 or more uppercase characters.")
    }

    @Test
    fun `validatePassword returns validation error if missing digit`() {
        // given
        val password = "ffffffffffffffff"

        // when
        val actual = PasswordManager.validatePassword(password)

        // then
        assertThat(actual).contains("Password must contain 1 or more digit characters.")
    }

    @Test
    fun `validatePassword returns no validation error`() {
        // given
        val password = "ffffffffffff!g0G"

        // when
        val actual = PasswordManager.validatePassword(password)

        // then
        assertThat(actual).isEmpty()
    }
}