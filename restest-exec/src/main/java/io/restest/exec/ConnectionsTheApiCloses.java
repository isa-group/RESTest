/*
 * Copyright 2026 ISA Research Group, Universidad de Sevilla.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.restest.exec;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import javax.net.SocketFactory;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Response;

/**
 * Keeps the {@link OkHttpEngine} from sending a request on a connection the API has said it will
 * close.
 *
 * <p>The engine keeps each connection to the API open once a reply is over, so that the next
 * request can go on it without opening another. The API may say it will close the connection
 * instead. In HTTP/1.1 it says so with {@code Connection: close}, and the HTTP client the engine
 * uses understands that. In HTTP/1.0, the older version that some small servers still answer in,
 * closing is what happens unless the reply asks to keep the connection with
 * {@code Connection: keep-alive}, and the HTTP client does not know it: it keeps the connection,
 * the next request goes out on it after the API has closed it, reaches nobody, and comes back with
 * no reply. Against such an API, every other request was lost that way, and the report blamed the
 * API.
 *
 * <p>So the connections the engine opens are its own kind, {@link ApiSocket}, and when a reply says
 * the connection will close, this marks it as one nothing more is to be sent on, as soon as the
 * reply's status line and headers have arrived. The reply itself still arrives whole. Before it
 * sends on a connection it kept, the HTTP client asks whether anything more can be sent on it,
 * hears that it cannot, closes it and opens a new one. Nothing is sent twice: no request is sent
 * again because a connection failed, since that one may have reached the API.
 *
 * <p>Nothing goes over the network to mark a connection. When the HTTP client lets it go, which it
 * does once the reply has been read whole, the connection is ended outright rather than closed
 * politely. A polite close leaves whichever side closed first holding the address it used for half
 * a minute or more, in case a late part of the conversation turns up. Against such an API RESTest
 * opens a connection for every request, thousands a second against a fast one, and holding an
 * address for each would leave it with none to open the next within seconds. Ending a connection
 * outright loses nothing here: the reply is in, and the API has said it has nothing more to say.
 *
 * <p>Like {@link WireCapture}, it sits at the last point before the connection, where the
 * connection a reply came on is known.
 */
final class ConnectionsTheApiCloses implements Interceptor {

    /** The connections the HTTP client opens, each of them one this can mark. */
    static SocketFactory sockets() {
        return new Sockets();
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        Connection connection = chain.connection();
        if (connection != null && connection.socket() instanceof ApiSocket socket
                && closesAfter(response)) {
            socket.closesAfterThisReply();
        }
        return response;
    }

    /**
     * Whether the API closes the connection once this reply is over, which in HTTP/1.0 it does
     * unless the reply asks to keep it.
     *
     * @param response a reply whose status line and headers have arrived
     * @return whether the API closes the connection after it
     */
    static boolean closesAfter(Response response) {
        if (response.protocol() != Protocol.HTTP_1_0) {
            return false;
        }
        for (String value : response.headers("Connection")) {
            for (String option : value.split(",")) {
                if (option.strip().equalsIgnoreCase("keep-alive")) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A connection to the API that can be told the API will close it after the reply on its way.
     *
     * <p>A connection over TLS is not covered: the HTTP client asks the encrypted connection laid
     * over this one, not this one, whether more can be sent.
     */
    static final class ApiSocket extends Socket {

        private volatile boolean closing;

        /**
         * Notes that the API will close this connection once the reply on its way is over, and that
         * it is to be ended outright when it is let go.
         */
        void closesAfterThisReply() {
            closing = true;
            try {
                setSoLinger(true, 0);
            } catch (SocketException e) {
                // Already closed, so there is nothing left to end.
            }
        }

        /**
         * Whether nothing more is to be sent on this connection: because RESTest closed its own
         * side of it, or because the API said it will close it.
         */
        @Override
        public boolean isOutputShutdown() {
            return closing || super.isOutputShutdown();
        }
    }

    /**
     * Makes {@link ApiSocket}s. The HTTP client asks for one not yet connected and connects it
     * itself; the other ways of asking are there because every maker of connections has them.
     */
    private static final class Sockets extends SocketFactory {

        @Override
        public Socket createSocket() {
            return new ApiSocket();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return connected(new InetSocketAddress(host, port), null);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                throws IOException {
            return connected(new InetSocketAddress(host, port),
                    new InetSocketAddress(localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return connected(new InetSocketAddress(host, port), null);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
                int localPort) throws IOException {
            return connected(new InetSocketAddress(address, port),
                    new InetSocketAddress(localAddress, localPort));
        }

        private static Socket connected(InetSocketAddress remote, InetSocketAddress local)
                throws IOException {
            Socket socket = new ApiSocket();
            try {
                if (local != null) {
                    socket.bind(local);
                }
                socket.connect(remote);
                return socket;
            } catch (IOException e) {
                socket.close();
                throw e;
            }
        }
    }
}
