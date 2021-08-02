package edu.vt.cs.cs5254.gallery

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import edu.vt.cs.cs5254.gallery.api.GalleryItem

private const val TAG = "MapView Fragment"

class PhotoMapFragment : MapViewFragment(), GoogleMap.OnMarkerClickListener {

    private lateinit var photoMapViewModel: PhotoMapViewModel
    private lateinit var thumbnailDownloader: ThumbnailDownloader<Marker>
    var geoGalleryItemMap = emptyMap<String, GalleryItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        photoMapViewModel = ViewModelProvider(this).get(PhotoMapViewModel::class.java)

        val responseHandler = Handler(Looper.myLooper()!!)
        thumbnailDownloader =
                ThumbnailDownloader(responseHandler) { marker, bitmap ->
                    setMarkerIcon(marker, bitmap)
                }

        lifecycle.addObserver(thumbnailDownloader.fragmentLifecycleObserver)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        viewLifecycleOwner.lifecycle.addObserver(thumbnailDownloader.viewLifecycleObserver)
        return super.onCreateMapView(
                inflater,
                container,
                savedInstanceState,
                R.layout.fragment_photo_map,
                R.id.map_view)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onMapViewCreated(view, savedInstanceState) { googleMap ->
            googleMap.setOnMarkerClickListener(this@PhotoMapFragment)
            photoMapViewModel.geoGalleryItemMapLiveData.observe(
                    viewLifecycleOwner,
                    Observer { galleryItemMap ->
                        geoGalleryItemMap = galleryItemMap
                        updateUI()
                    })
        }
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_gallery, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.refresh -> {
                FlickrFetchr.fetchPhotos(true)
                return true
            } else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewLifecycleOwner.lifecycle.removeObserver(thumbnailDownloader.viewLifecycleObserver)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycle.removeObserver(thumbnailDownloader.fragmentLifecycleObserver)
    }

    private fun updateUI() {

        if (!isAdded || geoGalleryItemMap.values.isEmpty() || !mapIsInitialized()) {
            return
        }

        Log.i(TAG, "Gallery has has " + geoGalleryItemMap.size + " items")


        googleMap.clear()

        val bounds = LatLngBounds.Builder()
        for (item in geoGalleryItemMap.values) {

            Log.i(TAG, "Item id=${item.id} " + "lat=${item.latitude} long=${item.longitude} " + "title=${item.title}")
            val itemPoint = LatLng(item.latitude.toDouble(), item.longitude.toDouble())
            bounds.include(itemPoint)
            
            val itemMarker = MarkerOptions().position(itemPoint).title(item.title)
            val marker = googleMap.addMarker(itemMarker)
            marker.tag = item.id

            if (item.id == null) {
                Log.i(TAG, "item has a null id $item $item.id ${item.photoPageUri}")
                throw IllegalStateException("item has a null id $item $item.id ${item.photoPageUri}")
            }
            val placeholder = PictureUtil.placeholder(requireContext())
            if (item.drawable == null || item.drawable == placeholder) {
                thumbnailDownloader.queueThumbnail(marker, item.url)
            } else {
                val drawable = item.drawable ?: placeholder
                setMarkerIcon(marker, PictureUtil.drawableToBitmap(drawable))
            }

        }

        Log.i(TAG, "Expecting ${geoGalleryItemMap.size} markers on the map")
    }


    override fun onMarkerClick(marker: Marker?): Boolean {
        val GalleryItemId = marker?.tag as String
        Log.d(TAG, "Clicked on marker $GalleryItemId")
        val item = geoGalleryItemMap[GalleryItemId]
        val uri = item?.photoPageUri ?: return false
        val intent = PhotoPageActivity.newIntent(requireContext(),uri)
        startActivity(intent)
        return true
    }

}


