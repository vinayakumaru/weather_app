package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var binding : ActivityMainBinding
    private lateinit var mSharedPreferences: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        setupUI()

        if(!isLocationEnabled()){
            alertDialogFunction()
        }else{
            Dexter.withContext(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ).withListener(
                    object : MultiplePermissionsListener{
                        override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                            if(report!!.areAllPermissionsGranted()){
                                requestLocationData()
                            }
                            if(report.isAnyPermissionPermanentlyDenied){
                                Toast.makeText(
                                    this@MainActivity,
                                    "Please grant location permission",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onPermissionRationaleShouldBeShown(
                            p0: MutableList<PermissionRequest>?,
                            p1: PermissionToken?
                        ) {
                            showRationalDialogForPermissions()
                        }

                    }
                ).onSameThread().check()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest = LocationRequest.create()
        mLocationRequest.priority = Priority.PRIORITY_HIGH_ACCURACY
        mFusedLocationProviderClient.requestLocationUpdates(
            mLocationRequest,mLocationCallback,
            Looper.myLooper()
        )
    }

    private fun getLocationWeatherDetails(latitude : Double,longitude : Double){
        if(Constants.isNetworkAvailable(this)){
            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val service : WeatherService = retrofit.create(WeatherService::class.java)
            val listCall : Call<WeatherResponse> = service.getWeather(
                latitude,longitude,Constants.METRIC_UNIT,Constants.APP_ID
            )

            binding.pbRequest.visibility = View.VISIBLE
            listCall.enqueue(object : Callback<WeatherResponse> {
                @SuppressLint("ApplySharedPref")
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    binding.pbRequest.visibility = View.INVISIBLE
                    if(response.isSuccessful){
                        val weatherList : WeatherResponse? = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                        editor.putString(Constants.LATITUDE,latitude.toString())
                        editor.putString(Constants.LONGITUDE,longitude.toString())
                        editor.commit()
                        setupUI()

                        Log.i("Response Result", "$weatherList")
                    }else{
                        when(response.code()){
                            400 -> {
                                Log.e("Error 400","Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404","Not Found")
                            }
                            else -> {
                                Log.e("Error","Generic Error")
                            }
                        }
                    }
                }
                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Error", t.message.toString())
                    binding.pbRequest.visibility = View.INVISIBLE
                }
            })
        }else{
            Snackbar.make(binding.root, "internet not available", Toast.LENGTH_SHORT).show()
        }
    }
    private val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation!!
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude","$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Longitude","$longitude ")
            getLocationWeatherDetails(latitude, longitude)
        }
    }
    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("" +
                    "It looks like you have turned off permission required" +
                    "for this feature. It can be enabled under the " +
                    "Application Settings"
            ).setPositiveButton("GO TO SETTINGS"){
                    _,_ ->
                try{
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package",packageName,null)
                    intent.data = uri
                    startActivity(intent)
                }catch (e: ActivityNotFoundException){
                    e.printStackTrace()
                }
            }.setNegativeButton("Cancel"){
                    dialog,_ ->
                dialog.dismiss()
            }.show()
    }
    private fun isLocationEnabled(): Boolean{
        val locationManager : LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun setupUI(){
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")
        if(!weatherResponseJsonString.isNullOrEmpty()){
            val weatherList : WeatherResponse = Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)
            val latitude : Double = mSharedPreferences.getString(Constants.LATITUDE,"0")!!.toDouble()
            val longitude : Double = mSharedPreferences.getString(Constants.LONGITUDE,"0")!!.toDouble()
            for(i in weatherList.weather.indices){
                binding.tvMain.text = weatherList.weather[i].main
                binding.tvMainDescription.text = weatherList.weather[i].description
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                binding.tvTemp.text = buildString {
                    append(weatherList.main.temp)
                    append(getUnit(application.resources.configuration.locales.toString()))
                }
            }
            binding.tvSunriseTime.text = unixTime(weatherList.sys.sunrise)
            binding.tvSunsetTime.text = unixTime(weatherList.sys.sunset)
            binding.tvMin.text = buildString {
                append(weatherList.main.temp_min.toString())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    append(getUnit(application.resources.configuration.locales.toString()))
                }
                append(" min")
            }
            binding.tvMax.text = buildString {
                append(weatherList.main.temp_max.toString())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    append(getUnit(application.resources.configuration.locales.toString()))
                }
                append(" max")
            }
            binding.tvHumidity.text = buildString {
                append(weatherList.main.humidity.toString())
                append(" per cent")
            }
            binding.tvSpeed.text = weatherList.wind.speed.toString()
            binding.tvName.text = getCityName(latitude,longitude)
            binding.tvCountry.text = getCountryName(latitude,longitude)

            when (weatherList.weather[0].icon) {
                "01d" -> binding.ivMain.setImageResource(R.drawable.sunny)
                "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                "13d" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "11n" -> binding.ivMain.setImageResource(R.drawable.rain)
                "13n" -> binding.ivMain.setImageResource(R.drawable.snowflake)
            }
        }
    }

    private fun getUnit(v : String) : String {
        var value = " \u2103"
        if(v == "US" || v == "LR" || v == "MM"){
            value = " \u2109"
        }
        return value
    }

    private fun unixTime(timex : Long) : String {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("hh:mm a",Locale.getDefault())
//        sdf.timeZone =  TimeZone.getDefault()
        return sdf.format(date)
    }

    private fun getCityName(lat: Double, lon: Double): String {
        val geocoder = Geocoder(this, Locale.getDefault())
        val addresses: List<Address> = geocoder.getFromLocation(lat, lon, 1)
        return addresses[0].subLocality
    }

    private fun getCountryName(lat: Double, lon: Double): String {
        val geocoder = Geocoder(this, Locale.getDefault())
        val addresses: List<Address> = geocoder.getFromLocation(lat, lon, 1)
        return addresses[0].countryName
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh -> {
                mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback)
                requestLocationData()
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }
    private fun alertDialogFunction() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Message")
        builder.setMessage("Your location provider is turned OFF, please turn it ON")
        builder.setPositiveButton("Yes"){
                dialogInterface,_ ->
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
            dialogInterface.dismiss()
        }
        builder.setNegativeButton("no"){
                dialogInterface,_ ->
            dialogInterface.dismiss()
        }
        val alertDialog : androidx.appcompat.app.AlertDialog = builder.create()
        alertDialog.setCancelable(false)
        alertDialog.show()
    }
}