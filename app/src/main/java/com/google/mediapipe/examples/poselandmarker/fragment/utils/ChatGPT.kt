package com.google.mediapipe.examples.poselandmarker.fragment.utils

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.assistant.AssistantRequest
import com.aallam.openai.api.assistant.AssistantTool
import com.aallam.openai.api.audio.SpeechRequest
import com.aallam.openai.api.audio.Voice
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.core.Role
import com.aallam.openai.api.core.Status
import com.aallam.openai.api.exception.OpenAITimeoutException
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.message.MessageContent
import com.aallam.openai.api.message.MessageRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.api.run.RunRequest
import com.aallam.openai.client.OpenAI
import com.google.mediapipe.examples.poselandmarker.fragment.utils.Poses.getCorrectLandmarks
import com.google.mediapipe.examples.poselandmarker.fragment.utils.Poses.getPoseName
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import kotlin.time.Duration


object ChatGPT {
    private const val TAG = "ChatGPT"

    private lateinit var chatGPTInstance: OpenAI
    private lateinit var prompt: String
    private lateinit var apiKey: String
    private val mediaPlayer = MediaPlayer()

    fun createChatGPTInstance(ctx: Context) {
        var api = ""

        try {
            val inputStream = ctx.assets.open("api_key.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))

            api = reader.readLine()

            reader.close()
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Log.d(TAG, api)
        apiKey = api

        prompt = getPrompt(ctx)
        Log.d(TAG, prompt)
    }

    @OptIn(BetaOpenAI::class)
    suspend fun requestYogaAdvice(ctx: Context, pose: String, poseDetection: String) {
        val maxRetry = 3  // Decide a reasonable retry count.
        var currentRetry = 0

        while (currentRetry < maxRetry) {
            try {
                chatGPTInstance = OpenAI(
                    token = apiKey,
                    timeout = Timeout(Duration.INFINITE)
                )
                Log.d(TAG, "Initializing")

                val correct = getCorrectLandmarks(pose.toInt()).split("world")[0]
                val request = correct + "\nPose performed by the user: \n" + poseDetection.split("world")[0]
                Log.d(TAG, "Correct pose $correct")
                Log.d(TAG, "Sending request: $request")

                val assistant = chatGPTInstance.assistant(
                    request = AssistantRequest(
                        name = "Yoga Coach",
                        instructions = prompt,
                        tools = listOf(AssistantTool.CodeInterpreter),
                        model = ModelId("gpt-4-1106-preview")
                    )
                )

                val thread = chatGPTInstance.thread()
                chatGPTInstance.message(
                    threadId = thread.id,
                    request = MessageRequest(
                        role = Role.User,
                        content = request
                    )
                )

                val run = chatGPTInstance.createRun(
                    thread.id,
                    request = RunRequest(
                        assistantId = assistant.id,
                        instructions = "Please compare the two poses and return an advice in MAX 15 words, behaving like a coach",
                    )
                )

                do {
                    delay(500)
                    val retrievedRun = chatGPTInstance.getRun(threadId = thread.id, runId = run.id)
                } while (retrievedRun.status != Status.Completed)

                val assistantMessages = chatGPTInstance.messages(thread.id)
                var advice = ""
//                for (message in assistantMessages) {
                    val textContent = assistantMessages[0].content.first() as? MessageContent.Text ?: error("Expected MessageContent.Text")
                    Log.d(TAG, "Assistant: ${textContent.text.value}")
                    advice = textContent.text.value
//                }

//                val chatCompletionRequest = ChatCompletionRequest(
//                    model = ModelId("gpt-3.5-turbo-1106"), //gpt-4-1106-preview
//                    temperature = 2.0,
//                    messages = listOf(
//                        ChatMessage(
//                            role = ChatRole.System,
//                            content = prompt
//                        ),
//                        ChatMessage(
//                            role = ChatRole.User,
//                            content = request
//                        )
//                    )
//                )
//
//                val advice = chatGPTInstance.chatCompletion(chatCompletionRequest).choices[0].message.messageContent.toString().split("=")[1].split(")")[0]
//                Log.d(TAG, advice)

                // Regex pattern that matches anything that's NOT a-z, A-Z, '.', '?' and ' ' (space).
                val pattern = "[^a-zA-Z\\.\\?\\s\\'\\,]".toRegex()

                // Get the first mismatch index
                val mismatchIndex = advice.indexOfFirst { it.toString().matches(pattern) }

                val processedAdvice = if (mismatchIndex != -1) advice.substring(0, mismatchIndex) else advice

                Log.d(TAG, "Processed advice: $processedAdvice")

                val rawAudio = chatGPTInstance.speech(
                    request = SpeechRequest(
                        model = ModelId("tts-1"),
                        input = processedAdvice,
                        voice = Voice.Alloy,
                    )
                )

                playMp3(ctx, rawAudio)
                chatGPTInstance.close()
                return
            } catch (e: OpenAITimeoutException) {
                Log.d(TAG, "OpenAITimeoutException occurred. Retrying...")
                currentRetry += 1
            } catch (e: Exception) {
                Log.d(TAG, "An unexpected error occurred: ${e.message}")

                // Break the loop since this isn't a TimeoutException that might be solved by just retrying.
                break
            }
        }
    }


    private fun getPrompt(ctx: Context): String {
        val prompt = StringBuilder()

        try {
            val inputStream = ctx.assets.open("prompt.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                prompt.append(line).append("\n")
            }

            reader.close()
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return prompt.toString()
    }


    private fun playMp3(ctx: Context, mp3SoundByteArray: ByteArray) {
        try {
            // create temp file that will hold byte array
            val tempMp3: File = File.createTempFile("speech", "mp3", ctx.cacheDir)
            tempMp3.deleteOnExit()
            val fos: FileOutputStream = FileOutputStream(tempMp3)
            fos.write(mp3SoundByteArray)
            fos.close()

            // resetting mediaplayer instance to evade problems
            mediaPlayer.reset()

            // In case you run into issues with threading consider new instance like:
            // MediaPlayer mediaPlayer = new MediaPlayer();

            // Tried passing path directly, but kept getting
            // "Prepare failed.: status=0x1"
            // so using file descriptor instead
            val fis: FileInputStream = FileInputStream(tempMp3)
            mediaPlayer.setDataSource(fis.fd)
            mediaPlayer.prepare()
            mediaPlayer.start()
        } catch (ex: IOException) {
            val s = ex.toString()
            ex.printStackTrace()
        }
    }
}