package it.giampaolotrapasso.coroutine_webserver

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpGet
import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.kotlin.core.http.listenAwait
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class MainVerticle : CoroutineVerticle() {

  override suspend fun start() {

    val router = Router.router(vertx)

    val indexHandler = StaticHandler.create("html/").setCachingEnabled(false)

    router.get("/").handler(indexHandler)
    router.get("/index.html").handler(indexHandler)
    router.get("/fuel-await-response").coroutineHandler { ctx -> callFuelCoroutine(ctx) }
    router.get("/fuel-blocking").coroutineHandler { ctx -> callFuelBlocking(ctx) }
    router.get("/fuel-blocking-on-io-dispatcher").coroutineHandlerIO { ctx -> callFuelBlocking(ctx) }

    // Start the server
    vertx
      .createHttpServer()
      .requestHandler(router)
      .listenAwait(9998)
  }

  private suspend fun callFuelCoroutine(ctx: RoutingContext) {
    // Fuel jumps to the Dispatchers.IO CoroutineContext
    val (_, _, result) = Fuel.get("http://localhost:9999/slow").awaitStringResponseResult()

    forwardResultOrLogFailure(result, ctx)
  }

  private fun callFuelBlocking(ctx: RoutingContext) {
    println("Blocking code executed on ${Thread.currentThread().name}")
    val (_, _, result) = "http://localhost:9999/slow"
      .httpGet()
      .responseString()

    forwardResultOrLogFailure(result, ctx)
  }

  private fun Route.coroutineHandlerIO(fn: suspend (RoutingContext) -> Unit) {
    val scope : CoroutineContext = Dispatchers.IO
    handler { ctx ->
      launch(scope) {
        try {
          fn(ctx)
        } catch (e: Exception) {
          ctx.fail(e)
        }
      }
    }
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

  private fun forwardResultOrLogFailure(
    result: com.github.kittinunf.result.Result<String, FuelError>,
    ctx: RoutingContext
  ) {
    result.fold(
      { data ->
        ctx
          .response()
          .putHeader("content-type", "application/json")
          .end(data)
      },
      { error ->
        println("An error of type ${error.exception} happened: ${error.message}")
      }
    )
  }

}
