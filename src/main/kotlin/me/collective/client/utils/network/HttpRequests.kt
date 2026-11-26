package me.collective.client.utils.network

import java.io.IOException
import java.io.UncheckedIOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpRequest.Builder
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandler
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

class HttpRequests private constructor() {

   companion object {
      private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)
      private val CLIENT: HttpClient = HttpClient.newBuilder()
         .connectTimeout(Duration.ofSeconds(10))
         .build()

      @JvmStatic
      fun send(request: HttpRequest): HttpResponse<String> {
         return send(request, BodyHandlers.ofString())
      }

      @JvmStatic
      fun <T> send(request: HttpRequest, bodyHandler: BodyHandler<T>): HttpResponse<T> {
         try {
            return CLIENT.send(request, bodyHandler)
         } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CompletionException("HTTP request interrupted", interrupted)
         } catch (failure: IOException) {
            throw UncheckedIOException("HTTP request failed", failure)
         }
      }

      @JvmStatic
      fun sendAsync(request: HttpRequest): CompletableFuture<HttpResponse<String>> {
         return sendAsync(request, BodyHandlers.ofString())
      }

      @JvmStatic
      fun <T> sendAsync(request: HttpRequest, bodyHandler: BodyHandler<T>): CompletableFuture<HttpResponse<T>> {
         return CLIENT.sendAsync(request, bodyHandler)
            .orTimeout(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
      }

      @JvmStatic
      fun get(url: String): Builder {
         return newRequest(url).GET()
      }

      @JvmStatic
      fun post(url: String, bodyPublisher: BodyPublisher): Builder {
         return newRequest(url).POST(bodyPublisher)
      }

      @JvmStatic
      fun newRequest(url: String): Builder {
         return HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT)
      }
   }
}
