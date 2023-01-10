package com.example.reba

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.atan2


private class PoseAnalyzer(private val poseFoundListener: (Pose) -> Unit) : ImageAnalysis.Analyzer {

    private val options = AccuratePoseDetectorOptions.Builder()
    .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
    .build()

    private val poseDetector = PoseDetection.getClient(options);

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            // Accurate pose detector on static images, when depending on the pose-detection-accurate sdk

            poseDetector
                    .process(image)
                    .addOnSuccessListener { pose ->
                        poseFoundListener(pose)
                        imageProxy.close()
                    }
                    .addOnFailureListener { error ->
                        Log.d(TAG, "Failed to process the image")
                        error.printStackTrace()
                        imageProxy.close()
                    }
        }
    }
}

class RectOverlay constructor(context: Context?, attributeSet: AttributeSet?) :
    View(context, attributeSet) {
    private lateinit var extraCanvas: Canvas
    private lateinit var extraBitmap: Bitmap
    private val STROKE_WIDTH = 2f // has to be float
    private val drawColor = Color.WHITE
    // Set up the paint with which to draw.
    private val paint = Paint().apply {
        color = drawColor
        // Smooths out edges of what is drawn without affecting shape.
        isAntiAlias = true
        // Dithering affects how colors with higher-precision than the device are down-sampled.
        isDither = true
        style = Paint.Style.STROKE // default: FILL
        strokeJoin = Paint.Join.ROUND // default: MITER
        strokeCap = Paint.Cap.ROUND // default: BUTT
        strokeWidth = STROKE_WIDTH // default: Hairline-width (really thin)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (::extraBitmap.isInitialized) extraBitmap.recycle()
        extraBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        extraCanvas = Canvas(extraBitmap)
        extraCanvas.drawColor(Color.TRANSPARENT)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(extraBitmap, 0f, 0f, null)
    }

     fun clear() {
        extraCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }

    internal fun drawLine(
        startLandmark: PoseLandmark?,
        endLandmark: PoseLandmark?
    ) {
        val start = startLandmark!!.position
        val end = endLandmark!!.position


        val xmul = 3.3f;
        val ymul = 3.3f;

        extraCanvas.drawLine(
            (start.x * xmul) - 250, start.y* ymul, (end.x* xmul) -250, end.y* ymul, paint
        )
        invalidate();
    }

    internal fun drawNeck(
        _occhioSx: PoseLandmark?,
        _occhioDx: PoseLandmark?,
        _spallaSx: PoseLandmark?,
        _spallaDx: PoseLandmark?
    ) {

        val xmul = 3.3f;
        val ymul = 3.3f;


        val occhioSx = _occhioSx!!.position
        val occhioDx = _occhioDx!!.position
        val spallaSx = _spallaSx!!.position
        val spallaDx = _spallaDx!!.position


        val fineColloX =  occhioDx.x +  ((occhioSx.x - occhioDx.x) / 2);
        val fineColloY = occhioDx.y + ((occhioSx.y - occhioDx.y) / 2);

        val inizioColloX = spallaDx.x + ((spallaSx.x - spallaDx.x ) / 2);
        val inizioColloY = spallaDx.y + ((spallaSx.y - spallaDx.y) / 2);

        extraCanvas.drawLine(
            (fineColloX * xmul) - 250, fineColloY* ymul, (inizioColloX* xmul) -250, inizioColloY* ymul, paint
        )

        extraCanvas.drawLine(
            (occhioSx.x * xmul) - 250, occhioSx.y* ymul, (occhioDx.x* xmul) -250, occhioDx.y* ymul, paint
        )
        invalidate();
    }


}

class MainActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var textView : TextView;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listener for take photo button
//        camera_capture_button.setOnClickListener { takePhoto() }

        textView = findViewById(R.id.text_view_id)

        cameraExecutor = Executors.newSingleThreadExecutor()

    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults:
            IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    fun getAngle(firstPoint: PoseLandmark, midPoint: PoseLandmark, lastPoint: PoseLandmark): Double {

        var result = Math.toDegrees(atan2( lastPoint.getPosition().y.toDouble() - midPoint.getPosition().y,
            lastPoint.getPosition().x.toDouble() - midPoint.getPosition().x)
                - atan2(firstPoint.getPosition().y - midPoint.getPosition().y,
            firstPoint.getPosition().x - midPoint.getPosition().x))
        result = Math.abs(result) // 각도는 절대 음수일 수 없습니다
        if (result > 180) {
            result = 360.0 - result // 항상 각도를 선명하게 표현하십시오.
        }
        return result
    }

    fun getNeckAngle(
        ear: PoseLandmark, shoulder: PoseLandmark
    ): Double {

        var result = Math.toDegrees(atan2( shoulder.getPosition().y.toDouble() - shoulder.getPosition().y,
            (shoulder.getPosition().x + 100 ).toDouble() - shoulder.getPosition().x)
                - atan2(ear.getPosition().y - shoulder.getPosition().y,
            ear.getPosition().x - shoulder.getPosition().x))

        result = Math.abs(result) // 각도는 절대 음수일 수 없습니다

        if (result > 180) {
            result = 360.0 - result // 항상 각도를 선명하게 표현하십시오.
        }
        return result
    }

    private fun onTextFound(pose: Pose)  {
        try {

            //왼쪽 어깨
            val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
            //오른쪽 어깨
            val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
            //왼쪽 팔꿈치
            val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
            //오른쪽 팔꿈치
            val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
            // 왼쪽 손목
            val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
            //오른쪽 손목
            val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
            //왼쪽 엉덩이
            val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
            //오른쪽 엉덩이
            val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
            //왼쪽 무릎
            val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
            //오른쪽 무릎
            val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
            //왼쪽 발목
            val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
            //오른쪽 발목
            val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
            //왼쪽 새끼손가락
            val leftPinky = pose.getPoseLandmark(PoseLandmark.LEFT_PINKY)
            //오른쪽 새끼손가락
            val rightPinky = pose.getPoseLandmark(PoseLandmark.RIGHT_PINKY)
            //왼쪽 검지손가락
            val leftIndex = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX)
            //오른쪽 검지손가락
            val rightIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX)
            //왼쪽 엄지손가락
            val leftThumb = pose.getPoseLandmark(PoseLandmark.LEFT_THUMB)
            //오른쪽 엄지손가락
            val rightThumb = pose.getPoseLandmark(PoseLandmark.RIGHT_THUMB)

            //왼쪽 발꿈치
            val leftHeel = pose.getPoseLandmark(PoseLandmark.LEFT_HEEL)
            //오른쪽 발꿈치
            val rightHeel = pose.getPoseLandmark(PoseLandmark.RIGHT_HEEL)
            //왼쪽 엄지발가락
            val leftFootIndex = pose.getPoseLandmark(PoseLandmark.LEFT_FOOT_INDEX)
            //오른쪽 엄지발가락
            val rightFootIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_FOOT_INDEX)
            //왼쪽 눈
            val leftEye = pose.getPoseLandmark(PoseLandmark.LEFT_EYE);
            //오른쪽 눈
            val rightEye = pose.getPoseLandmark(PoseLandmark.RIGHT_EYE);
            //왼쪽 귀
            val leftEar = pose.getPoseLandmark(PoseLandmark.LEFT_EAR);
            //오른쪽 귀
            val rightEar = pose.getPoseLandmark(PoseLandmark.RIGHT_EAR);



            val builder = StringBuilder()
            rect_overlay.clear()

            // 눈과 귀 사이의 평균으로 목을 그립니다.
            if( leftEye != null && rightEye != null && leftShoulder != null && rightShoulder != null  ){
                rect_overlay.drawNeck(leftEye, rightEye, leftShoulder, rightShoulder);
            }

            // 왼쪽 목
            var leftNeckAngle = getNeckAngle(leftEar, leftShoulder);
            if(leftEar != null && leftShoulder != null){
                rect_overlay.drawLine(leftEar, leftShoulder)
                builder.append("${leftNeckAngle.toInt()} 왼쪽 목 \n")
            }

            // 오른쪽 목
            var rightNeckAngle = getNeckAngle(rightEar, rightShoulder);
            if(rightEar != null && rightShoulder != null){
                rect_overlay.drawLine(rightEar, rightShoulder)
                builder.append("${rightNeckAngle.toInt()} 오른쪽 목 \n")
            }

            // 오른쪽 가슴
            var rightChestAngle = getAngle(rightShoulder, rightHip, rightKnee);
            if(rightShoulder != null && rightHip != null && rightKnee != null){
                builder.append("${rightChestAngle.toInt()} 오른쪽 가슴 \n")
            }

            //왼쪽 가슴
            var leftChestAngle = getAngle(leftShoulder, leftHip, leftKnee);
            if(leftShoulder != null && leftHip != null && leftKnee != null){
                builder.append("${leftChestAngle.toInt()} 왼쪽 가슴 \n")
            }


            //오른쪽 다리
            var rightLegAngle = getAngle( rightHip, rightKnee, rightAnkle);
            if( rightHip != null && rightKnee != null  && rightAnkle != null){

                builder.append("${ rightLegAngle.toInt()} 오른쪽 다리 \n")
            }

            //왼쪽다리
            var leftLegAngle = getAngle( leftHip, leftKnee,leftAnkle);
            if( leftHip != null && leftKnee != null  && leftAnkle != null){

                builder.append("${leftLegAngle.toInt()} 왼쪽 다리 \n")
            }
            // 왼쪽 팔
            var leftArmAngle = getAngle( leftShoulder, leftElbow,leftWrist);
            if( leftShoulder != null && leftWrist != null  && leftElbow != null){

                builder.append("${leftArmAngle.toInt()} 왼쪽 팔 \n")
            }
            // 오른쪽 팔
            var rightArmAngle = getAngle( rightShoulder, rightElbow,rightWrist);
            if( rightShoulder != null && rightWrist != null  && rightElbow != null){

                builder.append("${rightArmAngle.toInt()} 오른쪽 팔 \n")
            }
            if((103<=leftNeckAngle.toInt()|| leftNeckAngle.toInt()<= 109 )
                &&(61<=rightNeckAngle.toInt()|| rightNeckAngle.toInt()<= 67 )
                &&(169<=leftChestAngle.toInt()|| leftChestAngle.toInt()<= 175 )
                &&(155<=rightChestAngle.toInt()|| rightChestAngle.toInt()<= 161 )
                &&(167<=leftLegAngle.toInt()|| leftLegAngle.toInt()<= 173 )
                &&(149<=rightLegAngle.toInt()|| rightLegAngle.toInt()<= 155 )
                &&(35<=leftArmAngle.toInt()|| leftArmAngle.toInt()<= 41 )
                &&(90<=rightArmAngle.toInt()|| rightArmAngle.toInt()<= 96 )){
                Toast.makeText(this@MainActivity, "일치합니다", Toast.LENGTH_SHORT).show()
            }
            //왼쪽 다리 모서리



            if(leftShoulder != null && rightShoulder != null){
                rect_overlay.drawLine(leftShoulder, rightShoulder)
            }

            if(leftHip != null &&  rightHip != null){
                rect_overlay.drawLine(leftHip, rightHip)
            }

            if(leftShoulder != null &&  leftElbow != null){
                rect_overlay.drawLine(leftShoulder, leftElbow)
            }

            if(leftElbow != null &&  leftWrist != null){
                rect_overlay.drawLine(leftElbow, leftWrist)
            }

            if(leftShoulder != null &&  leftHip != null){
                rect_overlay.drawLine(leftShoulder, leftHip)
            }

            if(leftHip != null &&  leftKnee != null){
                rect_overlay.drawLine(leftHip, leftKnee)
            }

            if(leftKnee != null &&  leftAnkle != null){
                rect_overlay.drawLine(leftKnee, leftAnkle)
            }

            if(leftWrist != null &&  leftThumb != null){
                rect_overlay.drawLine(leftWrist, leftThumb)
            }

            if(leftWrist != null &&  leftPinky != null){
                rect_overlay.drawLine(leftWrist, leftPinky)
            }

            if(leftWrist != null &&  leftIndex != null){
                rect_overlay.drawLine(leftWrist, leftIndex)
            }

            if(leftIndex != null &&  leftPinky != null){
                rect_overlay.drawLine(leftIndex, leftPinky)
            }

            if(leftAnkle != null &&  leftHeel != null){
                rect_overlay.drawLine(leftAnkle, leftHeel)
            }

            if(leftHeel != null &&  leftFootIndex != null){
                rect_overlay.drawLine(leftHeel, leftFootIndex)
            }

            if(rightShoulder != null &&  rightElbow != null){
                rect_overlay.drawLine(rightShoulder, rightElbow)
            }

            if(rightElbow != null &&  rightWrist != null){
                rect_overlay.drawLine(rightElbow, rightWrist)
            }

            if(rightShoulder != null &&  rightHip != null){
                rect_overlay.drawLine(rightShoulder, rightHip)
            }

            if(rightHip != null &&  rightKnee != null){
                rect_overlay.drawLine(rightHip, rightKnee)
            }

            if(rightKnee != null &&  rightAnkle != null){
                rect_overlay.drawLine(rightKnee, rightAnkle)
            }

            if(rightWrist != null &&  rightThumb != null){
                rect_overlay.drawLine(rightWrist, rightThumb)
            }

            if(rightWrist != null &&  rightPinky != null){
                rect_overlay.drawLine(rightWrist, rightPinky)
            }

            if(rightWrist != null &&  rightIndex != null){
                rect_overlay.drawLine(rightWrist, rightIndex)
            }

            if(rightIndex != null &&  rightPinky != null){
                rect_overlay.drawLine(rightIndex, rightPinky)
            }

            if(rightAnkle != null &&  rightHeel != null){
                rect_overlay.drawLine(rightAnkle, rightHeel)
            }

            if(rightHeel != null &&  rightFootIndex != null){
                rect_overlay.drawLine(rightHeel, rightFootIndex)
            }


            textView.setText("${builder.toString()}")

        } catch (e: java.lang.Exception) {
            Toast.makeText(this@MainActivity, "Errore", Toast.LENGTH_SHORT).show()
        }
    }



    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                    }


            val imageAnalyzer = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor,PoseAnalyzer(::onTextFound))
                    }

            imageCapture = ImageCapture.Builder()
                    .build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}