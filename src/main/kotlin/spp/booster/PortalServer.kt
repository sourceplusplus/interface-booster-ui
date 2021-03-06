package spp.booster

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.RequestOptions
import io.vertx.core.http.impl.MimeMapping
import io.vertx.ext.bridge.PermittedOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.SessionHandler
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
    private val ssl: Boolean = true,
    private val jwtToken: String? = null
) : CoroutineVerticle() {

    companion object {
        val log: Logger = LoggerFactory.getLogger(PortalServer::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            Vertx.vertx().deployVerticle(PortalServer(8081))
        }

        fun addSPAHandler(router: Router, sessionHandler: SessionHandler? = null) {
            router.get().apply { sessionHandler?.let { handler(it) } }.handler { ctx ->
                var fileStream = PortalServer::class.java.classLoader.getResourceAsStream("webroot/index.html")
                if (fileStream == null) {
                    fileStream = PortalServer::class.java.getResourceAsStream("webroot/index.html")
                }

                ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "text/html; charset=utf-8")
                    .end(Buffer.buffer(fileStream.readBytes()))
            }
        }

        fun addStaticHandler(router: Router, sessionHandler: SessionHandler? = null) {
            router.get("/*").apply { sessionHandler?.let { handler(it) } }.handler {
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
        }
    }

    override suspend fun start() {
        val server = vertx.createHttpServer()
        val router = Router.router(vertx)
        setupSkyWalkingProxy(router)

        addPortalEventbusHandler(router)
        addStaticHandler(router)
        addSPAHandler(router)

        server.requestHandler(router).listen(serverPort).await()
        serverPort = server.actualPort()
    }

    private fun addPortalEventbusHandler(router: Router) {
        val sockJSHandler = SockJSHandler.create(vertx)
        val portalBridgeOptions = SockJSBridgeOptions()
            .addInboundPermitted(PermittedOptions().setAddressRegex(".+"))
            .addOutboundPermitted(PermittedOptions().setAddressRegex(".+"))
        sockJSHandler.bridge(portalBridgeOptions)
        router.route("/eventbus/*").handler(sockJSHandler)
    }

    private fun setupSkyWalkingProxy(router: Router) {
        val httpClient = vertx.createHttpClient(HttpClientOptions().setVerifyHost(false).setTrustAll(true))
        router.post("/graphql/dashboard").handler(BodyHandler.create()).handler { req ->
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
                    .setSsl(ssl)
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
