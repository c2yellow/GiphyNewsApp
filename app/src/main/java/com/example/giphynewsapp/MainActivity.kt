package com.example.giphynewsapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// tag for logging
private const val TAG = "MainActivity"


class MainActivity : AppCompatActivity() {
    private lateinit var adapter: GifAdapter
    private lateinit var gifViewModel: GifViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // set up RecyclerView and adapter
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        adapter = GifAdapter(mutableListOf<Gif>())
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // set up API, repository, view model
        val api = GiphyClient.api
        val repository = GifRepository(api)
        gifViewModel =
            ViewModelProvider(this, ViewModelFactory(repository)).get(GifViewModel::class.java)

        // observes LiveData in the view model and updates adapter when gif data has finished loading
        gifViewModel.gifs.observe(this, Observer { gifs ->
            adapter.updateItems(gifs)
        })
    }
}

// ViewModelFactory that creates ViewModel for MVVM
class ViewModelFactory(private val repository: GifRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GifViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GifViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// takes GiphyApi as parameter to parse data, abstracts data in MVVM
class GifRepository(private val api: GiphyApi) {
    suspend fun getTrendingGifs(apiKey: String): List<Gif> {
        return api.getTrendingGifs(apiKey).data
    }
}

// GifViewModel that takes GifRepository as a parameter
class GifViewModel(private val repository: GifRepository) : ViewModel() {
    private val _gifs = MutableLiveData<List<Gif>>()
    val gifs: LiveData<List<Gif>> = _gifs

    init {
        loadTrendingGifs()
    }

    fun loadTrendingGifs() {
        viewModelScope.launch {
            try {
                _gifs.value = repository.getTrendingGifs(BuildConfig.API_KEY)
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving GIFs", e)
            }
        }
    }
}

// Adapter for view that handles updating UI when data changes
class GifAdapter(private val articles: MutableList<Gif>) :
    RecyclerView.Adapter<GifAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gif, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val article = articles[position]
        holder.title.text = article.title
        Glide.with(holder.gif.context)
            .load(article.images.fixed_width.url)
            .into(holder.gif)
    }

    override fun getItemCount() = articles.size

    fun updateItems(newItems: List<Gif>) {
        articles.clear()
        articles.addAll(newItems)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val gif: ImageView = view.findViewById(R.id.gif)
        val title: TextView = view.findViewById(R.id.text)
    }
}

//singleton instance of Retrofit to construct Giphy API
object GiphyClient {
    private const val baseURL = "https://api.giphy.com/v1/"

    private val retrofit =
        Retrofit.Builder().baseUrl(baseURL).addConverterFactory(GsonConverterFactory.create())
            .build()

    val api: GiphyApi = retrofit.create(GiphyApi::class.java)
}

//defines Giphy API
interface GiphyApi {
    @GET("gifs/trending")
    suspend fun getTrendingGifs(@Query("api_key") apiKey: String): GiphyResponse
}

//a list of GIFs that are contained within the Giphy response
data class GiphyResponse(
    val data: List<Gif>
)

// relevant fields from GIF are the id, title (which is our headline), and images
data class Gif(
    val id: String, val title: String, val images: Images
)

// relevant fields are the original image and the reduced size fixed width image
data class Images(
    val original: Original, val fixed_width: FixedWidth

)

// url of the image, along with size data
data class Original(
    val url: String,
    val width: String,
    val height: String
)

// url of image, along with size data
data class FixedWidth(
    val url: String,
    val width: String,
    val height: String
)