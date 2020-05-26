/*
 *  Copyright (c) 2017. Mycroft AI, Inc.
 *
 *  This file is part of Mycroft-Android a client for Mycroft Core.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package mycroft.ai

import com.crashlytics.android.Crashlytics
import io.fabric.sdk.android.Fabric
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import mycroft.ai.Constants.MycroftMobileConstants.VERSION_CODE_PREFERENCE_KEY
import mycroft.ai.Constants.MycroftMobileConstants.VERSION_NAME_PREFERENCE_KEY
import mycroft.ai.adapters.MycroftAdapter
import mycroft.ai.receivers.NetworkChangeReceiver
import mycroft.ai.services.BackgroundService
import mycroft.ai.shared.utilities.GuiUtilities
import mycroft.ai.shared.wear.Constants.MycroftSharedConstants.MYCROFT_WEAR_REQUEST
import mycroft.ai.shared.wear.Constants.MycroftSharedConstants.MYCROFT_WEAR_REQUEST_KEY_NAME
import mycroft.ai.shared.wear.Constants.MycroftSharedConstants.MYCROFT_WEAR_REQUEST_MESSAGE
import mycroft.ai.utils.NetworkUtil

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager

import org.java_websocket.client.WebSocketClient
import org.java_websocket.exceptions.WebsocketNotConnectedException
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.net.URISyntaxException
import java.util.*

class MainActivity : AppCompatActivity(), RecognitionListener {
    private val logTag = "Mycroft"
    private val utterances = mutableListOf<Utterance>()
    private val reqCodeSpeechInput = 101

    private var maximumRetries = 1
    private var currentItemPosition = -1
    private var isNetworkChangeReceiverRegistered = false
    private var isWearBroadcastRevieverRegistered = false
    private var launchedFromWidget = false
    private var autoPromptForSpeech = false
    private var backgroundService = true
    private var speech: SpeechRecognizer? = null

    // API Bug Fix see "onResults"
    private var singleResult = true

    private lateinit var ttsManager: TTSManager
    private lateinit var mycroftAdapter: MycroftAdapter
    private lateinit var wsip: String
    private lateinit var sharedPref: SharedPreferences
    private lateinit var networkChangeReceiver: NetworkChangeReceiver
    private lateinit var wearBroadcastReceiver: BroadcastReceiver

    var webSocketClient: WebSocketClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupPermissions()

        Fabric.with(this, Crashlytics())
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        registerForContextMenu(cardList)

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        ttsManager = TTSManager(this)
        mycroftAdapter = MycroftAdapter(utterances, applicationContext, menuInflater)

        speech = SpeechRecognizer.createSpeechRecognizer(this)
        speech?.setRecognitionListener(this)

        mycroftAdapter.setOnLongItemClickListener(object: MycroftAdapter.OnLongItemClickListener {
            override fun itemLongClicked(v: View, position: Int) {
                currentItemPosition = position
                v.showContextMenu()
            }
        })

        kbMicSwitch.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPref.edit()
            editor.putBoolean("kbMicSwitch", isChecked)
            editor.apply()

            if (isChecked) {
                // Switch to mic
                micButton.visibility = View.VISIBLE
                utteranceInput.visibility = View.INVISIBLE
                sendUtterance.visibility = View.INVISIBLE
            } else {
                // Switch to keyboard
                micButton.visibility = View.INVISIBLE
                utteranceInput.visibility = View.VISIBLE
                sendUtterance.visibility = View.VISIBLE
            }
        }

        sendUtterance.setOnClickListener {
            val utterance = utteranceInput.text.toString()
            if (utterance != "") {
                sendMessage(utterance)
                utteranceInput.text.clear()
            }
        }

        //attach a listener to check for changes in state
        voxswitch.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPref.edit()
            editor.putBoolean("appReaderSwitch", isChecked)
            editor.apply()

            // stop tts from speaking if app reader disabled
            if (!isChecked) ttsManager.initQueue("")
        }

        val llm = androidx.recyclerview.widget.LinearLayoutManager(this)
        llm.stackFromEnd = true
        llm.orientation = androidx.recyclerview.widget.LinearLayoutManager.VERTICAL
        with (cardList) {
            setHasFixedSize(true)
            layoutManager = llm
            adapter = mycroftAdapter
        }

        micButton.setOnClickListener { promptSpeechInput() }

        // start the discovery activity (testing only)
        // startActivity(new Intent(this, DiscoveryActivity.class));
    }

    private fun setupPermissions() {
        val permission = ContextCompat.checkSelfPermission(this,  Manifest.permission.RECORD_AUDIO)
        if (permission != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    reqCodeSpeechInput)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            reqCodeSpeechInput -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) finish()
            }
        }
    }

    public override fun onStart() {
        super.onStart()
        recordVersionInfo()
        registerReceivers()
        checkIfLaunchedFromWidget(intent)
    }

    public override fun onResume() {
        super.onResume()
        loadPreferences()

        backgroundService = true
        stopBackgroundService()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onPause() {
        unregisterReceivers()
        if (speech != null) speech!!.destroy()

        super.onPause()
    }

    public override fun onStop() {
        if (launchedFromWidget) {
            autoPromptForSpeech = true
        }
        if (backgroundService) startBackgroundService()

        super.onStop()
    }

    public override fun onDestroy() {
        ttsManager.shutDown()
        super.onDestroy()
    }

    /**
    *
    * END Android Lifecycle, START ActionMenu
    *
    */

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_setup, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        var consumed = false
        when (item.itemId) {
            R.id.action_settings -> {
                backgroundService = false
                startActivity(Intent(this, SettingsActivity::class.java))
                consumed = true
            }
            R.id.action_home_mycroft_ai -> {
                val intent = Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.mycroft_website_url)))
                startActivity(intent)
            }
        }

        return consumed && super.onOptionsItemSelected(item)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        super.onContextItemSelected(item)
        if (item.itemId == R.id.user_resend) {
            // Resend user utterance
            sendMessage(utterances[currentItemPosition].utterance)
        } else if (item.itemId == R.id.user_copy || item.itemId == R.id.mycroft_copy) {
            // Copy utterance to clipboard
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
            val data = ClipData.newPlainText("text", utterances[currentItemPosition].utterance)
            clipboardManager?.setPrimaryClip(data)
            showToast("Copied to clipboard")
        } else if (item.itemId == R.id.mycroft_share) {
            // Share utterance
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, utterances[currentItemPosition].utterance)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, resources.getText(R.string.action_share)))
        } else {
            return super.onContextItemSelected(item)
        }

        return true
    }

    /**
    *
    * END ActionMenu, START Background System ChangeListener
    *
    */
    private fun registerReceivers() {
        registerNetworkReceiver()
        registerWearBroadcastReceiver()
    }

    private fun registerNetworkReceiver() {
        if (!isNetworkChangeReceiverRegistered) {
            // set up the dynamic broadcast receiver for maintaining the socket
            networkChangeReceiver = NetworkChangeReceiver(this)

            // set up the intent filters
            val connChange = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
            val wifiChange = IntentFilter("android.net.wifi.WIFI_STATE_CHANGED")
            registerReceiver(networkChangeReceiver, connChange)
            registerReceiver(networkChangeReceiver, wifiChange)

            isNetworkChangeReceiverRegistered = true
        }
    }

    private fun registerWearBroadcastReceiver() {
        if (!isWearBroadcastRevieverRegistered) {
            wearBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val message = intent.getStringExtra(MYCROFT_WEAR_REQUEST_MESSAGE)
                    // send to mycroft
                    if (message != null) {
                        Log.d(logTag, "Wear message received: [$message] sending to Mycroft")
                        sendMessage(message)
                    }
                }
            }

            registerReceiver(wearBroadcastReceiver, IntentFilter(MYCROFT_WEAR_REQUEST))
            isWearBroadcastRevieverRegistered = true
        }
    }

    private fun unregisterReceivers() {
        if (isNetworkChangeReceiverRegistered) {
            unregisterBroadcastReceiver(networkChangeReceiver)
            isNetworkChangeReceiverRegistered = false
        }

        if (isWearBroadcastRevieverRegistered) {
            isWearBroadcastRevieverRegistered = false
            unregisterBroadcastReceiver(wearBroadcastReceiver)
        }
    }

    private fun unregisterBroadcastReceiver(broadcastReceiver: BroadcastReceiver) {
        unregisterReceiver(broadcastReceiver)
    }


    /**
     *
     * END Background System ChangeListener, START Connection to MycroftCore
     *
     * This method will attach the correct path to the
     * [.wsip] hostname to allow for communication
     * with a Mycroft instance at that address.
     *
     *
     * If [.wsip] cannot be used as a hostname
     * in a [URI] (e.g. because it's null), then
     * this method will return null.
     *
     *
     * @return a valid uri, or null
     */
    private fun deriveURI(): URI? {
        return if (wsip.isNotEmpty()) {
            try {
                URI("ws://$wsip:8181/core")
            } catch (e: URISyntaxException) {
                Log.e(logTag, "Unable to build URI for websocket", e)
                null
            }
        } else {
            null
        }
    }

    /**
     * Build connection to MycroftCore WebInstance
     */
    fun connectWebSocket() {
        val uri = deriveURI()

        if (uri != null) {
            webSocketClient = object : WebSocketClient(uri) {
                override fun onOpen(serverHandshake: ServerHandshake) {
                    // Set micImg to blue
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) micButton.setImageDrawable(resources.getDrawable(R.drawable.ic_mycroft, applicationContext.theme))
                    else micButton.setImageDrawable(resources.getDrawable(R.drawable.ic_mycroft))
                    Log.i("Websocket", "Opened")
                }

                override fun onMessage(s: String) {
                    // Log.i(TAG, s);
                    runOnUiThread(MessageParser(s, object : SafeCallback<Utterance> {
                        override fun call(param: Utterance) {
                            addData(param)
                        }
                    }))
                }

                override fun onClose(i: Int, s: String, b: Boolean) {
                    // Set micImg to red
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) micButton.setImageDrawable(resources.getDrawable(R.drawable.ic_mycroft_red, applicationContext.theme))
                    else micButton.setImageDrawable(resources.getDrawable(R.drawable.ic_mycroft_red))
                    Log.i("Websocket", "Closed $s")

                }

                override fun onError(e: Exception) {
                    // Set micImg to red
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) micButton.setImageDrawable(resources.getDrawable(R.drawable.ic_mycroft_red, applicationContext.theme))
                    else micButton.setImageDrawable(resources.getDrawable(R.drawable.ic_mycroft_red))
                    Log.i("Websocket", "Error " + e.message)
                }
            }
            webSocketClient!!.connect()
        }
    }

    /**
     * Send to MycroftCore WebInstance
     */
    fun sendMessage(msg: String) {
        // let's keep it simple eh?
        //final String json = "{\"message_type\":\"recognizer_loop:utterance\", \"context\": null, \"metadata\": {\"utterances\": [\"" + msg + "\"]}}";
        val json = "{\"data\": {\"utterances\": [\"$msg\"]}, \"type\": \"recognizer_loop:utterance\", \"context\": null}"

        try {
            if (webSocketClient == null || webSocketClient!!.connection.isClosed) {
                // try and reconnect
                if (NetworkUtil.getConnectivityStatus(this) == NetworkUtil.NETWORK_STATUS_WIFI) { //TODO: add config to specify wifi only.
                    connectWebSocket()
                }
            }
            val handler = Handler()
            handler.postDelayed({
                // Actions to do after 1 seconds
                try {
                    webSocketClient!!.send(json)
                    addData(Utterance(msg, UtteranceFrom.USER))
                } catch (exception: WebsocketNotConnectedException) {
                    showToast(resources.getString(R.string.websocket_closed))
                }
            }, 1000)

        } catch (exception: WebsocketNotConnectedException) {
            showToast(resources.getString(R.string.websocket_closed))
        }

    }

    /**
     * Receive MycroftCore WebInstance
     */
    private fun addData(mycroftUtterance: Utterance) {
        utterances.add(mycroftUtterance)
        mycroftAdapter.notifyItemInserted(utterances.size - 1)

        // TURN OFF SELF SPOKEN SETTING
        if (voxswitch.isChecked && (mycroftUtterance.from !== UtteranceFrom.USER || sharedPref.getBoolean("repeatSwitch", false))) {
            ttsManager.addQueue(mycroftUtterance.utterance)
        }
        cardList.smoothScrollToPosition(mycroftAdapter.itemCount - 1)
    }

    /**
     *
     * END Connection to MycroftCore, START SpeechRecognition
     * Showing google speech input dialog
     */
    private fun promptSpeechInput() {

        if (webSocketClient != null && webSocketClient!!.connection.isOpen) {
            // Set micImg to green
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) micButton.setImageDrawable(resources.getDrawable(R.drawable.ic_mycroft_green, applicationContext.theme))
            else micButton.setImageDrawable(resources.getDrawable(R.drawable.ic_mycroft_green))

            // Create Listening Intent
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                    getString(R.string.speech_prompt))
            //intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en")
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                    application.packageName)

            // Start Listening
            speech?.startListening(intent)
            /*
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                    getString(R.string.speech_prompt))
            try {
                startActivityForResult(intent, reqCodeSpeechInput)
            } catch (a: ActivityNotFoundException) {
                showToast(getString(R.string.speech_not_supported))
            }*/
        } else showToast(resources.getString(R.string.websocket_closed))
    }

    // Speech Recognizer Class Handling
    override fun onReadyForSpeech(params: Bundle?) {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onBeginningOfSpeech() {}

    override fun onEndOfSpeech() {}

    // Speech Recognizer Error Handling
    override fun onError(errorCode: Int) {
        val errorMessage = getErrorText(errorCode)
        showToast("FAILED $errorMessage")
    }

    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "error from server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Didn't understand, please try again."
        }
    }

    /**
     * Receiving speech input
     */
    override fun onResults(results: Bundle?) {
        /** Known API Bug onResults is called twice on some devices and APIs
         *  https://issuetracker.google.com/issues/152628934
         *  local Boolean for a temp fix
        */
        Log.d(logTag, "SR Test0")
        // Set micImg back to blue

        // check local boolean fix
        if (singleResult) {
            val result = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.d(logTag, "SR Test1 " + result!![0])
            sendMessage(result!![0])
            singleResult=false
        }

        // reset local boolean & change micButton color back to indicate change
        Handler().postDelayed(Runnable {
            singleResult = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) micButton.setImageDrawable(resources.getDrawable(R.drawable.ic_mycroft, applicationContext.theme))
            else micButton.setImageDrawable(resources.getDrawable(R.drawable.ic_mycroft))
        }, 100)
    }

    /**
     *
     * END SpeechRecognition, START MainActivityFunctions
     *
     */

    private fun showToast(message: String) {
        GuiUtilities.showToast(applicationContext, message)
    }


    private fun recordVersionInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val editor = sharedPref.edit()
            val versionCode: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else packageInfo.versionCode

            editor.putInt(VERSION_CODE_PREFERENCE_KEY, versionCode)
            editor.putString(VERSION_NAME_PREFERENCE_KEY, packageInfo.versionName)
            editor.apply()
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(logTag, "Couldn't find package info", e)
        }
    }

    private fun loadPreferences() {

        // get mycroft-core ip address
        wsip = sharedPref.getString("ip", "")?: ""
        if (wsip.isEmpty()) {
            // eep, show the settings intent!
            startActivity(Intent(this, SettingsActivity::class.java))
        } else if (webSocketClient == null || webSocketClient!!.connection.isClosed) {
            connectWebSocket()
        } else if (webSocketClient != null &&   webSocketClient!!.connection.isOpen) {
            webSocketClient!!.close()
            connectWebSocket()
        }

        kbMicSwitch.isChecked = sharedPref.getBoolean("kbMicSwitch", true)
        if (kbMicSwitch.isChecked) {
            // Switch to mic
            micButton.visibility = View.VISIBLE
            utteranceInput.visibility = View.INVISIBLE
            sendUtterance.visibility = View.INVISIBLE
        } else {
            // Switch to keyboard
            micButton.visibility = View.INVISIBLE
            utteranceInput.visibility = View.VISIBLE
            sendUtterance.visibility = View.VISIBLE
        }

        // set app reader setting
        voxswitch.isChecked = sharedPref.getBoolean("appReaderSwitch", true)

        maximumRetries = Integer.parseInt(sharedPref.getString("maximumRetries", "1")?: "1")
    }

    private fun checkIfLaunchedFromWidget(intent: Intent) {
        val extras = getIntent().extras
        if (extras != null) {
            for (key in extras.keySet()) {
                Log.i(logTag, key + " : " + if (extras.get(key) != null) extras.get(key) else "NULL")
            }

            if (extras.containsKey("launchedFromWidget")) {
                launchedFromWidget = extras.getBoolean("launchedFromWidget")
                autoPromptForSpeech = extras.getBoolean("autoPromptForSpeech")
            }

            if (extras.containsKey(MYCROFT_WEAR_REQUEST_KEY_NAME)) {
                Log.i(logTag, "checkIfLaunchedFromWidget - extras contain key:$MYCROFT_WEAR_REQUEST_KEY_NAME")
                sendMessage(extras.getString(MYCROFT_WEAR_REQUEST_KEY_NAME)?: "")
                getIntent().removeExtra(MYCROFT_WEAR_REQUEST_KEY_NAME)
            }
        }

        if (autoPromptForSpeech) {
            promptSpeechInput()
            intent.putExtra("autoPromptForSpeech", false)
        }
    }

    private fun startBackgroundService() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            val widgetIntent = Intent(this@MainActivity, BackgroundService::class.java).putExtra("activity_background", true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(widgetIntent)
            } else {
                startService(widgetIntent)
            }
        }
    }

    private fun stopBackgroundService() {
        val widgetIntent = Intent(this@MainActivity, BackgroundService::class.java).putExtra("activity_background", false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(widgetIntent)
        } else {
            startService(widgetIntent)
        }
    }
}
