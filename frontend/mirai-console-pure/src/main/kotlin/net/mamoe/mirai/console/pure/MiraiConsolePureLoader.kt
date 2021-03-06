/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress(
    "INVISIBLE_MEMBER",
    "INVISIBLE_REFERENCE",
    "CANNOT_OVERRIDE_INVISIBLE_MEMBER",
    "INVISIBLE_SETTER",
    "INVISIBLE_GETTER",
    "INVISIBLE_ABSTRACT_MEMBER_FROM_SUPER",
    "INVISIBLE_ABSTRACT_MEMBER_FROM_SUPE_WARNING"
)
@file:OptIn(ConsoleInternalAPI::class)

package net.mamoe.mirai.console.pure

import kotlinx.coroutines.isActive
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.job
import net.mamoe.mirai.console.pure.MiraiConsolePure.Companion.start
import net.mamoe.mirai.console.utils.ConsoleInternalAPI
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.utils.DefaultLogger
import java.io.PrintStream
import kotlin.concurrent.thread

/**
 * mirai-console-pure CLI 入口点
 */
object MiraiConsolePureLoader {
    @JvmStatic
    fun main(args: Array<String>?) {
        startup()
    }
}


internal fun startup() {
    DefaultLogger = { MiraiConsoleFrontEndPure.loggerFor(it) }
    overrideSTD()
    MiraiConsolePure().start()
    startConsoleThread()
}

internal fun overrideSTD() {
    System.setOut(
        PrintStream(
            BufferedOutputStream(
                logger = DefaultLogger("sout").run { ({ line: String? -> info(line) }) }
            )
        )
    )
    System.setErr(
        PrintStream(
            BufferedOutputStream(
                logger = DefaultLogger("serr").run { ({ line: String? -> warning(line) }) }
            )
        )
    )
}

internal fun startConsoleThread() {
    thread(name = "Console Input") {
        val consoleLogger = DefaultLogger("Console")
        try {
            kotlinx.coroutines.runBlocking {
                while (isActive) {
                    val next = MiraiConsoleFrontEndPure.requestInput("").let {
                        when {
                            it.startsWith(CommandPrefix) -> {
                                it
                            }
                            it == "?" -> CommandPrefix + BuiltInCommands.Help.primaryName
                            else -> CommandPrefix + it
                        }
                    }
                    if (next.isBlank()) {
                        continue
                    }
                    consoleLogger.debug("INPUT> $next")
                    val result = ConsoleCommandSenderImpl.executeCommandDetailed(next)
                    when (result.status) {
                        CommandExecuteStatus.SUCCESSFUL -> {
                        }
                        CommandExecuteStatus.EXECUTION_EXCEPTION -> {
                            result.exception?.printStackTrace()
                        }
                        CommandExecuteStatus.COMMAND_NOT_FOUND -> {
                            consoleLogger.warning("Unknown command: ${result.commandName}")
                        }
                        CommandExecuteStatus.PERMISSION_DENIED -> {
                            consoleLogger.warning("Permission denied.")
                        }

                    }
                }
            }
        } catch (e: InterruptedException) {
            return@thread
        }
    }.let { thread ->
        MiraiConsole.job.invokeOnCompletion {
            runCatching {
                thread.interrupt()
            }.exceptionOrNull()?.printStackTrace()
            runCatching {
                ConsoleUtils.terminal.close()
            }.exceptionOrNull()?.printStackTrace()
        }
    }
}

internal object ConsoleCommandSenderImpl : ConsoleCommandSender() {
    override suspend fun sendMessage(message: Message) {
        kotlin.runCatching {
            ConsoleUtils.lineReader.printAbove(message.contentToString())
        }.onFailure {
            println(message.content)
            it.printStackTrace()
        }
    }
}