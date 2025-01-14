package com.greenfarm.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.greenfarm.R
import android.app.ActivityManager
import android.content.Intent
import android.graphics.*
import org.tensorflow.lite.examples.detection.tflite.Classifier.Recognition
import org.tensorflow.lite.examples.detection.tflite.Classifier
import org.tensorflow.lite.examples.detection.tflite.YoloV5Classifier
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.greenfarm.data.entities.FirebaseViewModel
import com.google.firebase.database.DatabaseError
import com.google.firebase.messaging.FirebaseMessaging
import com.greenfarm.data.entities.SearchSickNameResult
import com.greenfarm.data.nearby.NearbyUser
import com.greenfarm.data.nearby.NearbyUserResult
import com.greenfarm.data.remote.Search.SearchService
import com.greenfarm.databinding.ActivityTestBinding
import com.greenfarm.ui.guideLine.GuidelineActivity
import com.greenfarm.ui.main.MainActivity
import com.greenfarm.utils.getJwt
import com.greenfarm.utils.getUserId
import org.tensorflow.lite.examples.detection.env.Logger
import org.tensorflow.lite.examples.detection.env.Utils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.HashSet

//import org.tensorflow.lite.examples.detection.DetectorActivity;
//import java.util.Random;
class TestActivity : AppCompatActivity(), SearchSickNameView {
    private var filepath: String? = null
    private var detector: Classifier? = null
    private var sourceBitmap: Bitmap? = null
    private var cropBitmap: Bitmap? = null

    var sickname : String? = null
    var sickInformation = ArrayList<SearchSickNameResult>()
    val sickInformationRVAdapter = SickInformationRVAdapter(sickInformation, this)

    var imageView: ImageView? = null
    var userList = ArrayList<String>()
    var isLog : Boolean? = null
    val disease: MutableList<String> = ArrayList()
    val diseaseNoti: MutableList<String?> = ArrayList()
    var userid : String? = null
    var token : String? = null

    lateinit var binding: ActivityTestBinding

    lateinit var phoneNumberDialog : PhoneNumberDialog


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val configurationInfo = activityManager.deviceConfigurationInfo
        val handler = Handler()

        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(MyFirebaseMessagingService.TAG, "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            // Get new FCM registration token
            token = task.result
            Log.d("token",token.toString())
        })

        imageView = findViewById(R.id.test_iv)
        isLog = intent!!.getBooleanExtra("IsLog",false)
        userid = getUserId()

        // Information RecyclerView 설정
        var recyclerView = findViewById<RecyclerView>(R.id.test_result_item_recyclerview)

        recyclerView.layoutManager = LinearLayoutManager(baseContext, LinearLayoutManager.VERTICAL, false)
        recyclerView.adapter = sickInformationRVAdapter


        // 기록화면 확인 후 함수 실행
        // 기록화면 X (Test 화면)
        if (isLog == false) {
            if (intent.getStringExtra("class") == "sesame") {
                TF_OD_API_MODEL_FILE = "sesame_annot_yolov5s.tflite"
                TF_OD_API_LABELS_FILE = "file:///android_asset/sesame-label.txt"
            } else if (intent.getStringExtra("class") == "red-bean") {
                TF_OD_API_MODEL_FILE = "best-fp16-redbean.tflite"
                TF_OD_API_LABELS_FILE = "file:///android_asset/red-bean-label.txt"
            } else if (intent.getStringExtra("class") == "bean") {
                TF_OD_API_MODEL_FILE = "best-fp16_bean.tflite"
                TF_OD_API_LABELS_FILE = "file:///android_asset/coco.txt"
            }
            sourceBitmap =
                Utils.getBitmapFromAsset(this@TestActivity, intent.getStringExtra("image"))
            cropBitmap = Utils.processBitmap(sourceBitmap, TF_OD_API_INPUT_SIZE)
            imageView!!.setImageBitmap(cropBitmap)

            initBox()
            Thread {
                val startTime = System.currentTimeMillis()
                val results = detector!!.recognizeImage(cropBitmap)
                handler.post {
                    handleResult(cropBitmap, results)
                    val endTime = System.currentTimeMillis()
                    Log.d("Model running time", (endTime - startTime).toString())
                    // 모델 실행시간 약 0.9 ~ 1초
                }
            }.start()
            System.err.println(configurationInfo.glEsVersion.toDouble())
            System.err.println(configurationInfo.reqGlEsVersion >= 0x30000)
            System.err.println(String.format("%X", configurationInfo.reqGlEsVersion))
        }

        // 지난 기록 화면
        else {
            Glide.with(this).load(intent.getStringExtra("image")).into(binding.testIv)
            Log.d("images","${intent.getStringExtra("image")}")

            val searchSickNameResult : SearchSickNameResult = SearchSickNameResult()
            searchSickNameResult.sickNameKor = intent.getStringExtra("sickName").toString()
            searchSickNameResult.symptoms = intent.getStringExtra("symptoms").toString()
            searchSickNameResult.infectionRoute = intent.getStringExtra("infectionRoute").toString()
            searchSickNameResult.developmentCondition = intent.getStringExtra("developmentCondition").toString()
            searchSickNameResult.preventionMethod = intent.getStringExtra("preventionMethod").toString()
            sickInformation.add(searchSickNameResult)
            sickInformationRVAdapter.notifyDataSetChanged()
        }

        binding.loginBt.setOnClickListener{
            val intent= Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }


    private fun initBox() {
        try {
            detector = YoloV5Classifier.create(
                assets,
                TF_OD_API_MODEL_FILE,
                TF_OD_API_LABELS_FILE,
                TF_OD_API_IS_QUANTIZED,
                TF_OD_API_INPUT_SIZE
            )
        } catch (e: IOException) {
            e.printStackTrace()
            LOGGER.e(e, "Exception initializing classifier!")
            val toast = Toast.makeText(
                applicationContext, "Classifier could not be initialized", Toast.LENGTH_SHORT
            )
            toast.show()
            finish()
        }
    }

    private fun handleResult(bitmap: Bitmap?, results: List<Recognition>) {
        val canvas = Canvas(bitmap!!)
        val paint = Paint()
        val textPaint = Paint()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        textPaint.textSize = 25f
        textPaint.color = Color.parseColor("#e701a4")
        textPaint.style = Paint.Style.FILL_AND_STROKE
        textPaint.isAntiAlias = false
        textPaint.alpha = 255
        if (results.size == 0) {
            Log.d("not found","not found")
            val guideIntent =  Intent(this, GuidelineActivity::class.java)
            startActivity(guideIntent)
        } else {

            for (i in results.indices) {
                val result = results[i]
                val location = result.location
                var name: String? = null
                if (intent.getStringExtra("class") == "sesame") {
                    if (location != null && result.confidence >= MINIMUM_CONFIDENCE_TF_OD_API) {
                        Log.d("title", result.title)
                        if (result.title == "Bacterial leaf spo") {
                            paint.color = Color.parseColor("#ea0438")
                            name = "세균성점무늬병"
                            sickname = "Bacterial leaf spo"
                        } else if (result.title == "Powdery mildew2") {
                            paint.color = Color.parseColor("#af30ea")
                            name = "흰가루병"
                            sickname = "Powdery mildew2"
                        } else {
                            paint.color = Color.CYAN
                        }
                        diseaseNoti.add(name)
                        disease.add(result.title)
                        canvas.drawRect(location, paint)
                        val labelString = if (!TextUtils.isEmpty(result.title)) String.format(
                            "%.2f",
                            100 * result.confidence
                        ) else String.format("%.2f", 100 * result.confidence)
                        canvas.drawText("$labelString%", (location.left+location.right)/2, location.top, textPaint)
                        //                cropToFrameTransform.mapRect(location);
                        //
                        //                result.setLocation(location);
                        //                Log.d("2",result.getLocation().toString());
                        //                mappedRecognitions.add(result);
                    }
                } else if (intent.getStringExtra("class") == "red-bean") {
                    if (location != null && result.confidence >= MINIMUM_CONFIDENCE_TF_OD_API) {
                        Log.d("title", result.title)
                        if (result.title == "Rhizopus") {
                            paint.color = Color.parseColor("#ea0438")
                            name = "리조푸스"
                            sickname = "Rhizopus"
                        } else if (result.title == "Bacterial leaf blight") {
                            paint.color = Color.parseColor("#af30ea")
                            name = "잎마름병"
                            sickname = "Bacterial leaf blight"
                        } else if (result.title == "Powdery mildew1") {
                            paint.color = Color.parseColor("#ea562b")
                            name = "흰가루병"
                            sickname = "Powdery mildew1"
                        } else {
                            paint.color = Color.CYAN
                        }
                        diseaseNoti.add(name)
                        disease.add(result.title)
                        canvas.drawRect(location, paint)
                        val labelString = if (!TextUtils.isEmpty(result.title)) String.format(
                            "%.2f",
                            100 * result.confidence,
                        ) else String.format("%.2f", 100 * result.confidence)
                        canvas.drawText("$labelString%", (location.left+location.right)/2, location.top, textPaint)

                    }
                }
                else if (intent.getStringExtra("class") == "bean") {
                    if (location != null && result.confidence >= MINIMUM_CONFIDENCE_TF_OD_API) {
                        Log.d("title", result.title)
                        if (result.title == "Downy mildew") {
                            paint.color = Color.parseColor("#ea0438")
                            name = "노균병"
                            sickname = "Downy mildew"
                        }
                        else if (result.title == "Bacterial pustule") {
                            paint.color = Color.parseColor("#ea562b")
                            name = "불마름병"
                            sickname = "Bacterial pustule"
                        }

                        else {
                            paint.color = Color.CYAN
                        }
                        diseaseNoti.add(name)
                        disease.add(result.title)
                        canvas.drawRect(location, paint)
                        val labelString = if (!TextUtils.isEmpty(result.title)) String.format(
                            "%.2f",
                            100 * result.confidence,
                        ) else String.format("%.2f", 100 * result.confidence)
                        canvas.drawText("$labelString%", (location.left+location.right)/2, location.top, textPaint)

                    }
                }
            }
            Log.d("disease",disease.toString())
            val diseaseSetOuter = HashSet(disease)

            if(diseaseSetOuter.contains("Powdery mildew2")){
                phoneNumberDialog = PhoneNumberDialog(this)
                phoneNumberDialog.show()
            }

            saveBitmapAsPNGFile(bitmap)

            // 서버에 병해충 이름 사진 등 전달
            var currentTime: Long = System.currentTimeMillis()
            var cnt = 0
            for(i in diseaseSetOuter){
                Log.d("i",i)
                Log.d("i",currentTime.toString())
                Log.d("disease", "${diseaseSetOuter}")
                if(cnt == 0){
                    searchSickName(userid!!, i, filepath!!, currentTime)
                    cnt+=1
                }else{
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.d("start","start req")
                        searchSickName(userid!!, i, filepath!!, currentTime)
                    }, 200)
                }
                Log.d("start","end")
            }

            // 이미지뷰에 사진 넣기
            imageView!!.setImageBitmap(bitmap)

            // 일정 반경 내 유저 아이디 수신
            NearbyUser.getNearbyUser(this, userid!!)
        }
    }

    fun sendMessageToUserList(nearbyUsers: List<String>) {
        val diseaseSet = HashSet(diseaseNoti)
        // 파이어베이스 데이터베이스에서 해당 유저 아이디 토큰 받아옴
        val tokens: MutableList<String?> = ArrayList()
        val mDatabase: DatabaseReference = FirebaseDatabase.getInstance().reference
        val mUser = mDatabase.child("tokens")
        mUser.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (i in nearbyUsers.indices) {
                    if(snapshot.child(nearbyUsers[i]).getValue(String::class.java) != null){
                        userList.add(nearbyUsers[i])
                        tokens.add(snapshot.child(nearbyUsers[i]).getValue(String::class.java))
                    }
                }
                userList.add(userid.toString())
                tokens.add(token.toString())
                for(i in 0 until tokens.size){
                    val firebaseViewModel = FirebaseViewModel(application)
                    // fcm서버에 해당 토큰에 대해 알림 요청
                    firebaseViewModel.sendNotification(
                        tokens[i]!!,
                        userList[i],
                        "발견된 병해충: $diseaseSet"
                    )
                }

                Log.d("tokens", tokens.toString())
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun saveBitmapAsPNGFile(bitmap: Bitmap) {
        val path = File(this.filesDir, "image")
        if (!path.exists()) {
            path.mkdirs()
        }
        val photoName = newPngFileName()
        val file = File(path, photoName)
        var imageFile = null as OutputStream?
        try {
            file.createNewFile()
            imageFile = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 60, imageFile)
            imageFile!!.close()
            filepath = file.absolutePath.toString()
        } catch (var7: Exception) {

        }
    }

    private fun newPngFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd__HHmmss")
        val filename = sdf.format(System.currentTimeMillis())
        return "$filename.png"
    }

    companion object {
        const val MINIMUM_CONFIDENCE_TF_OD_API = 0.5f
        private var TF_OD_API_MODEL_FILE = "best-fp16_bean.tflite"
        private var TF_OD_API_LABELS_FILE = "file:///android_asset/coco.txt"
        private val LOGGER = Logger()
        const val TF_OD_API_INPUT_SIZE = 640
        private const val TF_OD_API_IS_QUANTIZED = false

        // Minimum detection confidence to track a detection.
        private const val MAINTAIN_ASPECT = true
    }

    private fun searchSickName(userId : String, sickName : String, filepath : String, currentTime: Long){
        val file = File(filepath)
        Handler(Looper.getMainLooper()).postDelayed({
            SearchService.SearchSickName(this, userId, sickName, file, currentTime)
        }, 300)
    }

    override fun onSearchSickNameLoading() {}

    override fun onSearchSickNameSuccess(searchSickNameResult: SearchSickNameResult) {
        Log.d("cropName","${searchSickNameResult.cropName}")
        Log.d("sickNameKor","${searchSickNameResult.sickNameKor}")
        Log.d("developmentCondition","${searchSickNameResult.developmentCondition}")
        Log.d("preventionMethod","${searchSickNameResult.preventionMethod}")
        Log.d("infectionRoute","${searchSickNameResult.infectionRoute}")
        Log.d("symptoms","${searchSickNameResult.symptoms}")
        sickInformation.add(searchSickNameResult)
        Log.d("sickinformation","${sickInformation}")
        sickInformationRVAdapter.notifyDataSetChanged()
    }

    override fun onSearchSickNameFailure(code: Int, message: String) {
        when(code) {
            2001 -> {
                Log.d("message",message)
            }
            else -> {
                Log.d("message",message)
            }
        }
    }
}