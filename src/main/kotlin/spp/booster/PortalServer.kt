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
    private val jwtToken: String? = null
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

            launch(vertx.dispatcher()) {
                val reqOptions = RequestOptions()
                    .setMethod(method)
                    .setHost(skywalkingHost)
                    .setPort(skywalkingPort)
                    .setURI("/graphql")
                    .setHeaders(headers.apply { jwtToken?.let { set("Authorization", "Bearer $it") } })
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
