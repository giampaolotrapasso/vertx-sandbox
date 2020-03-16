package it.giampaolotrapasso.slowhttpserver

import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.http.listenAwait
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainVerticle : CoroutineVerticle() {

  override suspend fun start() {

    val router = Router.router(vertx)

    router.get("/").coroutineHandler{ctx -> sayHello(ctx)}
    router.get("/hello").coroutineHandler{ctx -> sayHello(ctx)}

    // Start the server
    vertx
      .createHttpServer()
      .requestHandler(router)
      .listenAwait(9999)
  }

  private suspend fun sayHello(ctx: RoutingContext) {
    val pause = Random.nextLong(3000, 8000)
    delay(pause)
    ctx
      .response()
      .putHeader("content-type", "application/json")
      .end(json {
        obj(
          "hello" to "from the slow server",
          "elapsed" to pause
        ).encode()
      })
  }

  private fun Route.coroutineHandler(fn: suspend (RoutingContext) -> Unit) {
    handler { ctx ->
      launch(ctx.vertx().dispatcher()) {
        try {
          fn(ctx)
        } catch (e: Exception) {
          ctx.fail(e)
        }
      }
    }
  }

}
