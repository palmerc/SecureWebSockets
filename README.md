This project is meant to add working TLS support to the [Autobahn WebSocket][6] library as a preliminary step to implementation at work.

The problem being solved was in part switching the Autobahn library off of Java NIO. Java NIO is broken on Android and you must use the classic java sockets. [Android Issue 12955][1]

The project comes with an example [Android][5] WebSocket [Echo client][4] that communicates with [WebSocket.org's echo server][2] with or without SSL encryption.

To disable SSL certificate checks you can tell [SSLCertificateSocketFactory][3] to relax its checks:
> On development devices, "setprop socket.relaxsslcheck yes" bypasses all SSL certificate and hostname checks for testing purposes. This setting requires root access. 

[1]: http://code.google.com/p/android/issues/detail?id=12955
[2]: http://www.websocket.org/echo.html
[3]: http://developer.android.com/reference/android/net/SSLCertificateSocketFactory.html
[4]: http://en.wikipedia.org/wiki/Echo_Protocol
[5]: http://www.android.com
[6]: http://autobahn.ws

