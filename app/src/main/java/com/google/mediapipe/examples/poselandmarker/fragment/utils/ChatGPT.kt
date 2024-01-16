package com.google.mediapipe.examples.poselandmarker.fragment.utils

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.aallam.openai.api.audio.SpeechRequest
import com.aallam.openai.api.audio.Voice
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader


object ChatGPT {
    private const val TAG = "ChatGPT"

    private lateinit var chatGPTInstance: OpenAI
    private lateinit var prompt: String
    private val mediaPlayer = MediaPlayer()

    fun createChatGPTInstance(ctx: Context) {
        var apiKey = ""

        try {
            val inputStream = ctx.assets.open("api_key.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))

            apiKey = reader.readLine()

            reader.close()
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Log.d(TAG, apiKey)

        prompt = getPrompt(ctx)
        Log.d(TAG, prompt)


        chatGPTInstance = OpenAI(
            token = apiKey
        )
    }

    suspend fun requestYogaAdvice(ctx: Context, pose: String, poseDetection: String) {
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId("gpt-3.5-turbo"),
            temperature = 2.0,
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = prompt
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = pose + "\n" + poseDetection
                )
            )
        )

        var advice = chatGPTInstance.chatCompletion(chatCompletionRequest).choices[0].message.messageContent.toString().split("=")[1].split(")")[0]
        Log.d(TAG, advice)

        val rawAudio = chatGPTInstance.speech(
            request = SpeechRequest(
                model = ModelId("tts-1"),
                input = advice,
                voice = Voice.Alloy,
            )
        )

        playMp3(ctx, rawAudio)
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
            val tempMp3: File = File.createTempFile("kurchina", "mp3", ctx.cacheDir)
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