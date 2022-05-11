package spp.booster

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.RequestOptions
import io.vertx.core.http.impl.MimeMapping
import io.vertx.ext.bridge.PermittedOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.sockjs.SockJSBridgeOptions
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream

class PortalServer(
    var serverPort: Int = 0,
    private val skywalkingHost: String = "localhost",
    private val skywalkingPort: Int = 12800,
) : CoroutineVerticle() {

    companion object {
        val log: Logger = LoggerFactory.getLogger(PortalServer::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            Vertx.vertx().deployVerticle(PortalServer(8081))
        }
    }

    override suspend fun start() {
        val server = vertx.createHttpServer()
        val router = Router.router(vertx)
        setupSkyWalkingProxy(router)

        val sockJSHandler = SockJSHandler.create(vertx)
        val portalBridgeOptions = SockJSBridgeOptions()
            .addInboundPermitted(PermittedOptions().setAddressRegex(".+"))
            .addOutboundPermitted(PermittedOptions().setAddressRegex(".+"))
        sockJSHandler.bridge(portalBridgeOptions)
        router.route("/eventbus/*").handler(sockJSHandler)

        // Static handler
        router.get("/*").handler {
            log.trace("Request: " + it.request().path() + " - Params: " + it.request().params())
            var fileStream: InputStream?
            val response = it.response().setStatusCode(200)
            if (it.request().path() == "/") {
                fileStream = PortalServer::class.java.classLoader.getResourceAsStream("webroot/index.html")
                if (fileStream == null) {
                    fileStream = PortalServer::class.java.getResourceAsStream("webroot/index.html")
                }

                response.end(Buffer.buffer(fileStream.readBytes()))
            } else {
                fileStream = PortalServer::class.java.classLoader.getResourceAsStream(
                    "webroot/" + it.request().path().substring(1)
                )
                if (fileStream == null) {
                    fileStream = PortalServer::class.java.getResourceAsStream(
                        "webroot/" + it.request().path().substring(1)
                    )
                }
                if (fileStream != null) {
                    response.putHeader(
                        "Content-Type",
                        MimeMapping.getMimeTypeForExtension(it.request().path().substringAfterLast("."))
                    ).end(Buffer.buffer(fileStream.readBytes()))
                }
            }

            if (!response.ended()) {
                it.next()
            }
        }

        router.route().handler { ctx ->
            var fileStream = PortalServer::class.java.classLoader.getResourceAsStream("webroot/index.html")
            if (fileStream == null) {
                fileStream = PortalServer::class.java.getResourceAsStream("webroot/index.html")
            }

            ctx.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "text/html; charset=utf-8")
                .end(Buffer.buffer(fileStream.readBytes()))
        }
        server.requestHandler(router).listen(serverPort).await()
        serverPort = server.actualPort()
    }

    private fun setupSkyWalkingProxy(router: Router) {
        val httpClient = vertx.createHttpClient(HttpClientOptions().setVerifyHost(false).setTrustAll(true))
        router.route("/graphql").handler(BodyHandler.create()).handler { req ->
            val body = req.bodyAsJson
            val headers = req.request().headers()
            val method = req.request().method()
            log.trace("Forwarding SkyWalking request: {}", body)
            val skywalkingToken =
                "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJkZXZlbG9wZXJfaWQiOiJzeXN0ZW0iLCJjcmVhdGVkX2F0IjoxNjIyNDIxMzY0ODY4LCJleHBpcmVzX2F0IjoxNjUzOTU3MzY0ODY4LCJpYXQiOjE2MjI0MjEzNjR9.ZVHtxQkfCF7KM_dyDOgawbwpEAsmnCWB4c8I52svPvVc-SlzkEe0SYrNufNPniYZeM3IF0Gbojl_DSk2KleAz9CLRO3zfegciXKeEEvGjsNOqfQjgU5yZtBWmTimVXq5QoZMEGuAojACaf-m4J0H7o4LQNGwrDVA-noXVE0Eu84A5HxkjrRuFlQWv3fzqSRC_-lI0zRKuFGD-JkIfJ9b_wP_OjBWT6nmqkZn_JmK7UwniTUJjocszSA2Ma3XLx2xVPzBcz00QWyjhIyiftxNQzgqLl1XDVkRtzXUIrHnFCR8BcgR_PsqTBn5nH7aCp16zgmkkbOpmJXlNpDSVz9zUY4NOrB1jTzDB190COrfCXddb7JO6fmpet9_Zd3kInJx4XsT3x7JfBSWr9FBqFoUmNkgIWjkbN1TpwMyizXASp1nOmwJ64FDIbSpfpgUAqfSWXKZYhSisfnBLEyHCjMSPzVmDh949w-W1wU9q5nGFtrx6PTOxK_WKOiWU8_oeTjL0pD8pKXqJMaLW-OIzfrl3kzQNuF80YT-nxmNtp5PrcxehprlPmqSB_dyTHccsO3l63d8y9hiIzfRUgUjTJbktFn5t41ADARMs_0WMpIGZJyxcVssstt4J1Gj8WUFOdqPsIKigJZMn3yshC5S-KY-7S0dVd0VXgvpPqmpb9Q9Uho"

            launch(vertx.dispatcher()) {
                val reqOptions = RequestOptions()
                    .setMethod(method)
                    .setHost(skywalkingHost)
                    .setPort(skywalkingPort)
                    .setURI("/graphql")
                    .setHeaders(headers.set("Authorization", "Bearer $skywalkingToken"))
                    .setSsl(true)
                val forward = httpClient.request(reqOptions).await()

                forward.response().onComplete { resp ->
                    resp.result().body().onComplete {
                        val respBody = it.result()
                        log.trace("Forwarding SkyWalking response: {}", respBody)
                        req.response()
                            .apply { headers().addAll(resp.result().headers()) }
                            .setStatusCode(resp.result().statusCode())
                            .end(respBody.toString())
                    }
                }
                forward.end(body.toString()).await()
            }
        }
    }
}
