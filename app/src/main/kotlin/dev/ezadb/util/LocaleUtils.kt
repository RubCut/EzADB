package dev.ezadb.util

import androidx.core.os.LocaleListCompat
import dev.ezadb.data.AppLanguage

fun AppLanguage.toLocaleList(): LocaleListCompat =
    if (this == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList()
    else LocaleListCompat.forLanguageTags(tag)
