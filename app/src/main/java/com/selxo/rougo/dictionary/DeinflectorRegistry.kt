package com.selxo.rougo.dictionary

import androidx.annotation.StringRes
import com.selxo.rougo.R
import com.selxo.rougo.dictionary.arabic.ArabicDeinflector
import com.selxo.rougo.dictionary.chinese.ChineseDeinflector
import com.selxo.rougo.dictionary.english.EnglishDeinflector
import com.selxo.rougo.dictionary.ko.KoreanDeinflector
import com.selxo.rougo.dictionary.de.GermanDeinflector
import com.selxo.rougo.dictionary.fr.FrenchDeinflector
import com.selxo.rougo.dictionary.ru.RussianDeinflector
import com.selxo.rougo.dictionary.es.SpanishDeinflector
import com.selxo.rougo.dictionary.it.ItalianDeinflector

data class DictionaryLanguageOption(
    val code: String,
    @param:StringRes val labelRes: Int,
)

/**
 * Registry to provide the appropriate [Deinflector] for a given language code.
 */
object DeinflectorRegistry {
    const val DEFAULT_LANGUAGE = "ja"

    val languageOptions = listOf(
        DictionaryLanguageOption("ja", R.string.dictionary_language_japanese),
        DictionaryLanguageOption("en", R.string.dictionary_language_english),
        DictionaryLanguageOption("zh", R.string.dictionary_language_chinese),
        DictionaryLanguageOption("ko", R.string.dictionary_language_korean),
        DictionaryLanguageOption("ar", R.string.dictionary_language_arabic),
        DictionaryLanguageOption("de", R.string.dictionary_language_german),
        DictionaryLanguageOption("es", R.string.dictionary_language_spanish),
        DictionaryLanguageOption("fr", R.string.dictionary_language_french),
        DictionaryLanguageOption("it", R.string.dictionary_language_italian),
        DictionaryLanguageOption("ru", R.string.dictionary_language_russian),
    )

    private val registry = mapOf(
        "ar" to ArabicDeinflector,
        "ko" to KoreanDeinflector,
        "en" to EnglishDeinflector,
        "zh" to ChineseDeinflector,
        "de" to GermanDeinflector,
        "fr" to FrenchDeinflector,
        "ru" to RussianDeinflector,
        "es" to SpanishDeinflector,
        "it" to ItalianDeinflector
    )

    fun get(languageCode: String): Deinflector? {
        return registry[normalize(languageCode)]
    }

    fun normalize(languageCode: String): String {
        val normalized = languageCode.trim().lowercase()
        return when {
            normalized.startsWith("zh") -> "zh"
            normalized.isBlank() -> DEFAULT_LANGUAGE
            else -> normalized.substringBefore('-')
        }
    }

    fun isSupported(languageCode: String): Boolean {
        val normalized = normalize(languageCode)
        return normalized == DEFAULT_LANGUAGE || normalized in registry
    }
}
