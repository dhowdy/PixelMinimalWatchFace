package com.benoitletondor.pixelminimalwatchface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.rendering.ComplicationDrawable
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.util.SparseArray
import android.view.SurfaceHolder
import android.view.WindowInsets
import com.benoitletondor.pixelminimalwatchface.model.ComplicationColors
import com.benoitletondor.pixelminimalwatchface.model.Storage
import com.benoitletondor.pixelminimalwatchface.settings.ComplicationLocation
import java.util.*

class PixelMinimalWatchFace : CanvasWatchFaceService() {

    override fun onCreateEngine(): Engine {
        val storage = Injection.storage()
        storage.init(this)

        return Engine(this, storage)
    }

    inner class Engine(private val service: WatchFaceService,
                       private val storage: Storage
    ) : CanvasWatchFaceService.Engine() {
        private lateinit var calendar: Calendar
        private var registeredTimeZoneReceiver = false

        private val watchFaceDrawer = Injection.watchFaceDrawer()

        private lateinit var complicationsColors: ComplicationColors
        private lateinit var activeComplicationDataSparseArray: SparseArray<ComplicationData>
        private lateinit var complicationDrawableSparseArray: SparseArray<ComplicationDrawable>

        private var muteMode = false
        private var ambient = false
        private var lowBitAmbient = false
        private var burnInProtection = false

        private var lastVisibleTimestamp = 0L

        private val timeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                calendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(
                WatchFaceStyle.Builder(service)
                    .setAcceptsTapEvents(true)
                    .build()
            )

            calendar = Calendar.getInstance()

            watchFaceDrawer.onCreate(service)
            initializeComplications()
        }

        private fun initializeComplications() {
            complicationsColors = storage.getComplicationColors()
            activeComplicationDataSparseArray = SparseArray(COMPLICATION_IDS.size)

            val leftComplicationDrawable = ComplicationDrawable(service)
            val rightComplicationDrawable = ComplicationDrawable(service)

            complicationDrawableSparseArray = SparseArray(COMPLICATION_IDS.size)

            complicationDrawableSparseArray.put(LEFT_COMPLICATION_ID, leftComplicationDrawable)
            complicationDrawableSparseArray.put(RIGHT_COMPLICATION_ID, rightComplicationDrawable)

            setComplicationsActiveAndAmbientColors(complicationsColors)
            setActiveComplications(*COMPLICATION_IDS)

            watchFaceDrawer.setComplicationDrawable(LEFT_COMPLICATION_ID, leftComplicationDrawable)
            watchFaceDrawer.setComplicationDrawable(RIGHT_COMPLICATION_ID, rightComplicationDrawable)
        }

        override fun onDestroy() {
            unregisterReceiver()

            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            lowBitAmbient = properties.getBoolean(
                WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false
            )
            burnInProtection = properties.getBoolean(
                WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false
            )
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)

            watchFaceDrawer.onApplyWindowInsets(insets)
        }

        override fun onTimeTick() {
            super.onTimeTick()

            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            ambient = inAmbientMode

            invalidate()
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            if (muteMode != inMuteMode) {
                muteMode = inMuteMode

                invalidate()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            watchFaceDrawer.onSurfaceChanged(width, height)
        }

        override fun onComplicationDataUpdate(watchFaceComplicationId: Int, data: ComplicationData) {
            super.onComplicationDataUpdate(watchFaceComplicationId, data)

            // Adds/updates active complication data in the array.
            activeComplicationDataSparseArray.put(watchFaceComplicationId, data)

            // Updates correct ComplicationDrawable with updated data.
            val complicationDrawable = complicationDrawableSparseArray.get(watchFaceComplicationId)
            complicationDrawable.setComplicationData(data)

            // Invalidate only if not in ambient mode && face is visible since more than 1s
            if( !ambient &&  System.currentTimeMillis() - lastVisibleTimestamp >= 1000L ) {
                invalidate()
            }
        }

        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TAP -> {
                    COMPLICATION_IDS.forEach { complicationId ->
                        val complicationDrawable: ComplicationDrawable = complicationDrawableSparseArray.get(complicationId)

                        if ( complicationDrawable.onTap(x, y) ) {
                            return
                        }
                    }
                }
            }
        }


        override fun onDraw(canvas: Canvas, bounds: Rect) {
            calendar.timeInMillis = System.currentTimeMillis()

            watchFaceDrawer.draw(
                canvas,
                calendar.time,
                muteMode,
                ambient,
                lowBitAmbient,
                burnInProtection
            )
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()

                /* Update time zone in case it changed while we weren't visible. */
                calendar.timeZone = TimeZone.getDefault()

                complicationsColors = storage.getComplicationColors()
                setComplicationsActiveAndAmbientColors(complicationsColors)

                invalidate()

                lastVisibleTimestamp = System.currentTimeMillis()
            } else {
                unregisterReceiver()
            }
        }

        private fun registerReceiver() {
            if (registeredTimeZoneReceiver) {
                return
            }
            registeredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            service.registerReceiver(timeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!registeredTimeZoneReceiver) {
                return
            }
            registeredTimeZoneReceiver = false
            service.unregisterReceiver(timeZoneReceiver)
        }

        private fun setComplicationsActiveAndAmbientColors(complicationColors: ComplicationColors) {
            watchFaceDrawer.setComplicationsColors(complicationColors, activeComplicationDataSparseArray)
        }
    }

    companion object {
        const val LEFT_COMPLICATION_ID = 100
        const val RIGHT_COMPLICATION_ID = 101

        private val COMPLICATION_IDS = intArrayOf(
            LEFT_COMPLICATION_ID, RIGHT_COMPLICATION_ID
        )

        private val COMPLICATION_SUPPORTED_TYPES = arrayOf(
            intArrayOf(
                ComplicationData.TYPE_RANGED_VALUE,
                ComplicationData.TYPE_ICON,
                ComplicationData.TYPE_SHORT_TEXT,
                ComplicationData.TYPE_SMALL_IMAGE
            ),
            intArrayOf(
                ComplicationData.TYPE_RANGED_VALUE,
                ComplicationData.TYPE_ICON,
                ComplicationData.TYPE_SHORT_TEXT,
                ComplicationData.TYPE_SMALL_IMAGE
            )
        )

        fun getComplicationId(complicationLocation: ComplicationLocation): Int {
            return when (complicationLocation) {
                ComplicationLocation.LEFT -> LEFT_COMPLICATION_ID
                ComplicationLocation.RIGHT -> RIGHT_COMPLICATION_ID
            }
        }

        fun getSupportedComplicationTypes(complicationLocation: ComplicationLocation): IntArray {
            return when (complicationLocation) {
                ComplicationLocation.LEFT -> COMPLICATION_SUPPORTED_TYPES[0]
                ComplicationLocation.RIGHT -> COMPLICATION_SUPPORTED_TYPES[1]
            }
        }

        fun getComplicationIds(): IntArray {
            return COMPLICATION_IDS
        }
    }
}


