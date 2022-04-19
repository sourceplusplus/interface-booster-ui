package spp.booster

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.impl.MimeMapping
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream

class MainVerticle(
    private val listenPort: Int = 8080,
    private val skywalkingHost: String = "localhost",
    private val skywalkingPort: Int = 12800
) : CoroutineVerticle() {

    companion object {
        val log: Logger = LoggerFactory.getLogger(MainVerticle::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            Vertx.vertx().deployVerticle(MainVerticle())
        }
    }

    override suspend fun start() {
        val server = vertx.createHttpServer()
        val router = Router.router(vertx)
        setupSkyWalkingProxy(router)

        // Static handler
        router.get("/*").handler {
            log.trace("Request: " + it.request().path() + " - Params: " + it.request().params())
            var fileStream: InputStream?
            val response = it.response().setStatusCode(200)
            if (it.request().path() == "/") {
                fileStream = MainVerticle::class.java.classLoader.getResourceAsStream("webroot/index.html")
                if (fileStream == null) {
                    fileStream = MainVerticle::class.java.getResourceAsStream("webroot/index.html")
                }

                response.end(Buffer.buffer(fileStream.readBytes()))
            } else {
                fileStream = MainVerticle::class.java.classLoader.getResourceAsStream(
                    "webroot/" + it.request().path().substring(1)
                )
                if (fileStream == null) {
                    fileStream = MainVerticle::class.java.getResourceAsStream(
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

        router.route()
            .handler { ctx ->
                ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "text/html; charset=utf-8")
                    .sendFile("webroot/index.html")
            }
        server.requestHandler(router).listen(listenPort)
    }

    private fun setupSkyWalkingProxy(router: Router) {
        val httpClient = vertx.createHttpClient()
        router.route("/graphql").handler(BodyHandler.create()).handler { req ->
            val body = req.bodyAsJson
            val headers = req.request().headers()
            val method = req.request().method()
            log.trace("Forwarding SkyWalking request: {}", body)

            launch(vertx.dispatcher()) {
                val forward = httpClient.request(
                    method, skywalkingPort, skywalkingHost, "/graphql"
                ).await()

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

                headers?.forEach {
                    forward.putHeader(it.key, it.value)
                }
                forward.end(body.toString()).await()
            }
        }
    }
}
