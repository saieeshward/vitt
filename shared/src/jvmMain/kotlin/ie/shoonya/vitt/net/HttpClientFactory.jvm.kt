package ie.shoonya.vitt.net

import io.ktor.client.HttpClient

actual fun platformHttpClient(): HttpClient = HttpClient()
