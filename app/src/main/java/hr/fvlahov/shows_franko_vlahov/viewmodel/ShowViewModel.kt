package hr.fvlahov.shows_franko_vlahov.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import hr.fvlahov.shows_franko_vlahov.database.ShowsDatabase
import hr.fvlahov.shows_franko_vlahov.login.USER_EMAIL
import hr.fvlahov.shows_franko_vlahov.login.USER_ID
import hr.fvlahov.shows_franko_vlahov.login.USER_IMAGE
import hr.fvlahov.shows_franko_vlahov.model.api_response.ListShowsResponse
import hr.fvlahov.shows_franko_vlahov.model.api_response.LoginResponse
import hr.fvlahov.shows_franko_vlahov.model.api_response.Show
import hr.fvlahov.shows_franko_vlahov.model.api_response.User
import hr.fvlahov.shows_franko_vlahov.networking.ApiModule
import hr.fvlahov.shows_franko_vlahov.utils.NetworkChecker
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.util.concurrent.Executors

class ShowViewModel : ViewModel() {

    private val showsLiveData: MutableLiveData<List<Show>> by lazy {
        MutableLiveData<List<Show>>()
    }

    fun getShowsLiveData(): LiveData<List<Show>> {
        return showsLiveData
    }

    private val profileLiveData: MutableLiveData<User> by lazy {
        MutableLiveData<User>()
    }

    fun getProfileLiveData(): LiveData<User> {
        return profileLiveData
    }

    fun getProfileDetails(prefs: SharedPreferences) {
        profileLiveData.postValue(
            User(
                id = prefs.getInt(USER_ID, 0),
                email = prefs.getString(USER_EMAIL, "user") ?: "user",
                imageUrl = prefs.getString(USER_IMAGE, "")
            )
        )
    }

    //If you have a connection, retrieve data from API and save it to DB, otherwise retrieve from DB
    fun getShows(context : Context) {
        if(NetworkChecker().checkInternetConnectivity()){
            //TODO: Repository pattern -> handles retrieving data based on internet connection
            Executors.newSingleThreadExecutor().execute {
                ApiModule.retrofit.getAllShows()
                    .enqueue(object : Callback<ListShowsResponse> {
                        override fun onResponse(
                            call: Call<ListShowsResponse>,
                            response: Response<ListShowsResponse>
                        ) {
                            if(response.isSuccessful){
                                showsLiveData.value = response.body()?.shows

                                val showEntities = response.body()?.shows?.map { it.convertToEntity() }
                                if(showEntities != null){
                                    ShowsDatabase.getDatabase(context).showDao().insertAllShows(showEntities)
                                }
                                else{
                                    //TODO: Handle showEntities null error
                                }
                            }
                        }

                        override fun onFailure(call: Call<ListShowsResponse>, t: Throwable) {
                            //TODO: Handle retrieving all shows error
                        }

                    })
            }
        }
        else{
            val dbShows = ShowsDatabase.getDatabase(context).showDao().getAllShows().value?.map { it.convertToModel() }

            showsLiveData.value = dbShows
        }

    }

    fun uploadAvatarImage(imagePath: String) {
        ApiModule.retrofit.uploadImage(
            prepareImagePathForUpload(imagePath)
        )
            .enqueue(object : Callback<LoginResponse> {
                override fun onResponse(
                    call: Call<LoginResponse>,
                    response: Response<LoginResponse>
                ) {
                    profileLiveData.postValue(response.body()?.user)
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    throw t
                }

            })
    }

    private fun prepareImagePathForUpload(imagePath: String): MultipartBody.Part {
        val file = File(imagePath)
        val requestFile = file.asRequestBody("multipart/form-data".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("image", file.name, requestFile)
    }
}