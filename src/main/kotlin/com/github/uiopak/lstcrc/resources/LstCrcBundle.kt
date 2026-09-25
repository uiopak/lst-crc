package com.github.uiopak.lstcrc.resources

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.LstCrcMessages"

object LstCrcBundle : DynamicBundle(LstCrcBundle::class.java, BUNDLE) {
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}