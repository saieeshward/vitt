package ie.shoonya.vitt.net

import io.ktor.client.HttpClient

/** The platform's HTTP engine: Darwin on iOS, OkHttp on Android. */
expect fun platformHttpClient(): HttpClient
