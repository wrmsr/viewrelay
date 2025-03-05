package com.wrmsr.viewrelay

import java.io.File
import java.io.IOException
import java.net.*
import java.nio.channels.ServerSocketChannel
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class ChatServer(vararg listeners: Any) {
    private val serverSockets = mutableListOf<SocketHandler>()
    private val clients = ConcurrentHashMap.newKeySet<ClientHandler>()
    @Volatile private var running = true

    init {
        listeners.forEach { listener ->
            when (listener) {
                is Int -> serverSockets.add(TcpSocketHandler(listener))
                is String -> serverSockets.add(UnixSocketHandler(listener))
                else -> throw IllegalArgumentException("Unsupported listener type: $listener")
            }
        }
    }

    fun start() {
        serverSockets.forEach { handler -> thread { handler.run() } }
        println("Chat server started.")
    }

    fun broadcast(message: String, sender: ClientHandler) {
        synchronized(clients) {
            clients.filter { it != sender }.forEach { it.sendMessage(message) }
        }
    }

    fun removeClient(client: ClientHandler) {
        clients.remove(client)
    }

    fun close() {
        running = false
        serverSockets.forEach { it.close() }
        synchronized(clients) {
            clients.forEach { it.close() }
            clients.clear()
        }
        println("Chat server stopped.")
    }

    //

    abstract inner class SocketHandler {
        abstract fun run()
        abstract fun close()
    }

    inner class TcpSocketHandler(private val port: Int) : SocketHandler() {
        private var serverSocket: ServerSocket? = null

        override fun run() {
            try {
                serverSocket = ServerSocket(port)
                println("Listening on TCP port $port")

                while (!serverSocket!!.isClosed) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        val clientHandler = ClientHandler(clientSocket)
                        clients.add(clientHandler)
                        thread { clientHandler.run() }
                    } catch (e: SocketException) {
                        if (!serverSocket!!.isClosed) println("TCP error: ${e.message}")
                        break
                    }
                }
            } catch (e: IOException) {
                println("Failed to start TCP server on port $port: ${e.message}")
            } finally {
                close()
            }
        }

        override fun close() {
            try {
                serverSocket?.close()
            } catch (e: IOException) {
                println("Error closing TCP server socket: ${e.message}")
            }
        }
    }

    inner class UnixSocketHandler(private val path: String) : SocketHandler() {
        private var serverSocket: ServerSocketChannel? = null

        override fun run() {
            try {
                val socketFile = File(path)
                socketFile.delete() // Remove stale socket file
                val address = UnixDomainSocketAddress.of(Path.of(path))

                serverSocket = ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply {
                    bind(address)
                }
                println("Listening on Unix socket $path")

                while (serverSocket?.isOpen == true) {
                    try {
                        val clientSocket = serverSocket?.accept()?.socket() ?: break
                        val clientHandler = ClientHandler(clientSocket)
                        clients.add(clientHandler)
                        thread { clientHandler.run() }
                    } catch (e: SocketException) {
                        if (serverSocket?.isOpen == true) println("Unix socket error: ${e.message}")
                        break
                    }
                }
            } catch (e: IOException) {
                println("Failed to start Unix socket at $path: ${e.message}")
            } finally {
                close()
            }
        }

        override fun close() {
            try {
                serverSocket?.close()
                File(path).delete()
            } catch (e: IOException) {
                println("Error closing Unix socket: ${e.message}")
            }
        }
    }

    //

    inner class ClientHandler(private val socket: Socket) {
        private val reader = socket.getInputStream().bufferedReader()
        private val writer = socket.getOutputStream().bufferedWriter()
        private var running = true

        fun run() {
            try {
                println("Client connected: ${socket.remoteSocketAddress}")
                reader.forEachLine { line ->
                    if (!running) {
                        return@forEachLine
                    }
                    broadcast(line, this)
                }
            } catch (e: IOException) {
                if (running) {
                    println("Client communication error: ${e.message}")
                }
            } finally {
                close()
            }
        }

        fun sendMessage(message: String) {
            try {
                writer.write("$message\n")
                writer.flush()
            } catch (e: IOException) {
                println("Failed to send message to client: ${e.message}")
                close()
            }
        }

        fun close() {
            if (!running) {
                return
            }

            running = false
            removeClient(this)

            try {
                socket.close()
            } catch (e: IOException) {
                println("Error closing client socket: ${e.message}")
            }

            println("Client disconnected: ${socket.remoteSocketAddress}")
        }
    }
}

//fun main() {
//    val server = ChatServer(12345, "/tmp/chat.sock")
//    server.start()
//
//    // Simulate running indefinitely, stop server gracefully on input
//    readlnOrNull()
//    server.close()
//}
