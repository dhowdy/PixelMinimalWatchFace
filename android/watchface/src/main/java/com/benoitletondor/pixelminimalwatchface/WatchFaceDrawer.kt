/*
 *   Copyright 2020 Benoit LETONDOR
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.benoitletondor.pixelminimalwatchface

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Icon
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationText
import android.support.wearable.complications.rendering.ComplicationDrawable
import android.text.format.DateUtils.*
import android.util.ArrayMap
import android.util.DisplayMetrics
import android.util.Log
import android.util.SparseArray
import android.view.WindowInsets
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.benoitletondor.pixelminimalwatchface.PixelMinimalWatchFace.Companion.BOTTOM_COMPLICATION_ID
import com.benoitletondor.pixelminimalwatchface.PixelMinimalWatchFace.Companion.LEFT_COMPLICATION_ID
import com.benoitletondor.pixelminimalwatchface.PixelMinimalWatchFace.Companion.MIDDLE_COMPLICATION_ID
import com.benoitletondor.pixelminimalwatchface.PixelMinimalWatchFace.Companion.RIGHT_COMPLICATION_ID
import com.benoitletondor.pixelminimalwatchface.helper.sameAs
import com.benoitletondor.pixelminimalwatchface.helper.timeSizeToScaleFactor
import com.benoitletondor.pixelminimalwatchface.helper.toBitmap
import com.benoitletondor.pixelminimalwatchface.model.ComplicationColors
import com.benoitletondor.pixelminimalwatchface.model.Storage
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

interface WatchFaceDrawer {
    fun onCreate(context: Context, storage: Storage)

    fun onApplyWindowInsets(insets: WindowInsets)
    fun onSurfaceChanged(width: Int, height: Int)
    fun setComplicationDrawable(complicationId: Int, complicationDrawable: ComplicationDrawable)
    fun onComplicationColorsUpdate(complicationColors: ComplicationColors, complicationsData: SparseArray<ComplicationData>)
    fun onComplicationDataUpdate(complicationId: Int,
                                 complicationDrawable: ComplicationDrawable,
                                 data: ComplicationData?,
                                 complicationColors: ComplicationColors)
    fun tapIsOnWeather(x: Int, y: Int): Boolean
    fun tapIsInCenterOfScreen(x: Int, y: Int): Boolean

    fun draw(canvas: Canvas,
             currentTime: Date,
             muteMode: Boolean,
             ambient:Boolean,
             lowBitAmbient: Boolean,
             burnInProtection: Boolean,
             weatherComplicationData: ComplicationData?,
             batteryComplicationData: ComplicationData?)

}

class WatchFaceDrawerImpl : WatchFaceDrawer {
    private lateinit var storage: Storage
    private lateinit var context: Context
    private var drawingState: DrawingState = DrawingState.NoScreenData
    private val complicationsDrawable: MutableMap<Int, ComplicationDrawable> = ArrayMap()

    private lateinit var wearOSLogoPaint: Paint
    private lateinit var timePaint: Paint
    private lateinit var datePaint: Paint
    private lateinit var weatherIconPaint: Paint
    private lateinit var weatherAndBatteryIconColorFilter: ColorFilter
    private lateinit var weatherAndBatteryIconColorFilterDimmed: ColorFilter
    private lateinit var secondsRingPaint: Paint
    @ColorInt private var backgroundColor: Int = 0
    @ColorInt private var timeColor: Int = 0
    @ColorInt private var timeColorDimmed: Int = 0
    @ColorInt private var dateAndBatteryColor: Int = 0
    @ColorInt private var dateAndBatteryColorDimmed: Int = 0
    @ColorInt private var complicationTitleColor: Int = 0
    private lateinit var wearOSLogo: Bitmap
    private lateinit var wearOSLogoAmbient: Bitmap
    private lateinit var productSansRegularFont: Typeface
    private lateinit var batteryIconPaint: Paint
    private var batteryIconSize = 0
    private lateinit var batteryLevelPaint: Paint
    private lateinit var battery100Icon: Bitmap
    private lateinit var battery80Icon: Bitmap
    private lateinit var battery60Icon: Bitmap
    private lateinit var battery50Icon: Bitmap
    private lateinit var battery40Icon: Bitmap
    private lateinit var battery20Icon: Bitmap
    private lateinit var battery10Icon: Bitmap
    private var titleSize: Int = 0
    private var textSize: Int = 0
    private var chinSize: Int = 0
    private var isRound: Boolean = false
    private lateinit var timeFormatter24H: SimpleDateFormat
    private lateinit var timeFormatter12H: SimpleDateFormat
    private var currentTimeSize = 0
    private val secondsCalendar = Calendar.getInstance()
    private var spaceBeforeWeather = 0

    override fun onCreate(context: Context, storage: Storage) {
        this.context = context
        this.storage = storage

        currentTimeSize = storage.getTimeSize()
        wearOSLogoPaint = Paint()
        backgroundColor = ContextCompat.getColor(context, R.color.face_background)
        timeColor = ContextCompat.getColor(context, R.color.face_time)
        timeColorDimmed = ContextCompat.getColor(context, R.color.face_time_dimmed)
        dateAndBatteryColor = ContextCompat.getColor(context, R.color.face_date)
        dateAndBatteryColorDimmed = ContextCompat.getColor(context, R.color.face_date_dimmed)
        complicationTitleColor = ContextCompat.getColor(context, R.color.complication_title_color)
        wearOSLogo = ContextCompat.getDrawable(context, R.drawable.ic_wear_os_logo)!!.toBitmap()
        wearOSLogoAmbient = ContextCompat.getDrawable(context, R.drawable.ic_wear_os_logo_ambient)!!.toBitmap()
        batteryIconPaint = Paint()
        productSansRegularFont = ResourcesCompat.getFont(context, R.font.product_sans_regular)!!
        timeFormatter24H = SimpleDateFormat("HH:mm", Locale.getDefault())
        timeFormatter12H = SimpleDateFormat("h:mm", Locale.getDefault())
        titleSize = context.resources.getDimensionPixelSize(R.dimen.complication_title_size)
        textSize = context.resources.getDimensionPixelSize(R.dimen.complication_text_size)
        spaceBeforeWeather = context.dpToPx(5)
        timePaint = Paint().apply {
            typeface = productSansRegularFont
            strokeWidth = 1.8f
        }
        datePaint = Paint().apply {
            typeface = productSansRegularFont
        }
        batteryLevelPaint = Paint().apply {
            typeface = productSansRegularFont
        }
        weatherIconPaint = Paint()
        weatherAndBatteryIconColorFilter = PorterDuffColorFilter(dateAndBatteryColor, PorterDuff.Mode.SRC_IN)
        weatherAndBatteryIconColorFilterDimmed = PorterDuffColorFilter(dateAndBatteryColorDimmed, PorterDuff.Mode.SRC_IN)
        secondsRingPaint = Paint().apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = 10F
            isAntiAlias = true
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets) {
        chinSize = insets.systemWindowInsetBottom
        isRound = insets.isRound
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        drawingState = DrawingState.NoCacheAvailable(
            width,
            height,
            width / 2f,
            height / 2f
        )
    }

    override fun setComplicationDrawable(complicationId: Int, complicationDrawable: ComplicationDrawable) {
        complicationsDrawable[complicationId] = complicationDrawable
    }

    override fun onComplicationColorsUpdate(complicationColors: ComplicationColors, complicationsData: SparseArray<ComplicationData>) {
        complicationsDrawable.forEach { (complicationId, complicationDrawable) ->
            val primaryComplicationColor = getComplicationPrimaryColor(complicationId, complicationColors)

            complicationDrawable.setTitleSizeActive(titleSize)
            complicationDrawable.setTitleSizeAmbient(titleSize)
            complicationDrawable.setTitleColorActive(complicationTitleColor)
            complicationDrawable.setTitleColorAmbient(complicationTitleColor)
            complicationDrawable.setIconColorActive(primaryComplicationColor)
            complicationDrawable.setIconColorAmbient(dateAndBatteryColorDimmed)
            complicationDrawable.setTextTypefaceActive(productSansRegularFont)
            complicationDrawable.setTitleTypefaceActive(productSansRegularFont)
            complicationDrawable.setTextTypefaceAmbient(productSansRegularFont)
            complicationDrawable.setTitleTypefaceAmbient(productSansRegularFont)

            if( complicationId == BOTTOM_COMPLICATION_ID ) {
                complicationDrawable.setBorderColorActive(ContextCompat.getColor(context, R.color.transparent))
                complicationDrawable.setBorderColorAmbient(ContextCompat.getColor(context, R.color.transparent))
            }

            onComplicationDataUpdate(complicationId, complicationDrawable, complicationsData.get(complicationId), complicationColors)
        }
    }

    override fun onComplicationDataUpdate(complicationId: Int,
                                          complicationDrawable: ComplicationDrawable,
                                          data: ComplicationData?,
                                          complicationColors: ComplicationColors) {
        val primaryComplicationColor = getComplicationPrimaryColor(complicationId, complicationColors)
        if( data != null && data.icon != null ) {
            if( complicationId == BOTTOM_COMPLICATION_ID && ( data.longTitle != null ) ) {
                complicationDrawable.setTextColorActive(primaryComplicationColor)
                complicationDrawable.setTextColorAmbient(dateAndBatteryColorDimmed)
            } else {
                complicationDrawable.setTextColorActive(complicationTitleColor)
                complicationDrawable.setTextColorAmbient(complicationTitleColor)
            }

            if( complicationId != BOTTOM_COMPLICATION_ID && data.shortTitle == null ) {
                complicationDrawable.setTextSizeActive(titleSize)
                complicationDrawable.setTextSizeAmbient(titleSize)
            } else {
                complicationDrawable.setTextSizeActive(textSize)
                complicationDrawable.setTextSizeAmbient(textSize)
            }
        } else {
            complicationDrawable.setTextColorActive(primaryComplicationColor)
            complicationDrawable.setTextColorAmbient(dateAndBatteryColorDimmed)
            complicationDrawable.setTextSizeActive(textSize)
            complicationDrawable.setTextSizeAmbient(textSize)
        }
    }

    override fun tapIsOnWeather(x: Int, y: Int): Boolean {
        val drawingState = drawingState
        if( !storage.shouldShowWeather() ||
            !storage.isUserPremium() ||
            drawingState !is DrawingState.CacheAvailable ) {
            return false
        }

        val displayRect = drawingState.getWeatherDisplayRect() ?: return false
        return displayRect.contains(x, y)
    }

    override fun tapIsInCenterOfScreen(x: Int, y: Int): Boolean {
        val drawingState = drawingState as? DrawingState.CacheAvailable ?: return false

        val centerRect = Rect(
            (drawingState.screenWidth * 0.25f).toInt(),
            (drawingState.screenHeight * 0.25f).toInt(),
            (drawingState.screenWidth * 0.75f).toInt(),
            (drawingState.screenHeight * 0.75f).toInt()
        )

        return centerRect.contains(x, y)
    }

    @ColorInt
    private fun getComplicationPrimaryColor(complicationId: Int, complicationColors: ComplicationColors): Int {
        return when (complicationId) {
            LEFT_COMPLICATION_ID -> { complicationColors.leftColor }
            MIDDLE_COMPLICATION_ID -> { complicationColors.middleColor }
            BOTTOM_COMPLICATION_ID -> { complicationColors.bottomColor }
            else -> { complicationColors.rightColor }
        }
    }

    override fun draw(canvas: Canvas,
                      currentTime: Date,
                      muteMode: Boolean,
                      ambient:Boolean,
                      lowBitAmbient: Boolean,
                      burnInProtection: Boolean,
                      weatherComplicationData: ComplicationData?,
                      batteryComplicationData: ComplicationData?) {

        setPaintVariables(muteMode, ambient, lowBitAmbient, burnInProtection)
        drawBackground(canvas)

        val currentDrawingState = drawingState
        if( currentDrawingState is DrawingState.NoCacheAvailable ) {
            drawingState = currentDrawingState.buildCache()
        } else if( currentDrawingState is DrawingState.CacheAvailable && currentTimeSize != storage.getTimeSize() ) {
            drawingState = currentDrawingState.buildCache()
        }

        val drawingState = drawingState
        if( drawingState is DrawingState.CacheAvailable ){
            drawingState.draw(
                canvas,
                currentTime,
                muteMode, ambient,
                lowBitAmbient,
                burnInProtection,
                storage.isUserPremium(),
                storage.shouldShowSecondsRing(),
                storage.shouldShowBattery(),
                weatherComplicationData,
                batteryComplicationData
            )
        }
    }

    private fun DrawingState.NoCacheAvailable.buildCache(): DrawingState.CacheAvailable {
        val timeSize = storage.getTimeSize()
        setScaledSizes(timeSize)
        loadBatteryIcons()

        val timeText = "22:13"
        val timeTextBounds = Rect().apply {
            timePaint.getTextBounds(timeText, 0, timeText.length, this)
        }
        val timeYOffset = centerY + (timeTextBounds.height() / 2.0f ) - 5f

        val dateText = "May, 15"
        val dateTextHeight = Rect().apply {
            datePaint.getTextBounds(dateText, 0, dateText.length, this)
        }.height()
        val dateYOffset = timeYOffset + (timeTextBounds.height() / 2) - (dateTextHeight / 2.0f ) + context.dpToPx(8)

        val complicationsDrawingCache = buildComplicationDrawingCache(
            timeYOffset - timeTextBounds.height() - context.dpToPx(2),
            dateYOffset + dateTextHeight / 2
        )

        currentTimeSize = timeSize

        val batteryBottomY = screenHeight - chinSize - context.dpToPx(20)

        return DrawingState.CacheAvailable(
            screenWidth,
            screenHeight,
            centerX,
            centerY,
            timeYOffset,
            dateTextHeight,
            dateYOffset,
            complicationsDrawingCache,
            batteryBottomY,
            batteryBottomY + context.dpToPx(1)
        )
    }

    private fun loadBatteryIcons() {
        battery100Icon = ContextCompat.getDrawable(context, R.drawable.battery_100)!!.toBitmap(batteryIconSize, batteryIconSize)
        battery80Icon = ContextCompat.getDrawable(context, R.drawable.battery_80)!!.toBitmap(batteryIconSize, batteryIconSize)
        battery60Icon = ContextCompat.getDrawable(context, R.drawable.battery_60)!!.toBitmap(batteryIconSize, batteryIconSize)
        battery50Icon = ContextCompat.getDrawable(context, R.drawable.battery_50)!!.toBitmap(batteryIconSize, batteryIconSize)
        battery40Icon = ContextCompat.getDrawable(context, R.drawable.battery_40)!!.toBitmap(batteryIconSize, batteryIconSize)
        battery20Icon = ContextCompat.getDrawable(context, R.drawable.battery_20)!!.toBitmap(batteryIconSize, batteryIconSize)
        battery10Icon = ContextCompat.getDrawable(context, R.drawable.battery_10)!!.toBitmap(batteryIconSize, batteryIconSize)
    }

    private fun DrawingState.NoCacheAvailable.buildComplicationDrawingCache(topBottom: Float, bottomTop: Float): ComplicationsDrawingCache {
        val wearOsImage = wearOSLogo

        val sizeOfComplication = (screenWidth / 4.5).toInt()
        val verticalOffset = topBottom.toInt() - sizeOfComplication
        val distanceBetweenComplications = context.dpToPx(3)

        val maxWidth = max(sizeOfComplication, wearOsImage.width)

        val leftBounds = Rect(
            (centerX - (maxWidth / 2) - distanceBetweenComplications - sizeOfComplication).toInt(),
            verticalOffset,
            (centerX - (maxWidth / 2)  - distanceBetweenComplications).toInt(),
            (verticalOffset + sizeOfComplication)
        )

        complicationsDrawable[LEFT_COMPLICATION_ID]?.let { leftComplicationDrawable ->
            leftComplicationDrawable.bounds = leftBounds
        }

        val middleBounds = Rect(
            (centerX - (sizeOfComplication / 2)).toInt(),
            verticalOffset,
            (centerX + (sizeOfComplication / 2)).toInt(),
            (verticalOffset + sizeOfComplication)
        )

        complicationsDrawable[MIDDLE_COMPLICATION_ID]?.let { middleComplicationDrawable ->
            middleComplicationDrawable.bounds = middleBounds
        }

        val rightBounds = Rect(
            (centerX + (maxWidth / 2) + distanceBetweenComplications).toInt(),
            verticalOffset,
            (centerX + (maxWidth / 2)  + distanceBetweenComplications + sizeOfComplication).toInt(),
            (verticalOffset + sizeOfComplication)
        )

        complicationsDrawable[RIGHT_COMPLICATION_ID]?.let { rightComplicationDrawable ->
            rightComplicationDrawable.bounds = rightBounds
        }

        val availableBottomSpace = screenHeight - bottomTop - chinSize - context.dpToPx(15)
        val bottomComplicationHeight = min(availableBottomSpace, context.dpToPx(36).toFloat())
        val bottomComplicationBottom = (bottomTop + bottomComplicationHeight).toInt()
        val bottomComplicationLeft = computeComplicationLeft(bottomComplicationBottom, screenHeight)
        val bottomComplicationWidth = (screenWidth - 2* bottomComplicationLeft) * 0.9
        val bottomBounds = Rect(
            (centerX - (bottomComplicationWidth / 2)).toInt(),
            bottomTop.toInt() + context.dpToPx(5),
            (centerX + (bottomComplicationWidth / 2)).toInt(),
            bottomComplicationBottom
        )

        complicationsDrawable[BOTTOM_COMPLICATION_ID]?.let { bottomComplicationDrawable ->
            bottomComplicationDrawable.bounds = bottomBounds
        }

        val iconXOffset = centerX - (wearOsImage.width / 2.0f)
        val iconYOffset = leftBounds.top + (leftBounds.height() / 2) - (wearOsImage.height / 2)

        return ComplicationsDrawingCache(
            iconXOffset,
            iconYOffset.toFloat()
        )
    }

    private fun computeComplicationLeft(bottomY: Int, screenHeight: Int): Int {
        return if( isRound ) {
            screenHeight / 2 - sqrt((screenHeight / 2).toDouble().pow(2) - ((bottomY - (screenHeight / 2)).toDouble().pow(2))).toInt()
        } else {
            context.dpToPx(10)
        }
    }

    private fun DrawingState.CacheAvailable.draw(canvas: Canvas,
                                                 currentTime: Date,
                                                 muteMode: Boolean,
                                                 ambient:Boolean,
                                                 lowBitAmbient: Boolean,
                                                 burnInProtection: Boolean,
                                                 isUserPremium: Boolean,
                                                 drawSecondsRing: Boolean,
                                                 drawBattery: Boolean,
                                                 weatherComplicationData: ComplicationData?,
                                                 batteryComplicationData: ComplicationData?) {
        val timeText = if( storage.getUse24hTimeFormat()) {
            timeFormatter24H.format(currentTime)
        } else {
            timeFormatter12H.format(currentTime)
        }
        val timeXOffset = centerX - (timePaint.measureText(timeText) / 2f)
        canvas.drawText(timeText, timeXOffset, timeYOffset, timePaint)

        complicationsDrawingCache.drawComplications(canvas, ambient, currentTime, isUserPremium)

        val dateText = formatDateTime(context, currentTime.time, FORMAT_SHOW_DATE or FORMAT_SHOW_WEEKDAY or FORMAT_ABBREV_WEEKDAY)
        val dateTextLength = datePaint.measureText(dateText)
        val dateXOffset = if( isUserPremium && weatherComplicationData != null ) {
            val weatherText = weatherComplicationData.shortText
            val weatherIcon = weatherComplicationData.icon

            if( weatherText != null && weatherIcon != null ) {
                drawWeatherAndComputeDateXOffset(weatherText, weatherIcon, currentTime, dateTextLength, canvas)
            } else {
                currentWeatherBitmap = null
                currentWeatherIcon = null
                weatherTextEndX = null

                centerX - (dateTextLength / 2f)
            }
        } else {
            currentWeatherBitmap = null
            currentWeatherIcon = null
            weatherTextEndX = null

            centerX - (dateTextLength / 2f)
        }

        canvas.drawText(dateText, dateXOffset, dateYOffset, datePaint)

        if( drawSecondsRing && !ambient ) {
            secondsCalendar.time = currentTime

            val endAngle = (secondsCalendar.get(Calendar.SECOND) * 6).toFloat()
            canvas.drawArc(0F, 0F, screenWidth.toFloat(), screenHeight.toFloat(), 270F, endAngle, false, secondsRingPaint)
        }

        if( isUserPremium && batteryComplicationData != null && drawBattery ) {
            val batteryData = batteryComplicationData.shortText
            if( batteryData != null ) {
                val batteryText = batteryData.getText(context, currentTime.time).toString()
                if ( !batteryText.isBlank() ) {
                    val (batteryLevelText, batteryPercent) = try {
                        val batteryPercent = Integer.parseInt(batteryText.filter { it.isDigit() })
                        Pair("$batteryPercent%", batteryPercent)
                    } catch (t: Throwable) {
                        Log.e("WatchFaceDrawer", "Error parsing battery data", t)
                        Pair("N/A", 50)
                    }

                    val batteryTextLength = batteryLevelPaint.measureText(batteryLevelText)
                    val left = (centerX - (batteryTextLength / 2) - (batteryIconSize / 2)).toInt()
                    val icon = getBatteryIcon(batteryPercent)

                    canvas.drawBitmap(
                        icon,
                        null,
                        Rect(
                            left,
                            batteryIconBottomY - batteryIconSize,
                            left + batteryIconSize,
                            batteryIconBottomY
                        ),
                        batteryIconPaint
                    )

                    canvas.drawText(
                        batteryLevelText,
                        (left + batteryIconSize).toFloat(),
                        (batteryLevelBottomY).toFloat(),
                        batteryLevelPaint
                    )
                }
            }
        }
    }

    private fun getBatteryIcon(batteryPercent: Int): Bitmap {
        return when {
            batteryPercent <= 10 -> { battery10Icon }
            batteryPercent <= 25 -> { battery20Icon }
            batteryPercent <= 40 -> { battery40Icon }
            batteryPercent <= 50 -> { battery50Icon }
            batteryPercent <= 70 -> { battery60Icon }
            batteryPercent <= 90 -> { battery80Icon }
            else -> { battery100Icon }
        }
    }

    private fun DrawingState.CacheAvailable.drawWeatherAndComputeDateXOffset(
        weatherText: ComplicationText,
        weatherIcon: Icon,
        currentTime: Date,
        dateTextLength: Float,
        canvas: Canvas
    ): Float {
        val weatherIconSize = dateHeight
        val weatherTextString = weatherText.getText(context, currentTime.time).toString()
        val weatherTextLength = datePaint.measureText(weatherTextString)
        val dateFontMetrics = datePaint.fontMetrics

        val dateXOffset = centerX - (dateTextLength / 2f) - weatherTextLength / 2f - weatherIconSize / 2f - spaceBeforeWeather - dateFontMetrics.descent / 4f

        weatherIconRect.left = (dateXOffset + dateTextLength + spaceBeforeWeather).toInt()
        weatherIconRect.top = (dateYOffset - weatherIconSize + dateFontMetrics.descent / 2f).toInt()
        weatherIconRect.right = (dateXOffset + dateTextLength + weatherIconSize + spaceBeforeWeather + dateFontMetrics.descent / 2f).toInt()
        weatherIconRect.bottom = (dateYOffset + dateFontMetrics.descent).toInt()

        val cachedWeatherIcon = this.currentWeatherIcon
        val cachedWeatherBitmap = this.currentWeatherBitmap
        val weatherIconBitmap = if ( cachedWeatherIcon != null && cachedWeatherBitmap != null && weatherIcon.sameAs(cachedWeatherIcon) ) {
            cachedWeatherBitmap
        } else {
            val bitmap = weatherIcon.loadDrawable(context).toBitmap(weatherIconRect.right - weatherIconRect.left, weatherIconRect.bottom - weatherIconRect.top)
            currentWeatherBitmap = bitmap
            currentWeatherIcon = weatherIcon

            bitmap
        }

        val weatherTextX = dateXOffset + dateTextLength + weatherIconSize + spaceBeforeWeather * 2

        canvas.drawText(
            weatherTextString,
            weatherTextX,
            dateYOffset,
            datePaint
        )
        canvas.drawBitmap(
            weatherIconBitmap,
            null,
            weatherIconRect,
            weatherIconPaint
        )

        weatherTextEndX = weatherTextX + weatherTextLength

        return dateXOffset
    }

    private fun ComplicationsDrawingCache.drawComplications(canvas: Canvas, ambient: Boolean, currentTime: Date, isUserPremium: Boolean) {
        if( isUserPremium && (storage.shouldShowComplicationsInAmbientMode() || !ambient) ) {
            complicationsDrawable.forEach { (complicationId, complicationDrawable) ->
                if( complicationId == MIDDLE_COMPLICATION_ID && storage.shouldShowWearOSLogo() ) {
                    return@forEach
                }

                if( complicationId == BOTTOM_COMPLICATION_ID && storage.shouldShowBattery() ) {
                    return@forEach
                }

                complicationDrawable.draw(canvas, currentTime.time)
            }
        }

        if( storage.shouldShowWearOSLogo() ) {
            val wearOsImage = if( ambient ) { wearOSLogoAmbient } else { wearOSLogo }
            canvas.drawBitmap(wearOsImage, iconXOffset, iconYOffset, wearOSLogoPaint)
        }
    }

    private fun DrawingState.CacheAvailable.buildCache(): DrawingState.CacheAvailable {
        return DrawingState.NoCacheAvailable(screenWidth, screenHeight, centerX, centerY).buildCache()
    }

    private fun drawBackground(canvas: Canvas) {
        canvas.drawColor(backgroundColor)
    }

    private fun setPaintVariables(muteMode: Boolean,
                                  ambient:Boolean,
                                  lowBitAmbient: Boolean,
                                  burnInProtection: Boolean) {
        wearOSLogoPaint.isAntiAlias = !ambient

        timePaint.apply {
            isAntiAlias = !(ambient && lowBitAmbient)
            style = if( ambient && !storage.shouldShowFilledTimeInAmbientMode() ) { Paint.Style.STROKE } else { Paint.Style.FILL }
            color = if( ambient ) { timeColorDimmed } else { timeColor }
        }

        datePaint.apply {
            isAntiAlias = !(ambient && lowBitAmbient)
            color = if( ambient ) { dateAndBatteryColorDimmed } else { dateAndBatteryColor }
        }

        weatherIconPaint.apply {
            isAntiAlias = !ambient
            colorFilter = if( ambient ) { weatherAndBatteryIconColorFilterDimmed } else { weatherAndBatteryIconColorFilter }
        }

        batteryLevelPaint.apply {
            isAntiAlias = !(ambient && lowBitAmbient)
            color = if( ambient ) { dateAndBatteryColorDimmed } else { dateAndBatteryColor }
        }

        batteryIconPaint.apply {
            isAntiAlias = !(ambient && lowBitAmbient)
            colorFilter = if( ambient ) { weatherAndBatteryIconColorFilterDimmed } else { weatherAndBatteryIconColorFilter }
        }
    }

    private fun Context.dpToPx(dp: Int): Int {
        val displayMetrics = resources.displayMetrics
        return (dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT)).roundToInt()
    }

    private fun setScaledSizes(timeSize: Int) {
        val scaleFactor = timeSizeToScaleFactor(timeSize)

        timePaint.textSize = context.resources.getDimension(
            if( isRound ) {
                R.dimen.time_text_size_round
            } else {
                R.dimen.time_text_size
            }
        ) * scaleFactor

        val dateSize = context.resources.getDimension(
            if( isRound ) {
                R.dimen.date_text_size_round
            } else {
                R.dimen.date_text_size
            }
        ) * scaleFactor

        datePaint.textSize = dateSize
        batteryLevelPaint.textSize = context.resources.getDimension(R.dimen.battery_level_size) * scaleFactor
        batteryIconSize = (context.dpToPx(11) * scaleFactor).toInt()
    }
}

private sealed class DrawingState {
    object NoScreenData : DrawingState()
    data class NoCacheAvailable(val screenWidth: Int,
                                val screenHeight: Int,
                                val centerX: Float,
                                val centerY: Float) : DrawingState()
    data class CacheAvailable(val screenWidth: Int,
                              val screenHeight: Int,
                              val centerX: Float,
                              val centerY: Float,
                              val timeYOffset: Float,
                              val dateHeight: Int,
                              val dateYOffset: Float,
                              val complicationsDrawingCache: ComplicationsDrawingCache,
                              val batteryLevelBottomY: Int,
                              val batteryIconBottomY: Int,
                              var currentWeatherIcon: Icon? = null,
                              var currentWeatherBitmap: Bitmap? = null,
                              var weatherTextEndX: Float? = null) : DrawingState() {
        val weatherIconRect = Rect()

        fun getWeatherDisplayRect(): Rect? {
            val currentWeatherIcon = currentWeatherIcon
            val currentWeatherBitmap = currentWeatherBitmap
            val weatherTextEndX = weatherTextEndX
            if( currentWeatherIcon == null || currentWeatherBitmap == null || weatherTextEndX == null ) {
                return null
            }

            return Rect(weatherIconRect).apply {
                right = weatherTextEndX.toInt()
            }
        }
    }
}

private data class ComplicationsDrawingCache(
    val iconXOffset: Float,
    val iconYOffset: Float
)
