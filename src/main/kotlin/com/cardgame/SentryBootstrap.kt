package com.cardgame

import io.sentry.SentryAttributes
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryLogLevel
import io.sentry.logger.SentryLogParameters

/**
 * Initializes Sentry as early as possible in the process. DSN can be overridden with [SENTRY_DSN];
 * set it empty to disable reporting.
 */
object SentryBootstrap {
    private const val DEFAULT_DSN =
        "https://2c18c65ab342a08f438edf1fb5f71e33@o4511214016200704.ingest.us.sentry.io/4511218676924416"

    /** Matches [CPGameInfo] / package version — sent as Sentry release for grouping. */
    private const val RELEASE = "code437@1.0.0"

    private const val SHUTDOWN_FLUSH_MS = 5000L

    fun init(args: Array<String>) {
        val dsn = System.getenv("SENTRY_DSN") ?: DEFAULT_DSN
        if (dsn.isBlank()) {
            System.err.println("Sentry: disabled (SENTRY_DSN is blank). Unset SENTRY_DSN to use the default project DSN.")
            return
        }

        val debug =
            System.getenv("SENTRY_DEBUG") == "1" ||
                args.contains("--sentry-debug")

        Sentry.init { options ->
            options.dsn = dsn
            options.isSendDefaultPii = true
            options.logs.isEnabled = true
            options.isDebug = debug
            options.environment = System.getenv("SENTRY_ENVIRONMENT") ?: "development"
            options.release = System.getenv("SENTRY_RELEASE") ?: RELEASE
            // Helps shutdown deliver queued envelopes before the JVM stops.
            options.flushTimeoutMillis = SHUTDOWN_FLUSH_MS
        }
    }

    fun info(message: String, attributes: Map<String, Any?> = emptyMap(), origin: String = "game.lifecycle") {
        val logAttributes = SentryAttributes.fromMap(attributes)
        val logParams = SentryLogParameters.create(logAttributes).apply {
            this.origin = origin
        }
        Sentry.logger().log(SentryLogLevel.INFO, logParams, message)
    }

    fun captureCaughtError(
        message: String,
        throwable: Throwable? = null,
        attributes: Map<String, Any?> = emptyMap(),
    ) {
        val enriched = buildMap<String, Any?> {
            putAll(attributes)
            if (throwable != null) {
                put("error_class", throwable::class.simpleName ?: throwable::class.java.name)
                put("error_message", throwable.message ?: "")
            }
        }
        Sentry.withScope { scope ->
            scope.level = SentryLevel.ERROR
            for ((k, v) in enriched) {
                scope.setExtra(k, v?.toString() ?: "null")
            }
            Sentry.captureMessage(message)
        }
    }

    /** Optional: run with `--sentry-test` to send a single test error to Sentry. */
    fun captureTestExceptionIfRequested(args: Array<String>) {
        if (!args.contains("--sentry-test")) return
        try {
            throw Exception("This is a test.")
        } catch (e: Exception) {
            captureCaughtError(
                message = "Sentry test exception caught",
                throwable = e,
            )
        }
    }

    /** Emits both a Sentry log + message when a run starts from menu or level select. */
    fun recordNewGameStarted(startingLevel: Int, source: String, character: String) {
        val attrs = mapOf(
            "source" to source,
            "starting_level" to startingLevel,
            "character" to character,
        )
        info(
            message = "New game started",
            attributes = attrs,
            origin = "game.lifecycle",
        )
        Sentry.withScope { scope ->
            scope.level = SentryLevel.INFO
            scope.setTag("source", source)
            scope.setExtra("starting_level", startingLevel.toString())
            scope.setExtra("character", character)
            Sentry.captureMessage("New game started")
        }
    }

    fun close() {
        runCatching { flushAndCloseInternal() }
    }

    private fun flushAndCloseInternal() {
        Sentry.flush(SHUTDOWN_FLUSH_MS)
        Sentry.close()
    }
}
