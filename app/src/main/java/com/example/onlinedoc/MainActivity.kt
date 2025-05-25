package com.example.onlinedoc

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson // 导入 Gson
import com.google.gson.annotations.SerializedName // 导入 Gson 注解
import okhttp3.* // 导入 OkHttp 相关类
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit // 用于设置 OkHttpClient 超时

class MainActivity : AppCompatActivity() {

    private lateinit var editTextQuestion: EditText
    private lateinit var buttonSend: Button
    private lateinit var textViewAnswer: TextView
    private lateinit var scrollViewAnswer: ScrollView

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS) // 连接超时
        .readTimeout(30, TimeUnit.SECONDS)    // 读取超时
        .writeTimeout(30, TimeUnit.SECONDS)   // 写入超时
        .build()

    // !!! IMPORTANT: Replace with your actual OpenAI API Key !!!
    // For production apps, DO NOT hardcode your API key.
    // Use a backend server or more secure methods.
    private val OPENAI_API_KEY = "" // <--- 替换为你的 API Key

    private val OPENAI_URL = "https://api.siliconflow.cn/v1"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editTextQuestion = findViewById(R.id.editTextQuestion)
        buttonSend = findViewById(R.id.buttonSend)
        textViewAnswer = findViewById(R.id.textViewAnswer)
        scrollViewAnswer = findViewById(R.id.scrollViewAnswer)

        // Initial message
        textViewAnswer.text = "你好！我是AI助手，有什么可以帮助你的吗？\n\n"

        // Check if API Key is set
        if (OPENAI_API_KEY == "sk-YOUR_OPENAI_API_KEY" || OPENAI_API_KEY.isBlank()) {
            Toast.makeText(this, "请在 MainActivity.kt 中设置你的 OpenAI API Key！", Toast.LENGTH_LONG).show()
            buttonSend.isEnabled = false // Disable button if API key is not set
            return
        }

        val sendButtonRef = buttonSend // Create a local immutable reference for the lambda

        sendButtonRef.setOnClickListener {
            val userInput = editTextQuestion.text.toString().trim()

            if (userInput.isEmpty()) {
                Toast.makeText(this, "请输入你的问题", Toast.LENGTH_SHORT).show()
            } else {
                editTextQuestion.text.clear() // Clear input
                appendMessage("你", userInput) // Append user message

                // Send the question to OpenAI
                sendRequestToOpenAI(userInput)
            }
        }
    }

    private fun sendRequestToOpenAI(question: String) {
        appendMessage("AI", "思考中...") // 显示加载状态

        val requestBody = ChatCompletionRequest(
            model = "Qwen/Qwen2.5-72B-Instruct",
            messages = listOf(
                Message(role = "system", content = "你是一个乐于助人的助手。"),
                Message(role = "user", content = question)
            ),
            max_tokens = 150 // 限制回答的最大长度
        )

        val jsonRequestBody = Gson().toJson(requestBody)

        val request = Request.Builder()
            .url(OPENAI_URL)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
            .post(jsonRequestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("OpenAI", "OpenAI API request failed: ${e.message}", e)
                runOnUiThread {
                    // Update the last "思考中..." message to an error
                    replaceLastMessage("AI", "请求失败: ${e.message}")
                    Toast.makeText(this@MainActivity, "网络或API请求失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        Log.e("OpenAI", "OpenAI API request failed: ${response.code} - $errorBody")
                        runOnUiThread {
                            replaceLastMessage("AI", "AI 回答错误: ${response.code} - ${errorBody ?: "未知错误"}")
                            Toast.makeText(this@MainActivity, "AI 回答错误: ${response.code}", Toast.LENGTH_LONG).show()
                        }
                        return
                    }

                    val responseBody = response.body?.string()
                    Log.d("OpenAI", "OpenAI API response: $responseBody")

                    val apiResponse = Gson().fromJson(responseBody, ChatCompletionResponse::class.java)
                    val aiMessage = apiResponse.choices.firstOrNull()?.message?.content ?: "未能获取到AI回答。"

                    runOnUiThread {
                        // Replace the "思考中..." message with the actual AI response
                        replaceLastMessage("AI", aiMessage)
                    }
                }
            }
        })
    }

    // Helper function to append messages to the TextView
    private fun appendMessage(sender: String, message: String) {
        runOnUiThread {
            val currentText = textViewAnswer.text.toString()
            if (currentText.isBlank() || currentText == "你好！我是AI助手，有什么可以帮助你的吗？\n\n") {
                textViewAnswer.text = "$sender: $message\n\n"
            } else {
                textViewAnswer.append("$sender: $message\n\n")
            }
            scrollToBottom()
        }
    }

    // Helper function to replace the last message (e.g., "思考中..." with actual answer)
    private fun replaceLastMessage(sender: String, newMessage: String) {
        runOnUiThread {
            val currentText = textViewAnswer.text.toString()
            val lines = currentText.split("\n\n").toMutableList() // Split by two newlines
            if (lines.isNotEmpty()) {
                // Find the line that starts with "AI: 思考中..." (or similar) and update it
                val lastMessageIndex = lines.indexOfLast { it.startsWith("$sender: ") }
                if (lastMessageIndex != -1) {
                    // Replace only the message part, keep the sender prefix
                    val existingPrefix = lines[lastMessageIndex].substringBefore(" ")
                    lines[lastMessageIndex] = "$existingPrefix $newMessage"
                } else {
                    // If not found, just append (shouldn't happen if "思考中" is there)
                    lines.add("$sender: $newMessage")
                }
            } else {
                lines.add("$sender: $newMessage")
            }
            textViewAnswer.text = lines.joinToString("\n\n") // Reconstruct with newlines
            scrollToBottom()
        }
    }


    // Helper function to scroll to the bottom of the ScrollView
    private fun scrollToBottom() {
        scrollViewAnswer.post {
            scrollViewAnswer.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    // --- Data Classes for OpenAI API Request/Response (using Gson) ---

    // Request Body
    data class ChatCompletionRequest(
        val model: String,
        val messages: List<Message>,
        @SerializedName("max_tokens") val max_tokens: Int? = null,
        val temperature: Double? = null
    )

    data class Message(
        val role: String, // e.g., "system", "user", "assistant"
        val content: String
    )

    // Response Body
    data class ChatCompletionResponse(
        val id: String,
        val `object`: String,
        val created: Long,
        val model: String,
        val choices: List<Choice>,
        val usage: Usage
    )

    data class Choice(
        val index: Int,
        val message: Message,
        @SerializedName("finish_reason") val finishReason: String
    )

    data class Usage(
        @SerializedName("prompt_tokens") val promptTokens: Int,
        @SerializedName("completion_tokens") val completionTokens: Int,
        @SerializedName("total_tokens") val totalTokens: Int
    )
}