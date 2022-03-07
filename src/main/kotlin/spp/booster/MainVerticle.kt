package spp.booster

import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.launch

class MainVerticle : CoroutineVerticle() {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Vertx.vertx().deployVerticle(MainVerticle())
        }
    }

    override suspend fun start() {
        val server = vertx.createHttpServer()
        val router = Router.router(vertx)
        setupSkyWalkingProxy(router)
        router.route().handler(StaticHandler.create().setCachingEnabled(false))
        router.route()
            .handler { ctx ->
                ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "text/html; charset=utf-8")
                    .sendFile("webroot/index.html")
            }
        server.requestHandler(router).listen(8080)
    }

    private fun setupSkyWalkingProxy(router: Router) {
        //SkyWalking Graphql
        val skywalkingHost = "localhost"
        val skywalkingPort = 12800
        val httpClient = vertx.createHttpClient()
        router.route("/graphql").handler(BodyHandler.create()).handler { req ->
            val body = req.bodyAsJson
            val headers = req.request().headers()
            val method = req.request().method()
            println("Forwarding SkyWalking request: $body")

            launch(vertx.dispatcher()) {
                val forward = httpClient.request(
                    method, skywalkingPort, skywalkingHost, "/graphql"
                ).await()

                forward.response().onComplete { resp ->
                    resp.result().body().onComplete {
                        val respBody = it.result()
                        println("Forwarding SkyWalking response: $respBody")
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
