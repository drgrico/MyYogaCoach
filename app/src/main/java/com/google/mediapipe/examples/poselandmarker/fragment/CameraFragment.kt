/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.poselandmarker.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Camera
import androidx.camera.core.AspectRatio
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.examples.poselandmarker.MainViewModel
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentCameraBinding
import com.google.mediapipe.examples.poselandmarker.fragment.utils.ChatGPT
import com.google.mediapipe.examples.poselandmarker.fragment.utils.ChatGPT.isPlayingAdvice
import com.google.mediapipe.examples.poselandmarker.fragment.utils.PoseList
import com.google.mediapipe.examples.poselandmarker.fragment.utils.Poses
import com.google.mediapipe.examples.poselandmarker.fragment.utils.Poses.filterResult
import com.google.mediapipe.examples.poselandmarker.fragment.utils.Poses.getPoseName
import com.google.mediapipe.examples.poselandmarker.fragment.utils.Poses.isUserStatic
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlinx.coroutines.runBlocking
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {

    companion object {
        private const val TAG = "CameraFragment"
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    //pose detection and camera
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    //buttons
    private lateinit var cameraButton: Button
//    private lateinit var startButton: Button
    private lateinit var poseButton: Button
    private var poseSelected: String = ""
    private var cntDetection: Int = 0
    private lateinit var poseRes1: List<NormalizedLandmark>
    private lateinit var poseRes2: List<NormalizedLandmark>
    private var threshold: Float = 0.3F
    private lateinit var instructionsTextView: TextView
    private lateinit var poseNameButton: Button

    //countdown events
    private lateinit var countdownTextView: TextView
    private lateinit var countdownTimer: CountDownTimer
    private var isTimerActive = false

    private val startSecondActivityForResult = registerForActivityResult<Intent, ActivityResult>(
        ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            //obtain data from the second activity
            val data = result.data!!.getStringExtra("selectedItemData")

            //use the data
            if (data != null) {
                Toast.makeText(requireContext(), "Selected pose: ${getPoseName(data.toInt()).uppercase()}!", Toast.LENGTH_SHORT).show()
                poseSelected = data
                poseNameButton.text = getPoseName(data.toInt())
                poseNameButton.visibility = View.VISIBLE

                //start the countdown
                if (isTimerActive) {
                    countdownTimer.cancel()
                }
                startCountdown(7000, 1000)
            }
        }
    }

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }

        // Start the PoseLandmarkerHelper again when users come back
        // to the foreground.
        backgroundExecutor.execute {
            if(this::poseLandmarkerHelper.isInitialized) {
                if (poseLandmarkerHelper.isClose()) {
                    poseLandmarkerHelper.setupPoseLandmarker()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if(this::poseLandmarkerHelper.isInitialized) {
            viewModel.setMinPoseDetectionConfidence(poseLandmarkerHelper.minPoseDetectionConfidence)
            viewModel.setMinPoseTrackingConfidence(poseLandmarkerHelper.minPoseTrackingConfidence)
            viewModel.setMinPosePresenceConfidence(poseLandmarkerHelper.minPosePresenceConfidence)
            viewModel.setDelegate(poseLandmarkerHelper.currentDelegate)

            // Close the PoseLandmarkerHelper and release resources
            backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        ChatGPT.createChatGPTInstance(this.requireContext())
        Poses.loadCorrectLandmarks(this.requireContext())

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        cameraButton = view.findViewById<Button>(R.id.camera_button)
        cameraButton.setOnClickListener(View.OnClickListener {
            Log.d(TAG, "Camera button clicked")
            if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
                cameraFacing = CameraSelector.LENS_FACING_FRONT
                cameraButton.text = "Back"
            } else {
                cameraFacing = CameraSelector.LENS_FACING_BACK
                cameraButton.text ="Front"
            }
            bindCameraUseCases()
        })

//        startButton = view.findViewById(R.id.start_button)
//        startButton.setOnClickListener {
//
//        }

        instructionsTextView = view.findViewById(R.id.instructions_tv)
        poseNameButton = view.findViewById(R.id.posename_button)
        countdownTextView = view.findViewById(R.id.countdown_tv)
        countdownTextView.text = "Select a pose!"

        poseButton = view.findViewById(R.id.pose_button)
        poseButton.setOnClickListener{
            Log.d(TAG, "Pose button clicked")
            val intent = Intent(requireContext(), PoseList::class.java)
            startSecondActivityForResult.launch(intent)
        }

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        // Create the PoseLandmarkerHelper that will handle the inference
        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                currentDelegate = viewModel.currentDelegate,
                poseLandmarkerHelperListener = this
            )
        }

        // Attach listeners to UI control widgets
        initBottomSheetControls()
    }

    private fun startCountdown(millisTot: Long, millisInterval: Long) {
        countdownTimer = object : CountDownTimer(millisTot, millisInterval) {
            override fun onTick(millisUntilFinished: Long) {
                // update UI with remaining time
                val secondsRemaining = millisUntilFinished / 1000 + 1
                if (secondsRemaining == millisTot/1000 || secondsRemaining == millisTot/1000 - 1) {
                    countdownTextView.text = "GET READY!"
                    isTimerActive = true

                    instructionsTextView.text = ""
                }
                else {
                    countdownTextView.text = "$secondsRemaining"
                }
            }

            override fun onFinish() {
                countdownTextView.text = "START!"
                isTimerActive = false
                cntDetection = 0

                object : CountDownTimer(1000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {}

                    override fun onFinish() {
                        countdownTextView.text = ""
                        instructionsTextView.text = "Pose!"
                    }
                }.start()
            }
        }.start()
    }

    private fun initBottomSheetControls() {
        // init bottom sheet settings

//        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
//            String.format(
//                Locale.US, "%.2f", viewModel.currentMinPoseDetectionConfidence
//            )
//        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
//            String.format(
//                Locale.US, "%.2f", viewModel.currentMinPoseTrackingConfidence
//            )
//        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
//            String.format(
//                Locale.US, "%.2f", viewModel.currentMinPosePresenceConfidence
//            )
//
//        // When clicked, lower pose detection score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
//            if (poseLandmarkerHelper.minPoseDetectionConfidence >= 0.2) {
//                poseLandmarkerHelper.minPoseDetectionConfidence -= 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, raise pose detection score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
//            if (poseLandmarkerHelper.minPoseDetectionConfidence <= 0.8) {
//                poseLandmarkerHelper.minPoseDetectionConfidence += 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, lower pose tracking score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
//            if (poseLandmarkerHelper.minPoseTrackingConfidence >= 0.2) {
//                poseLandmarkerHelper.minPoseTrackingConfidence -= 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, raise pose tracking score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
//            if (poseLandmarkerHelper.minPoseTrackingConfidence <= 0.8) {
//                poseLandmarkerHelper.minPoseTrackingConfidence += 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, lower pose presence score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
//            if (poseLandmarkerHelper.minPosePresenceConfidence >= 0.2) {
//                poseLandmarkerHelper.minPosePresenceConfidence -= 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, raise pose presence score threshold floor
//        fragmentCameraBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
//            if (poseLandmarkerHelper.minPosePresenceConfidence <= 0.8) {
//                poseLandmarkerHelper.minPosePresenceConfidence += 0.1f
//                updateControlsUi()
//            }
//        }
//
//        // When clicked, change the underlying hardware used for inference.
//        // Current options are CPU and GPU
//        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
//            viewModel.currentDelegate, false
//        )
//        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
//            object : AdapterView.OnItemSelectedListener {
//                override fun onItemSelected(
//                    p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long
//                ) {
//                    try {
//                        poseLandmarkerHelper.currentDelegate = p2
//                        updateControlsUi()
//                    } catch(e: UninitializedPropertyAccessException) {
//                        Log.e(TAG, "PoseLandmarkerHelper has not been initialized yet.")
//                    }
//                }
//
//                override fun onNothingSelected(p0: AdapterView<*>?) {
//                    /* no op */
//                }
//            }

        // When clicked, change the underlying model used for object detection
//        fragmentCameraBinding.bottomSheetLayout.spinnerModel.setSelection(
//            viewModel.currentModel,
//            false
//        )
//        fragmentCameraBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
//            object : AdapterView.OnItemSelectedListener {
//                override fun onItemSelected(
//                    p0: AdapterView<*>?,
//                    p1: View?,
//                    p2: Int,
//                    p3: Long
//                ) {
//                    poseLandmarkerHelper.currentModel = p2
//                    updateControlsUi()
//                }
//
//                override fun onNothingSelected(p0: AdapterView<*>?) {
//                    /* no op */
//                }
//            }
    }

    // Update the values displayed in the bottom sheet. Reset Poselandmarker
    // helper.
    private fun updateControlsUi() {
        if(this::poseLandmarkerHelper.isInitialized) {
//            fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
//                String.format(
//                    Locale.US,
//                    "%.2f",
//                    poseLandmarkerHelper.minPoseDetectionConfidence
//                )
//            fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
//                String.format(
//                    Locale.US,
//                    "%.2f",
//                    poseLandmarkerHelper.minPoseTrackingConfidence
//                )
//            fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
//                String.format(
//                    Locale.US,
//                    "%.2f",
//                    poseLandmarkerHelper.minPosePresenceConfidence
//                )

            // Needs to be cleared instead of reinitialized because the GPU
            // delegate needs to be initialized on the thread using it when applicable
            backgroundExecutor.execute {
                poseLandmarkerHelper.clearPoseLandmarker()
                poseLandmarkerHelper.setupPoseLandmarker()
            }
            fragmentCameraBinding.overlay.clear()
        }
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        detectPose(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectPose(imageProxy: ImageProxy) {
        if(this::poseLandmarkerHelper.isInitialized) {
            poseLandmarkerHelper.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after pose have been detected. Extracts original
    // image height/width to scale and place the landmarks properly through
    // OverlayView
    override fun onResults(
        resultBundle: PoseLandmarkerHelper.ResultBundle
    ) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
//                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
//                    String.format("%d ms", resultBundle.inferenceTime)

                // Pass necessary information to OverlayView for drawing on the canvas
                fragmentCameraBinding.overlay.setResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )

                if (resultBundle.results.first().landmarks().flatten().isNotEmpty() && poseSelected.isNotEmpty() && !isTimerActive && !isPlayingAdvice()) {
                    cntDetection += 1
                    if (cntDetection % 25 == 0) { //to take the value every second
                        val filteredRes = filterResult(resultBundle)
//                        Log.d(TAG, filteredRes.toString())
//                        Log.d(TAG, "Check x, y, z: ${filteredRes[0].x()}, ${filteredRes[0].y()}, ${filteredRes[0].z()}")
                        Log.d(TAG, "Result bundle filtered: ${filteredRes.size}")

                        if (cntDetection == 25) {
                            poseRes1 = filteredRes
                        }
                        else if (cntDetection == 50) {
                            poseRes2 = filteredRes

                            instructionsTextView.text = "Analysis..."

                            if (isUserStatic(poseRes1, poseRes2, threshold)) {
                                Log.d(TAG, "Sending request to ChatGPT")
                                val ctx = this.requireContext()
                                runBlocking {
                                    ChatGPT.requestYogaAdvice(ctx, poseSelected, poseRes2.toString())
                                }
                            }

                            instructionsTextView.text = "Pose!"

                            cntDetection = 0
                        }
                    }

                }


                // Force a redraw
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
//            if (errorCode == PoseLandmarkerHelper.GPU_ERROR) {
//                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
//                    PoseLandmarkerHelper.DELEGATE_CPU, false
//                )
//            }
        }
    }
}
