package com.voconexus.app.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class ProviderCategory(val displayName: String) {
    LOCAL_ENGINE("Local Engine"),
    CLOUD_TTS("Cloud Voice / TTS"),
    TRANSLATOR_LLM("LLM & Translation"),
    CUSTOM("Custom Endpoint")
}

data class ProviderMetadata(
    val id: String,
    val name: String,
    val category: ProviderCategory,
    val description: String,
    val requiresKey: Boolean = true,
    val defaultBaseUrl: String = "",
    val websiteUrl: String = ""
)

data class CustomEndpointConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val authHeaderName: String = "Authorization",
    val engineType: String = "CLOUD_TTS",
    val createdAt: Long = System.currentTimeMillis()
)

class ApiVaultManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
    }

    private val _configuredProvidersFlow = MutableStateFlow<Set<String>>(emptySet())
    val configuredProvidersFlow: StateFlow<Set<String>> = _configuredProvidersFlow.asStateFlow()

    private val _customEndpointsFlow = MutableStateFlow<List<CustomEndpointConfig>>(emptyList())
    val customEndpointsFlow: StateFlow<List<CustomEndpointConfig>> = _customEndpointsFlow.asStateFlow()

    init {
        refreshVaultState()
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val builder = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                keyGenerator.init(builder.build())
                keyGenerator.generateKey()
            } else {
                (keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        val secretKey = getSecretKey() ?: return plainText
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            "$ivBase64:$encryptedBase64"
        } catch (_: Throwable) {
            plainText
        }
    }

    private fun decrypt(encryptedString: String): String {
        if (encryptedString.isBlank()) return ""
        val parts = encryptedString.split(":")
        if (parts.size != 2) return encryptedString
        val secretKey = getSecretKey() ?: return encryptedString
        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Throwable) {
            encryptedString
        }
    }

    private fun refreshVaultState() {
        val configured = BUILT_IN_PROVIDERS.filter { provider ->
            !provider.requiresKey || getApiKey(provider.id).isNotBlank()
        }.map { it.id }.toSet()

        _configuredProvidersFlow.value = configured
        _customEndpointsFlow.value = getCustomEndpoints()
    }

    fun saveApiKey(providerId: String, apiKey: String) {
        saveProviderConfig(providerId, apiKey)
    }

    fun saveProviderConfig(
        providerId: String,
        apiKey: String,
        customBaseUrl: String = "",
        customModelId: String = ""
    ) {
        val encryptedKey = encrypt(apiKey.trim())
        prefs.edit()
            .putString(KEY_PREFIX_API_KEY + providerId, encryptedKey)
            .putString(KEY_PREFIX_BASE_URL + providerId, customBaseUrl.trim())
            .putString(KEY_PREFIX_MODEL_ID + providerId, customModelId.trim())
            .apply()
        refreshVaultState()
    }

    fun getApiKey(providerId: String): String {
        val stored = prefs.getString(KEY_PREFIX_API_KEY + providerId, "") ?: ""
        return decrypt(stored)
    }

    fun getCustomBaseUrl(providerId: String): String {
        return prefs.getString(KEY_PREFIX_BASE_URL + providerId, "") ?: ""
    }

    fun getCustomModelId(providerId: String): String {
        return prefs.getString(KEY_PREFIX_MODEL_ID + providerId, "") ?: ""
    }

    fun hasApiKey(providerId: String): Boolean {
        val provider = BUILT_IN_PROVIDERS.find { it.id == providerId }
        if (provider != null && !provider.requiresKey) {
            return getCustomBaseUrl(providerId).isNotBlank() || getCustomModelId(providerId).isNotBlank() || getApiKey(providerId).isNotBlank()
        }
        return getApiKey(providerId).isNotBlank()
    }

    fun deleteApiKey(providerId: String) {
        deleteProviderConfig(providerId)
    }

    fun deleteProviderConfig(providerId: String) {
        prefs.edit()
            .remove(KEY_PREFIX_API_KEY + providerId)
            .remove(KEY_PREFIX_BASE_URL + providerId)
            .remove(KEY_PREFIX_MODEL_ID + providerId)
            .apply()
        refreshVaultState()
    }

    // --- Custom Endpoints Management ---

    fun saveCustomEndpoint(endpoint: CustomEndpointConfig) {
        val current = getCustomEndpoints().toMutableList()
        val index = current.indexOfFirst { it.id == endpoint.id }
        if (index >= 0) {
            current[index] = endpoint
        } else {
            current.add(endpoint)
        }

        saveCustomEndpointsList(current)
        refreshVaultState()
    }

    fun deleteCustomEndpoint(endpointId: String) {
        val current = getCustomEndpoints().filterNot { it.id == endpointId }
        saveCustomEndpointsList(current)
        refreshVaultState()
    }

    fun getCustomEndpoints(): List<CustomEndpointConfig> {
        val jsonStr = prefs.getString(KEY_CUSTOM_ENDPOINTS_JSON, "[]") ?: "[]"
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<CustomEndpointConfig>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CustomEndpointConfig(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        baseUrl = obj.optString("baseUrl"),
                        apiKey = obj.optString("apiKey"),
                        authHeaderName = obj.optString("authHeaderName", "Authorization"),
                        engineType = obj.optString("engineType", "CLOUD_TTS"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCustomEndpointsList(list: List<CustomEndpointConfig>) {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("baseUrl", item.baseUrl)
                put("apiKey", item.apiKey)
                put("authHeaderName", item.authHeaderName)
                put("engineType", item.engineType)
                put("createdAt", item.createdAt)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_ENDPOINTS_JSON, array.toString()).apply()
    }

    companion object {
        private const val KEYSTORE_ALIAS = "voconexus_vault_key"
        private const val PREFS_FILENAME = "voconexus_secure_vault"
        private const val PREFS_FILENAME_FALLBACK = "voconexus_secure_vault_fallback"
        private const val KEY_PREFIX_API_KEY = "api_key_"
        private const val KEY_PREFIX_BASE_URL = "base_url_"
        private const val KEY_PREFIX_MODEL_ID = "model_id_"
        private const val KEY_CUSTOM_ENDPOINTS_JSON = "custom_endpoints_json"

        val BUILT_IN_PROVIDERS = listOf(
            // --- Local Engines ---
            ProviderMetadata(
                id = "kokoro-82m",
                name = "Kokoro 82M",
                category = ProviderCategory.LOCAL_ENGINE,
                description = "Lightweight, ultra-fast 82M parameter ONNX neural voice model.",
                requiresKey = false
            ),
            ProviderMetadata(
                id = "piper-onnx",
                name = "Piper TTS",
                category = ProviderCategory.LOCAL_ENGINE,
                description = "Fast, local neural text-to-speech engine with 100+ open voices.",
                requiresKey = false
            ),
            ProviderMetadata(
                id = "sherpa-onnx",
                name = "Sherpa-ONNX",
                category = ProviderCategory.LOCAL_ENGINE,
                description = "Offline speech synthesis engine based on VITS & Next-gen Kaldi.",
                requiresKey = false
            ),
            ProviderMetadata(
                id = "localai",
                name = "LocalAI Engine",
                category = ProviderCategory.LOCAL_ENGINE,
                description = "Drop-in local OpenAI-compatible REST server (http://localhost:8080).",
                requiresKey = false,
                defaultBaseUrl = "http://localhost:8080"
            ),
            ProviderMetadata(
                id = "jan_ai",
                name = "Jan AI Local Engine",
                category = ProviderCategory.LOCAL_ENGINE,
                description = "Open-source desktop AI local inference server (http://localhost:1337).",
                requiresKey = false,
                defaultBaseUrl = "http://localhost:1337"
            ),
            ProviderMetadata(
                id = "textgen_webui",
                name = "Text Generation WebUI",
                category = ProviderCategory.LOCAL_ENGINE,
                description = "oobabooga self-hosted REST API endpoint (http://localhost:5000).",
                requiresKey = false,
                defaultBaseUrl = "http://localhost:5000"
            ),

            // --- Cloud TTS / Voice Providers ---
            ProviderMetadata(
                id = "elevenlabs",
                name = "ElevenLabs",
                category = ProviderCategory.CLOUD_TTS,
                description = "State-of-the-art AI voice cloning and multi-lingual voice synthesis.",
                defaultBaseUrl = "https://api.elevenlabs.io",
                websiteUrl = "https://elevenlabs.io"
            ),
            ProviderMetadata(
                id = "cartesia",
                name = "Cartesia (Sonic)",
                category = ProviderCategory.CLOUD_TTS,
                description = "Ultra-low latency real-time voice generation engine.",
                defaultBaseUrl = "https://api.cartesia.ai",
                websiteUrl = "https://cartesia.ai"
            ),
            ProviderMetadata(
                id = "fish_audio",
                name = "Fish Audio",
                category = ProviderCategory.CLOUD_TTS,
                description = "High-fidelity zero-shot voice synthesis and audio generation.",
                defaultBaseUrl = "https://api.fish.audio",
                websiteUrl = "https://fish.audio"
            ),
            ProviderMetadata(
                id = "deepgram",
                name = "Deepgram (Aura)",
                category = ProviderCategory.CLOUD_TTS,
                description = "Real-time humanlike conversational AI text-to-speech.",
                defaultBaseUrl = "https://api.deepgram.com",
                websiteUrl = "https://deepgram.com"
            ),
            ProviderMetadata(
                id = "openai_tts",
                name = "OpenAI Audio TTS",
                category = ProviderCategory.CLOUD_TTS,
                description = "Alloy, Echo, Fable, Onyx, Nova, and Shimmer neural voices.",
                defaultBaseUrl = "https://api.openai.com",
                websiteUrl = "https://platform.openai.com"
            ),
            ProviderMetadata(
                id = "playht",
                name = "PlayHT",
                category = ProviderCategory.CLOUD_TTS,
                description = "Conversational AI voice generator with expressive voices.",
                defaultBaseUrl = "https://api.play.ht",
                websiteUrl = "https://play.ht"
            ),
            ProviderMetadata(
                id = "google_cloud_tts",
                name = "Google Cloud TTS",
                category = ProviderCategory.CLOUD_TTS,
                description = "Google Wavenet & Journey neural voices across 50+ languages.",
                defaultBaseUrl = "https://texttospeech.googleapis.com",
                websiteUrl = "https://cloud.google.com/text-to-speech"
            ),
            ProviderMetadata(
                id = "azure_speech",
                name = "Azure Speech Services",
                category = ProviderCategory.CLOUD_TTS,
                description = "Microsoft Azure Cognitive Speech synthesis API.",
                defaultBaseUrl = "https://azure.microsoft.com",
                websiteUrl = "https://azure.microsoft.com/services/cognitive-services/text-to-speech"
            ),
            ProviderMetadata(
                id = "amazon_polly",
                name = "Amazon Polly",
                category = ProviderCategory.CLOUD_TTS,
                description = "AWS Polly Neural & Generative speech synthesis engine.",
                defaultBaseUrl = "https://polly.amazonaws.com",
                websiteUrl = "https://aws.amazon.com/polly"
            ),
            ProviderMetadata(
                id = "lmnt",
                name = "LMNT",
                category = ProviderCategory.CLOUD_TTS,
                description = "Real-time natural speech synthesis platform.",
                defaultBaseUrl = "https://api.lmnt.com",
                websiteUrl = "https://lmnt.com"
            ),
            ProviderMetadata(
                id = "resemble_ai",
                name = "Resemble AI",
                category = ProviderCategory.CLOUD_TTS,
                description = "Custom voice cloning and neural speech API.",
                defaultBaseUrl = "https://api.resemble.ai",
                websiteUrl = "https://resemble.ai"
            ),
            ProviderMetadata(
                id = "neets_ai",
                name = "Neets AI",
                category = ProviderCategory.CLOUD_TTS,
                description = "Cost-effective neural text-to-speech synthesis API.",
                defaultBaseUrl = "https://api.neets.ai",
                websiteUrl = "https://neets.ai"
            ),
            ProviderMetadata(
                id = "murf_ai",
                name = "Murf AI",
                category = ProviderCategory.CLOUD_TTS,
                description = "Studio quality synthetic voiceovers for content generation.",
                defaultBaseUrl = "https://api.murf.ai",
                websiteUrl = "https://murf.ai"
            ),
            ProviderMetadata(
                id = "speechify",
                name = "Speechify API",
                category = ProviderCategory.CLOUD_TTS,
                description = "High speed reader and natural voice engine API.",
                defaultBaseUrl = "https://api.speechify.com",
                websiteUrl = "https://speechify.com"
            ),
            ProviderMetadata(
                id = "replicate",
                name = "Replicate (TTS)",
                category = ProviderCategory.CLOUD_TTS,
                description = "Cloud hosted open-weight audio models (XTTS, SeamlessM4T).",
                defaultBaseUrl = "https://api.replicate.com",
                websiteUrl = "https://replicate.com"
            ),
            ProviderMetadata(
                id = "fal_ai",
                name = "Fal.ai (Audio)",
                category = ProviderCategory.CLOUD_TTS,
                description = "Generative media API platform for audio & speech synthesis.",
                defaultBaseUrl = "https://fal.run",
                websiteUrl = "https://fal.ai"
            ),
            ProviderMetadata(
                id = "together_tts",
                name = "Together AI (Audio)",
                category = ProviderCategory.CLOUD_TTS,
                description = "Fast inference cloud platform for open audio models.",
                defaultBaseUrl = "https://api.together.xyz",
                websiteUrl = "https://together.ai"
            ),
            ProviderMetadata(
                id = "edge_tts",
                name = "Edge TTS",
                category = ProviderCategory.CLOUD_TTS,
                description = "Free Microsoft Edge online neural voice endpoint.",
                requiresKey = false
            ),
            ProviderMetadata(
                id = "groq_audio",
                name = "Groq Speech & Audio (Whisper)",
                category = ProviderCategory.CLOUD_TTS,
                description = "Ultra-fast LPU Whisper speech transcription and translation pipeline.",
                defaultBaseUrl = "https://api.groq.com",
                websiteUrl = "https://groq.com"
            ),
            ProviderMetadata(
                id = "openrouter_audio",
                name = "OpenRouter Audio & Voice",
                category = ProviderCategory.CLOUD_TTS,
                description = "Unified cloud gateway to multimodal speech & audio models.",
                defaultBaseUrl = "https://openrouter.ai/api/v1",
                websiteUrl = "https://openrouter.ai"
            ),
            ProviderMetadata(
                id = "huggingface_audio",
                name = "Hugging Face Audio API",
                category = ProviderCategory.CLOUD_TTS,
                description = "Hugging Face Inference API for open-weight TTS & speech models.",
                defaultBaseUrl = "https://api-inference.huggingface.co",
                websiteUrl = "https://huggingface.co"
            ),
            ProviderMetadata(
                id = "huggingface_space_audio",
                name = "Hugging Face Spaces (Audio)",
                category = ProviderCategory.CLOUD_TTS,
                description = "User-hosted Gradio & Space endpoints for speech synthesis.",
                defaultBaseUrl = "https://huggingface.co/spaces",
                websiteUrl = "https://huggingface.co/spaces"
            ),
            ProviderMetadata(
                id = "assemblyai",
                name = "AssemblyAI Speech API",
                category = ProviderCategory.CLOUD_TTS,
                description = "Enterprise speech AI models and multi-lingual audio processing.",
                defaultBaseUrl = "https://api.assemblyai.com",
                websiteUrl = "https://assemblyai.com"
            ),
            ProviderMetadata(
                id = "cloudflare_audio",
                name = "Cloudflare Workers AI (Audio)",
                category = ProviderCategory.CLOUD_TTS,
                description = "Serverless global edge inference for speech and audio models.",
                defaultBaseUrl = "https://api.cloudflare.com/client/v4",
                websiteUrl = "https://ai.cloudflare.com"
            ),
            ProviderMetadata(
                id = "minimax_audio",
                name = "MiniMax Audio & Voice",
                category = ProviderCategory.CLOUD_TTS,
                description = "Ultra-realistic text-to-speech and voice synthesis API.",
                defaultBaseUrl = "https://api.minimax.chat",
                websiteUrl = "https://minimax.chat"
            ),
            ProviderMetadata(
                id = "speechmatics",
                name = "Speechmatics Voice API",
                category = ProviderCategory.CLOUD_TTS,
                description = "Autonomous Speech Recognition & Voice Intelligence API.",
                defaultBaseUrl = "https://api.speechmatics.com",
                websiteUrl = "https://speechmatics.com"
            ),
            ProviderMetadata(
                id = "gladia",
                name = "Gladia Speech Intelligence",
                category = ProviderCategory.CLOUD_TTS,
                description = "Real-time speech-to-text and audio translation engine.",
                defaultBaseUrl = "https://api.gladia.io",
                websiteUrl = "https://gladia.io"
            ),
            ProviderMetadata(
                id = "rev_ai",
                name = "Rev.ai Speech API",
                category = ProviderCategory.CLOUD_TTS,
                description = "Enterprise-grade speech recognition and voice API.",
                defaultBaseUrl = "https://api.rev.ai",
                websiteUrl = "https://rev.ai"
            ),

            // --- LLM / Script / Translator Providers ---
            ProviderMetadata(
                id = "openai_llm",
                name = "OpenAI (GPT-4o / O3)",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Industry standard LLM for script translation & SSML enrichment.",
                defaultBaseUrl = "https://api.openai.com",
                websiteUrl = "https://platform.openai.com"
            ),
            ProviderMetadata(
                id = "anthropic",
                name = "Anthropic (Claude 3.5)",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Nuanced multi-lingual text reasoning and document parsing.",
                defaultBaseUrl = "https://api.anthropic.com",
                websiteUrl = "https://anthropic.com"
            ),
            ProviderMetadata(
                id = "gemini",
                name = "Google Gemini (1.5 Pro/Flash)",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Long-context multimodal model with exceptional multi-language skills.",
                defaultBaseUrl = "https://generativelanguage.googleapis.com",
                websiteUrl = "https://ai.google.dev"
            ),
            ProviderMetadata(
                id = "groq",
                name = "Groq Llama 3 Speed API",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Ultra-fast LPUs for instantaneous document translation.",
                defaultBaseUrl = "https://api.groq.com",
                websiteUrl = "https://groq.com"
            ),
            ProviderMetadata(
                id = "deepseek",
                name = "DeepSeek AI",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "High precision open-weight reasoning model API.",
                defaultBaseUrl = "https://api.deepseek.com",
                websiteUrl = "https://deepseek.com"
            ),
            ProviderMetadata(
                id = "mistral",
                name = "Mistral AI",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "European leader in open LLMs (Mistral Large, Codestral).",
                defaultBaseUrl = "https://api.mistral.ai",
                websiteUrl = "https://mistral.ai"
            ),
            ProviderMetadata(
                id = "openrouter",
                name = "OpenRouter Hub",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Unified gateway to 100+ open source and proprietary LLMs.",
                defaultBaseUrl = "https://openrouter.ai/api/v1",
                websiteUrl = "https://openrouter.ai"
            ),
            ProviderMetadata(
                id = "perplexity",
                name = "Perplexity AI",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Online research and grounded text translation engine.",
                defaultBaseUrl = "https://api.perplexity.ai",
                websiteUrl = "https://perplexity.ai"
            ),
            ProviderMetadata(
                id = "together_llm",
                name = "Together AI (LLM)",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Cloud serverless inference for open-source AI models.",
                defaultBaseUrl = "https://api.together.xyz",
                websiteUrl = "https://together.ai"
            ),
            ProviderMetadata(
                id = "fireworks",
                name = "Fireworks AI",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Fast production inference platform for generative models.",
                defaultBaseUrl = "https://api.fireworks.ai",
                websiteUrl = "https://fireworks.ai"
            ),
            ProviderMetadata(
                id = "cohere",
                name = "Cohere (Command R+)",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Enterprise RAG and multi-lingual translation API.",
                defaultBaseUrl = "https://api.cohere.com",
                websiteUrl = "https://cohere.com"
            ),
            ProviderMetadata(
                id = "huggingface_llm",
                name = "Hugging Face Inference LLM",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Inference API for open-source LLMs & translation models.",
                defaultBaseUrl = "https://api-inference.huggingface.co",
                websiteUrl = "https://huggingface.co"
            ),
            ProviderMetadata(
                id = "huggingface_space_llm",
                name = "Hugging Face Spaces (LLM)",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Custom HF Gradio / TGI space endpoints for translation.",
                defaultBaseUrl = "https://huggingface.co/spaces",
                websiteUrl = "https://huggingface.co/spaces"
            ),
            ProviderMetadata(
                id = "hyperbolic",
                name = "Hyperbolic AI",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "High-performance decentralized AI GPU inference platform.",
                defaultBaseUrl = "https://api.hyperbolic.xyz",
                websiteUrl = "https://hyperbolic.xyz"
            ),
            ProviderMetadata(
                id = "sambanova",
                name = "SambaNova Systems",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Ultra-fast SN40L chip inference for massive LLMs.",
                defaultBaseUrl = "https://api.sambanova.ai",
                websiteUrl = "https://sambanova.ai"
            ),
            ProviderMetadata(
                id = "cerebras",
                name = "Cerebras AI",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "World's fastest LLM inference on Wafer-Scale Engines.",
                defaultBaseUrl = "https://api.cerebras.ai",
                websiteUrl = "https://cerebras.ai"
            ),
            ProviderMetadata(
                id = "novita",
                name = "Novita AI",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Scalable serverless API platform for open-source LLMs.",
                defaultBaseUrl = "https://api.novita.ai",
                websiteUrl = "https://novita.ai"
            ),
            ProviderMetadata(
                id = "deepinfra",
                name = "DeepInfra AI Platform",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Ultra-low cost hosted open models for text & audio.",
                defaultBaseUrl = "https://api.deepinfra.com",
                websiteUrl = "https://deepinfra.com"
            ),
            ProviderMetadata(
                id = "anyscale",
                name = "Anyscale Endpoints",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Fast, reliable Ray-powered LLM serving platform.",
                defaultBaseUrl = "https://api.endpoints.anyscale.com",
                websiteUrl = "https://anyscale.com"
            ),
            ProviderMetadata(
                id = "octoai",
                name = "OctoAI Compute Platform",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Optimized serverless endpoints for open LLM models.",
                defaultBaseUrl = "https://text.octoai.run",
                websiteUrl = "https://octo.ai"
            ),
            ProviderMetadata(
                id = "ai21",
                name = "AI21 Labs (Jamba)",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Enterprise SSM-Transformer hybrid models for text generation.",
                defaultBaseUrl = "https://api.ai21.com",
                websiteUrl = "https://ai21.com"
            ),
            ProviderMetadata(
                id = "monsterapi",
                name = "MonsterAPI Engine",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "1-click fine-tuned & open-source model cloud APIs.",
                defaultBaseUrl = "https://api.monsterapi.ai",
                websiteUrl = "https://monsterapi.ai"
            ),
            ProviderMetadata(
                id = "runpod",
                name = "RunPod Serverless AI",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Global serverless GPU cloud for vLLM & audio worker pools.",
                defaultBaseUrl = "https://api.runpod.ai",
                websiteUrl = "https://runpod.io"
            ),
            ProviderMetadata(
                id = "lmstudio",
                name = "LM Studio (Local Host)",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Local desktop OpenAI-compatible REST server (http://localhost:1234).",
                requiresKey = false,
                defaultBaseUrl = "http://localhost:1234"
            ),
            ProviderMetadata(
                id = "ollama",
                name = "Ollama (Local Host)",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "Self-hosted local REST LLM instance (http://localhost:11434).",
                requiresKey = false,
                defaultBaseUrl = "http://localhost:11434"
            ),
            ProviderMetadata(
                id = "vllm",
                name = "vLLM Server",
                category = ProviderCategory.TRANSLATOR_LLM,
                description = "High-throughput local LLM serving server.",
                requiresKey = false,
                defaultBaseUrl = "http://localhost:8000"
            )
        )
    }
}
