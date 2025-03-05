package com.wrmsr.viewrelay

interface Logger {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    fun log(level: Level, line: String)
    
    fun debug(line: String) = log(Level.DEBUG, line)
    fun info(line: String) = log(Level.INFO, line)
    fun warn(line: String) = log(Level.WARN, line)
    fun error(line: String) = log(Level.ERROR, line)

    fun exception(e: Throwable, line: String? = null) = {
        line?.let { error(it) }
        error(e.stackTraceToString())
    }
}

class CompositeLogger(val loggers: List<Logger>) : Logger {
    override fun log(level: Logger.Level, line: String) {
        loggers.forEach { it.log(level, line) }}
}

class PrintlnLogger : Logger {
    override fun log(level: Logger.Level, line: String) {
        println("[${level.name}] $line")
    }
}

class JulLogger(val logger: java.util.logging.Logger) : Logger {
    override fun log(level: Logger.Level, line: String) {
        logger.log(translateLevel(level), line)
    }

    companion object {
        fun translateLevel(level: Logger.Level): java.util.logging.Level = when (level) {
            Logger.Level.DEBUG -> java.util.logging.Level.FINE
            Logger.Level.INFO -> java.util.logging.Level.INFO
            Logger.Level.WARN -> java.util.logging.Level.WARNING
            Logger.Level.ERROR -> java.util.logging.Level.SEVERE
        }
    }
}
