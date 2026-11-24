package com.xperience.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.xperience.player.service.MediaNetworkClients

/**
 * Custom [Application] registering the shared Coil [ImageLoader] via [ImageLoaderFactory]
 * so every AsyncImage and Coil call shares one OkHttp connection pool, memory cache,
 * and lazy disk cache without blocking cold start.
 */
class XperienceApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return MediaNetworkClients.getImageLoader(this)
    }
}
