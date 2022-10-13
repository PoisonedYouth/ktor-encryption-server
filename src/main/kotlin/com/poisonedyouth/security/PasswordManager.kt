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
import org.passay.RuleResult


const val MINIMUM_PASSWORD_LENGTH = 16

object PasswordManager {
    val characterRules = listOf(
        CharacterRule(Alphabetical),
        CharacterRule(Digit),
        CharacterRule(Special),
        CharacterRule(UpperCase),
        CharacterRule(LowerCase),
    )
    val lengthRule = LengthRule(16)
    private val passwordGenerator = PasswordGenerator()
    private val passwordValidator = PasswordValidator(characterRules + lengthRule)

    fun createRandomPassword(): String {
        return passwordGenerator.generatePassword(MINIMUM_PASSWORD_LENGTH, characterRules)
    }

    fun validatePassword(password: String): List<String> {
        val password = PasswordData(password)
        return passwordValidator.getMessages(passwordValidator.validate(password))
    }
}