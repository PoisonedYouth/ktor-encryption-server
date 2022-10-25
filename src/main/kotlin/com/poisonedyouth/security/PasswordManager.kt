package com.poisonedyouth.security

import com.poisonedyouth.configuration.ApplicationConfiguration
import org.passay.CharacterRule
import org.passay.EnglishCharacterData.Alphabetical
import org.passay.EnglishCharacterData.Digit
import org.passay.EnglishCharacterData.LowerCase
import org.passay.EnglishCharacterData.Special
import org.passay.EnglishCharacterData.UpperCase
import org.passay.LengthRule
import org.passay.PasswordData
import org.passay.PasswordGenerator
import org.passay.PasswordValidator

object PasswordManager {
    private val characterRules = initCharacterRules()
    private val lengthRule = initLengthRule()

    private val passwordGenerator = PasswordGenerator()
    private val passwordValidator = PasswordValidator(characterRules + lengthRule)

    private fun initCharacterRules(): List<CharacterRule> {
        val characterRules = mutableListOf(CharacterRule(Alphabetical))
        if (ApplicationConfiguration.passwordSettings.mustContainDigits) {
            characterRules.add(CharacterRule(Digit))
        }
        if (ApplicationConfiguration.passwordSettings.mustContainLowerCase) {
            characterRules.add(CharacterRule(LowerCase))
        }
        if (ApplicationConfiguration.passwordSettings.mustContainUpperCase) {
            characterRules.add(CharacterRule(UpperCase))
        }
        if (ApplicationConfiguration.passwordSettings.mustContainSpecial) {
            characterRules.add(CharacterRule(Special))
        }
        return characterRules
    }

    private fun initLengthRule() = LengthRule(ApplicationConfiguration.passwordSettings.minimumLength)


    fun createRandomPassword(): String {
        return passwordGenerator.generatePassword(ApplicationConfiguration.passwordSettings.minimumLength, characterRules)
    }

    fun validatePassword(password: String): List<String> {
        val passwordData = PasswordData(password)
        return passwordValidator.getMessages(passwordValidator.validate(passwordData))
    }
}