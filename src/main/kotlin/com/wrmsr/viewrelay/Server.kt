/*
nc localhost 8081
socat - UNIX-CONNECT:/tmp/chat.sock
*/
package com.wrmsr.viewrelay

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.*
import java.nio.channels.ServerSocketChannel
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class ViewRelayServer(vararg binds: Any) {
    private val servers = mutableListOf<ServerHandler<*>>()

    init {
        binds.forEach { bind ->
            when (bind) {
                is Int -> servers.add(TcpServerHandler(bind))
                is String -> servers.add(UnixServerHandler(bind))
                else -> throw IllegalArgumentException("Unsupported server bind type: $bind")
            }
        }
    }

    //

    fun start() {
        servers.forEach { server -> server.start() }

        println("ViewRelay server started.")
    }

    //

    private val clients = ConcurrentHashMap.newKeySet<ClientHandler>()

    fun broadcast(message: String, sender: ClientHandler) {
        synchronized(clients) {
            clients.filter { it != sender }.forEach { it.sendMessage(message) }
        }
    }

    private fun addClient(client: ClientHandler) {
        clients.add(client)
        client.start()
    }

    fun removeClient(client: ClientHandler) {
        clients.remove(client)
    }

    //

    fun close() {
        servers.forEach {
            it.close()
        }

        synchronized(clients) {
            clients.forEach { it.close() }
            clients.clear()
        }

        println("ViewRelay server stopped.")
    }

    //

    abstract inner class Handler {
        @Volatile private var _stopped = false

        val stopped: Boolean
            get() = _stopped

        @Volatile private var thread: Thread? = null

        @Synchronized
        fun start() {
            if (_stopped) {
                throw RuntimeException("Stopped")
            }

            if (thread != null) {
                return
            }

            thread = thread { threadMain() }
            thread?.start()
        }

        @Synchronized
        fun stop() {
            _stopped = true

            close()

            thread?.interrupt()
            thread?.join()

            close()
        }

        protected abstract fun threadMain()

        open fun close() {}
    }

    //

    abstract inner class SocketHandler<S : Closeable> : Handler() {
        @Volatile private var _socket: S? = null

        val socket: S?
            get() = _socket

        @Synchronized
        protected fun setSocket(socket: S) {
            if (_socket != null) {
                throw RuntimeException("Socket already set")
            }

            _socket = socket
        }

        override fun close() {
            super.close()

            try {
                _socket?.close()
            } catch (e: IOException) {
                println("Error closing socket: ${e.message}")
            }
        }
    }

    //

    abstract inner class ServerHandler<S: Closeable> : SocketHandler<S>() {
        abstract fun socketClosed(): Boolean?

        abstract fun bind(): S

        abstract fun accept(): Socket?

        override fun threadMain() {
            try {
                setSocket(bind())

                while (socketClosed() == false) {
                    try {
                        val clientSocket = accept() ?: break
                        try {
                            addClient(ClientHandler(clientSocket))
                        }
                        catch(e: Exception) {
                            clientSocket.close()
                            throw e
                        }

                    } catch (e: SocketException) {
                        if (socketClosed() == false) {
                            println("Server handler error: ${e.message}")
                        }

                        break
                    }
                }

            } catch (e: IOException) {
                println("Failed to start server: ${e.message}")

            } finally {
                close()
            }
        }
    }

    inner class TcpServerHandler(private val port: Int) : ServerHandler<ServerSocket>() {
        override fun socketClosed(): Boolean? {
            return socket?.isClosed
        }

        override fun bind(): ServerSocket {
            return ServerSocket(port)
        }

        override fun accept(): Socket? {
            return socket?.accept()
        }
    }

    inner class UnixServerHandler(private val path: String) : ServerHandler<ServerSocketChannel>() {
        override fun socketClosed(): Boolean? {
            return socket?.isOpen != true
        }

        override fun bind(): ServerSocketChannel {
            File(path).delete()

            val address = UnixDomainSocketAddress.of(Path.of(path))

            return ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply {
                bind(address)
            }
        }

        override fun accept(): Socket? {
            return socket?.accept()?.socket()
        }

        override fun close() {
            super.close()

            try {
                File(path).delete()
            } catch (e: IOException) {
                println("Error closing unix socket: ${e.message}")
            }
        }
    }

    //

    inner class ClientHandler(socket: Socket) : SocketHandler<Socket>() {
        init {
            setSocket(socket)
        }

        private val reader = socket.getInputStream().bufferedReader()
        private val writer = socket.getOutputStream().bufferedWriter()

        override fun threadMain() {
            try {
                println("Client connected: ${socket?.remoteSocketAddress}")

                reader.forEachLine { line ->
                    if (stopped) {
                        return@forEachLine
                    }
                    broadcast(line, this)
                }

            } catch (e: IOException) {
                if (!stopped) {
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

        override fun close() {
            super.close()

            removeClient(this)

            try {
                socket?.close()
            } catch (e: IOException) {
                println("Error closing client socket: ${e.message}")
            }

            println("Client disconnected: ${socket?.remoteSocketAddress}")
        }
    }
}
