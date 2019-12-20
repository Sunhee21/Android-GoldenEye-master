package co.infinum.example

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.util.Log
import android.view.*
import android.widget.Toast
import co.infinum.goldeneye.GoldenEye
import co.infinum.goldeneye.InitCallback
import co.infinum.goldeneye.Logger
import co.infinum.goldeneye.config.CameraConfig
import co.infinum.goldeneye.config.CameraInfo
import co.infinum.goldeneye.utils.GoldenEyeScaleUtils
import kotlinx.android.synthetic.main.fragment_camera_shoot.*
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * @intro
 * @author sunhee
 * @date 2019/12/14
 */
class CameraShootFragment : Fragment() {
    companion object {

        fun newInstance() = CameraShootFragment()

        const val MAX_DURATION = 15;

        const val HDANDLER_DELAY = MAX_DURATION * 10;
    }

    private var previewMediaPlayer: MediaPlayer?=null
    private var stopAnimSet: AnimatorSet? = null
    private var startAnimSet: AnimatorSet? = null
    private val initCallback = object : InitCallback() {
        override fun onReady(config: CameraConfig) {
//            zoomView.text = "Zoom: ${config.zoom.toPercentage()}"
        }

        override fun onError(t: Throwable) {
            t.printStackTrace()
        }
    }


    private var isCancel: Boolean = false

    val TAG = "CameraShootFragment"

    private var mProgress: Int = 0
    private lateinit var goldenEye: GoldenEye

    private var isTimeout = false
    private lateinit var videoFile: File
    private val logger = object : Logger {
        override fun log(message: String) {
            Log.e("GoldenEye", message)
        }

        override fun log(t: Throwable) {
            t.printStackTrace()
        }
    }


    private var handler: Handler = @SuppressLint("HandlerLeak")
    object : Handler() {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                0 -> {
                    if (mProgress <= 100) {
                        main_progress_bar.setProgress(mProgress)
                    }
                    mProgress++
                    if (mProgress <= 100)
                        sendMessageDelayed(this.obtainMessage(0), HDANDLER_DELAY.toLong())

                }
                2 -> {
                    startRecordAnim()
                }
            }
        }


    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_camera_shoot, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViewClick()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        initGoldenEye()
        videoFile = File.createTempFile("vid", "")
    }

    private fun initGoldenEye() {
        goldenEye = GoldenEye.Builder(activity!!)
                .setLogger(logger)
                .setOnZoomChangedCallback {
                    //                    zoomView.text = "Zoom: ${it.toPercentage()}"
                }
                .build()
    }

    private var mDownTime = 0L


    private fun initViewClick() {
        main_progress_bar.setOnProgressEndListener {
            isTimeout = true
            stopRecord()
        }
        main_press_control.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isTimeout = false
                    isCancel = false
                    tv_info.text = ""
                    mDownTime = System.currentTimeMillis()
                    handler.sendEmptyMessageDelayed(2, 400L)
//                    goldenEye.stopRecording()
                    true
                }
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_UP -> {
                    if (!isTimeout) {
                        tv_info.text = "轻触拍照，按住摄像"
                        handler.removeMessages(2)
                        if (isRecording) {
                            if (isCancel) Toast.makeText(context, "撤销", Toast.LENGTH_SHORT).show()
                            stopRecord()
                        } else {
                            takePicture()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val rect = Rect()
                    if (main_press_control.getGlobalVisibleRect(rect)) {
                        isCancel = !rect.contains(event.rawX.toInt(), event.rawY.toInt())
                    }
                    true
                }
                else -> false
            }

        }
        btn_switch.setOnClickListener {
            switchCamera()
        }
        btn_close.setOnClickListener {
            activity?.finish()
        }

        view_send.backLayout.setOnClickListener {
            previewVideoContainer.visibility = View.GONE
            previewPictureView.visibility = View.GONE
            view_send.stopAnim()
            record_layout.visibility = View.VISIBLE
            main_progress_bar.setProgress(0)
            tv_info.visibility = View.VISIBLE
            previewMediaPlayer?.release()
            previewMediaPlayer = null
        }
        view_send.selectLayout.setOnClickListener {
            //选择
        }
    }

    /**
     * 拍照
     */
    private fun takePicture() {
        val (scaleX, scaleY, scale) = GoldenEyeScaleUtils.getScale(activity!!,textureView,goldenEye.config!!)

        val originBitmapWidth = (textureView.width * scale).toInt()
        val originBitmapHeight = textureView.height//原图尺寸

        val bitmap = textureView.getBitmap(originBitmapWidth,originBitmapHeight)//textureView取得原图宽高的bitmap


        val dx = (originBitmapWidth - textureView.width)/2
        val outputBitmap = ImageUtils.clip(bitmap,dx,0,textureView.width,textureView.height)//从原图中裁出预览的部分
        Log.d(TAG, "${outputBitmap.width}     ${outputBitmap.height}     ");

//        if (bitmap.width <= 4096 && bitmap.height <= 4096) {
            displayPicture(outputBitmap)
//        } else {
//            reducePictureSize(bitmap)
//        }
//        goldenEye.takePicture(
//                onPictureTaken = { bitmap ->
//                    Log.d(TAG, "${System.currentTimeMillis() - noew}: *----------------");
//
//                },
//                onError = { it.printStackTrace() })
    }

    private fun reducePictureSize(bitmap: Bitmap) {
        Executors.newSingleThreadExecutor().execute {
            try {
                val scaleX = 4096f / bitmap.width
                val scaleY = 4096f / bitmap.height
                val scale = min(scaleX, scaleY)
                val newBitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                handler.post {
                    displayPicture(newBitmap)
                }
            } catch (t: Throwable) {
//                toast("Picture is too big. Reduce picture size in settings below 4096x4096.")
            }
        }
    }

    private fun displayPicture(bitmap: Bitmap) {
        showSend()
        previewPictureView.apply {
            setImageBitmap(bitmap)
            visibility = View.VISIBLE
        }
    }


    private fun record() {
        isRecording = true
        goldenEye.startRecording(
                file = videoFile,
                onVideoRecorded = {
                    if (!isCancel){
                        previewVideoContainer.visibility = View.VISIBLE
                        if (previewVideoView.isAvailable) {

                            startVideo()
                        } else {
                            previewVideoView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {}
                                override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {}
                                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?) = true

                                override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
                                    startVideo()
                                }
                            }
                        }
                    }
                },
                onError = { it.printStackTrace() }
        )
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 0x1) {
            if (grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {
                goldenEye.open(textureView, goldenEye.availableCameras[0], initCallback)
            } else {
                Toast.makeText(context!!, "缺少权限", Toast.LENGTH_SHORT).show()
//                AlertDialog.Builder(context!!)
//                        .setTitle("")
//                        .setMessage("Smartass Detected!")
//                        .setPositiveButton("I am smartass") { _, _ ->
//                            throw SmartassException
//                        }
//                        .setNegativeButton("Sorry") { _, _ ->
//                            openCamera(goldenEye.availableCameras[0])
//                        }
//                        .setCancelable(false)
//                        .show()
            }
        } else if (requestCode == 0x2) {
            record()
        }
    }


    /**
     * 录像
     */
    fun startRecord() {
        if (ActivityCompat.checkSelfPermission(context!!, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            record()
        } else {
            ActivityCompat.requestPermissions(activity!!, arrayOf(Manifest.permission.RECORD_AUDIO), 0x2)
        }
    }


    /**
     * stop录像
     */
    private fun stopRecord() {
        isRecording = false
        goldenEye.stopRecording()
        stopAnim(Runnable {})
    }

    /**
     * 拍摄完播放预览
     */
    private fun startVideo() {
        showSend()
        previewMediaPlayer = MediaPlayer().apply {
            setSurface(Surface(previewVideoView.surfaceTexture))
            setDataSource(videoFile.absolutePath)
            isLooping = true
            setOnCompletionListener {

            }
            setOnVideoSizeChangedListener { _, width, height ->
                previewVideoView.apply {
                    layoutParams = layoutParams.apply {
                        val scaleX = previewVideoContainer.width / width.toFloat()
                        val scaleY = previewVideoContainer.height / height.toFloat()
                        val scale = min(scaleX, scaleY)

                        this.width = (width * scale).toInt()
                        this.height = (height * scale).toInt()
                    }
                }
            }
            prepare()
            start()

        }
    }

    override fun onStart() {
        super.onStart()
        if (goldenEye.availableCameras.isNotEmpty()) {
            openCamera(goldenEye.availableCameras[0])
        }
    }

    override fun onStop() {
        super.onStop()
        goldenEye.release()
    }


    /**
     * 切换摄像头
     */
    private fun switchCamera() {
        val currentIndex = goldenEye.availableCameras.indexOfFirst { goldenEye.config?.id == it.id }
        val nextIndex = (currentIndex + 1) % goldenEye.availableCameras.size
        openCamera(goldenEye.availableCameras[nextIndex])
    }

    private var isRecording = false


    /**
     * 录制按钮的放大动画
     */
    private fun startRecordAnim() {
        startAnimSet = AnimatorSet()
        startAnimSet!!.playTogether(
                ObjectAnimator.ofFloat(main_press_control, "scaleX", 1f, 0.5f),
                ObjectAnimator.ofFloat(main_press_control, "scaleY", 1f, 0.5f),
                ObjectAnimator.ofFloat(main_progress_bar, "scaleX", 1f, 1.3f),
                ObjectAnimator.ofFloat(main_progress_bar, "scaleY", 1f, 1.3f)
        )
        startAnimSet!!.setDuration(250).start()
        startAnimSet!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                mProgress = 0
                main_progress_bar.setProgress(mProgress)
                handler.removeMessages(0)
                handler.sendMessage(handler.obtainMessage(0))
                startRecord()
            }
        })
    }

    /**
     * 录制按钮的缩小动画
     */
    private fun stopAnim(runnable: Runnable) {
        mProgress = 0
        main_progress_bar.setProgress(mProgress)
        handler.removeMessages(0)

        stopAnimSet = AnimatorSet()
        stopAnimSet!!.playTogether(
                ObjectAnimator.ofFloat(main_press_control, "scaleX", 0.5f, 1f),
                ObjectAnimator.ofFloat(main_press_control, "scaleY", 0.5f, 1f),
                ObjectAnimator.ofFloat(main_progress_bar, "scaleX", 1.3f, 1f),
                ObjectAnimator.ofFloat(main_progress_bar, "scaleY", 1.3f, 1f)
        )
        stopAnimSet!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                mProgress = 0
                main_progress_bar.setProgress(mProgress)
                handler.removeMessages(0)
                handler.post(runnable)
            }
        })
        stopAnimSet!!.setDuration(250).start()
    }


    private fun openCamera(cameraInfo: CameraInfo) {
        if (ActivityCompat.checkSelfPermission(context!!, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            goldenEye.open(textureView, cameraInfo, initCallback)
        } else {
            ActivityCompat.requestPermissions(activity!!, arrayOf(Manifest.permission.CAMERA), 0x1)
        }
    }


    /**
     * 完成拍摄时才执行
     */
    private fun showSend() {
        record_layout.visibility = View.GONE
        view_send.startAnim()
        tv_info.visibility = View.GONE
    }
}