/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ua.motorny.randomplayer.utils

import android.util.Log
import ua.motorny.randomplayer.BuildConfig

import kotlin.reflect.KClass

object LogHelper {

    private val LOG_PREFIX = "uamp_"
    private val LOG_PREFIX_LENGTH = LOG_PREFIX.length
    private val MAX_LOG_TAG_LENGTH = 23

    fun makeLogTag(str: String): String {
        if (str.length > MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH) {
            return LOG_PREFIX + str.substring(0, MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH - 1)
        }

        return LOG_PREFIX + str
    }

    /**
     * Don't use this when obfuscating class names!
     */
    fun makeLogTag(cls: KClass<out Any>): String {
        return makeLogTag(cls.toString())
    }


    fun v(tag: String, vararg messages: String) {
        // Only log VERBOSE if build type is DEBUG
        if (BuildConfig.DEBUG) {
            log(tag, Log.VERBOSE, null, *messages)
        }
    }

    fun d(tag: String, vararg messages: String) {
        // Only log DEBUG if build type is DEBUG
        if (BuildConfig.DEBUG) {
            log(tag, Log.DEBUG, null, *messages)
        }
    }

    fun i(tag: String, vararg messages: String) {
        log(tag, Log.INFO, null, *messages)
    }

    fun w(tag: String, vararg messages: String) {
        log(tag, Log.WARN, null, *messages)
    }

    fun w(tag: String, t: Throwable, vararg messages: String) {
        log(tag, Log.WARN, t, *messages)
    }

    fun e(tag: String, vararg messages: String) {
        log(tag, Log.ERROR, null, *messages)
    }

    fun e(tag: String, t: Throwable, vararg messages: String) {
        log(tag, Log.ERROR, t, *messages)
    }

    fun log(tag: String, level: Int, t: Throwable?, vararg messages: String) {
        if (Log.isLoggable(tag, level)) {
            val message: String
            if (t == null && messages.size == 1) {
                // handle this common case without the extra cost of creating a stringbuffer:
                message = messages[0].toString()
            } else {
                val sb = StringBuilder()
                for (m in messages) {
                    sb.append(m)
                }
                if (t != null) {
                    sb.append("\n").append(Log.getStackTraceString(t))
                }
                message = sb.toString()
            }
            Log.println(level, tag, message)
        }
    }
}
