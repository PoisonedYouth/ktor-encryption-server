package com.poisonedyouth.security

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


const val MINIMUM_PASSWORD_LENGTH = 16

object PasswordManager {
    private val characterRules = listOf(
        CharacterRule(Alphabetical),
        CharacterRule(Digit),
        CharacterRule(Special),
        CharacterRule(UpperCase),
        CharacterRule(LowerCase),
    )
    private val lengthRule = LengthRule(MINIMUM_PASSWORD_LENGTH)
    private val passwordGenerator = PasswordGenerator()
    private val passwordValidator = PasswordValidator(characterRules + lengthRule)

    fun createRandomPassword(): String {
        return passwordGenerator.generatePassword(MINIMUM_PASSWORD_LENGTH, characterRules)
    }

    fun validatePassword(password: String): List<String> {
        val passwordData = PasswordData(password)
        return passwordValidator.getMessages(passwordValidator.validate(passwordData))
    }
}