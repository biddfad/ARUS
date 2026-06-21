package id.abid.arus

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.tomtom.sdk.common.Result
import com.tomtom.sdk.location.GeoPoint
import com.tomtom.sdk.map.display.MapOptions
import com.tomtom.sdk.map.display.TomTomMap
import com.tomtom.sdk.map.display.camera.CameraOptions
import com.tomtom.sdk.map.display.ui.MapFragment
import com.tomtom.sdk.search.Search
import com.tomtom.sdk.search.SearchOptions
import com.tomtom.sdk.search.online.OnlineSearch
import kotlin.concurrent.thread

object TomTomSetup {
    @JvmStatic
    fun buatOpsiPeta(kunciApi: String): MapOptions {
        return MapOptions(mapKey = kunciApi)
    }

    @JvmStatic
    fun buatMapFragment(opsi: MapOptions): MapFragment {
        return MapFragment.newInstance(opsi)
    }

    @JvmStatic
    fun buatSearchApi(context: Context, kunciApi: String): Search {
        return OnlineSearch.create(context, kunciApi)
    }

    @JvmStatic
    fun setCenterIndonesia(map: TomTomMap) {
        val indonesia = GeoPoint(-0.7893, 113.9213)
        map.moveCamera(CameraOptions(position = indonesia, zoom = 4.0))
    }

    interface SearchResultCallback {
        fun onSuccess(lat: Double, lon: Double)
        fun onFailure()
    }

    @JvmStatic
    fun cariLokasi(searchApi: Search, kueri: String, map: TomTomMap, callback: SearchResultCallback) {
        thread {
            try {
                val options = SearchOptions(query = kueri, limit = 1)
                val result = searchApi.search(options)
                Handler(Looper.getMainLooper()).post {
                    if (result.isSuccess()) {
                        val items = result.value().results
                        if (items.isNotEmpty()) {
                            val item = items[0]
                            val pos = item.place.coordinate
                            map.moveCamera(CameraOptions(position = pos, zoom = 14.0))
                            callback.onSuccess(pos.latitude, pos.longitude)
                        } else {
                            callback.onFailure()
                        }
                    } else {
                        callback.onFailure()
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    callback.onFailure()
                }
            }
        }
    }

    @JvmStatic
    fun cariBanyakLokasi(
        searchApi: com.tomtom.sdk.search.Search,
        kueri: String,
        map: TomTomMap,
        callback: SearchResultCallback
    ) {
        val searchOptions = com.tomtom.sdk.search.SearchOptions(
            query = kueri,
            limit = 10
        )

        kotlin.concurrent.thread {
            try {
                val response = searchApi.search(searchOptions)
                if (response.isSuccess()) {
                    val resultValue = response.value()
                    val results = resultValue.results
                    if (results.isNotEmpty()) {
                        Handler(Looper.getMainLooper()).post {
                            // Bersihkan marker lama
                            map.clear()
                            
                            val pinImage = com.tomtom.sdk.map.display.image.ImageFactory.fromResource(R.drawable.ic_pin)

                            var firstPos: GeoPoint? = null
                            
                            // Pasang marker untuk semua hasil
                            for (item in results) {
                                val pos = item.place.coordinate
                                if (firstPos == null) firstPos = pos
                                val options = com.tomtom.sdk.map.display.marker.MarkerOptions(
                                    coordinate = pos,
                                    pinImage = pinImage
                                )
                                map.addMarker(options)
                            }
                            
                            // Pindahkan kamera ke hasil pertama
                            if (firstPos != null) {
                                val cameraOptions = CameraOptions(
                                    position = firstPos,
                                    zoom = 12.0
                                )
                                map.moveCamera(cameraOptions)
                            }
                            
                            callback.onSuccess(firstPos?.latitude ?: 0.0, firstPos?.longitude ?: 0.0)
                        }
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            callback.onFailure()
                        }
                    }
                } else {
                    Handler(Looper.getMainLooper()).post {
                        callback.onFailure()
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    callback.onFailure()
                }
            }
        }
    }
    @JvmStatic
    fun buatRoutePlanner(context: Context, apiKey: String): com.tomtom.sdk.routing.RoutePlanner {
        return com.tomtom.sdk.routing.online.OnlineRoutePlanner.create(context, apiKey)
    }

    @JvmStatic
    fun buatLocationProvider(context: Context): com.tomtom.sdk.location.android.AndroidLocationProvider {
        return com.tomtom.sdk.location.android.AndroidLocationProvider(context = context)
    }

    @JvmStatic
    fun getCurrentGeoPoint(map: TomTomMap): GeoPoint? {
        return map.currentLocation?.position
    }

    interface RouteResultCallback {
        fun onSuccess(waktuMenit: Int, jarakKm: Double)
        fun onFailure()
    }

    @JvmStatic
    fun gambarRute(
        routePlanner: com.tomtom.sdk.routing.RoutePlanner,
        map: TomTomMap,
        destination: GeoPoint,
        callback: RouteResultCallback
    ) {
        val origin = map.currentLocation?.position ?: GeoPoint(-6.175392, 106.827153) // Monas default
        
        val routePlanningOptions = com.tomtom.sdk.routing.options.RoutePlanningOptions(
            itinerary = com.tomtom.sdk.routing.options.Itinerary(origin, destination)
        )

        routePlanner.planRoute(routePlanningOptions, object : com.tomtom.sdk.routing.RoutePlanningCallback {
            override fun onSuccess(result: com.tomtom.sdk.routing.RoutePlanningResponse) {
                Handler(Looper.getMainLooper()).post {
                    if (result.routes.isNotEmpty()) {
                        val route = result.routes.first()
                        
                        // Hapus rute lama
                        map.clear()
                        
                        // Gambar rute
                        val routeOptions = com.tomtom.sdk.map.display.route.RouteOptions(
                            geometry = route.geometry,
                            color = android.graphics.Color.BLUE
                        )
                        map.addRoute(routeOptions)

                        // Marker asal dan tujuan
                        val pinImage = com.tomtom.sdk.map.display.image.ImageFactory.fromResource(R.drawable.ic_pin)
                        map.addMarker(com.tomtom.sdk.map.display.marker.MarkerOptions(coordinate = origin, pinImage = pinImage))
                        map.addMarker(com.tomtom.sdk.map.display.marker.MarkerOptions(coordinate = destination, pinImage = pinImage))

                        // Pindahkan kamera
                        map.moveCamera(CameraOptions(position = origin, zoom = 11.0))
                        
                        val travelTimeSeconds = route.summary.travelTime.inWholeSeconds
                        val distanceMeters = route.summary.length.inWholeMeters()
                        
                        callback.onSuccess((travelTimeSeconds / 60).toInt(), distanceMeters / 1000.0)
                    } else {
                        callback.onFailure()
                    }
                }
            }

            override fun onFailure(failure: com.tomtom.sdk.routing.RoutingFailure) {
                Handler(Looper.getMainLooper()).post {
                    callback.onFailure()
                }
            }
        })
    }
}