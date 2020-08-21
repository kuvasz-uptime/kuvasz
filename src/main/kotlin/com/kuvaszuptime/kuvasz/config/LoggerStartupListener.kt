@file:Suppress("EmptyFunctionBlock")

package com.kuvaszuptime.kuvasz.config

import arrow.core.getOrElse
import arrow.core.toOption
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggerContextListener
import ch.qos.logback.core.Context
import ch.qos.logback.core.spi.ContextAwareBase
import ch.qos.logback.core.spi.LifeCycle

class LoggerStartupListener : ContextAwareBase(), LoggerContextListener, LifeCycle {
    private var isStarted = false

    override fun isStarted() = isStarted

    override fun isResetResistant() = true

    override fun onStart(context: LoggerContext?) {}

    override fun onReset(context: LoggerContext?) {}

    override fun onStop(context: LoggerContext?) {}

    override fun onLevelChange(logger: Logger?, level: Level?) {}

    override fun start() {
        if (isStarted) return
        val httpLogLevel =
            System.getenv("HTTP_COMMUNICATION_LOG_LEVEL").toOption().getOrElse { Level.INFO.levelStr }
        val context: Context = getContext()
        context.putProperty("HTTP_COMMUNICATION_LOG_LEVEL", httpLogLevel)
        isStarted = true
    }

    override fun stop() {}
}
