package com.whereware

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

class MainActivity : AppCompatActivity() {

    private lateinit var resultTextView: TextView
    private lateinit var copyButton: Button
    private var extractedText: String = ""

    // Обработчик ответа от встроенной библиотеки Google Document Text Recognition
    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pages = scanResult?.pages
            
            if (!pages.isNullOrEmpty()) {
                val textBuilder = StringBuilder()
                
                // Проходим по абсолютно всем страницам, блокам и строкам огромной накладной из галереи
                pages.forEach { page ->
                    page.textBlocks?.forEach { block ->
                        block.lines.forEach { line ->
                            // Склеиваем каждую считанную строчку текста с новой строки
                            textBuilder.append(line.text.trim()).append("\n")
                        }
                    }
                }
                
                extractedText = textBuilder.toString()

                if (extractedText.isBlank()) {
                    resultTextView.text = "Текст на накладной не обнаружен. Попробуйте выбрать другое изображение из галереи."
                    copyButton.visibility = Button.GONE
                } else {
                    // Засовываем весь гигантский массив текста в наше поле для отображения
                    resultTextView.text = extractedText
                    // Показываем кнопку быстрого копирования
                    copyButton.visibility = Button.VISIBLE
                    Toast.makeText(this, "Накладная успешно распознана!", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Выбор накладной из галереи отменен", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Главный вертикальный контейнер интерфейса WhereWare
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            padding = 40
            setBackgroundColor(Color.parseColor("#F5F5F7")) // Чистый светлый фон для удобства чтения
        }

        // 1. Главная синяя кнопка (открывает сканер, где выбирается накладная из галереи)
        val scanButton = Button(this).apply {
            text = "📁 Выбрать накладную из Галереи"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#007AFF")) // Фирменный синий цвет
            setPadding(0, 40, 0, 40)
        }
        rootLayout.addView(scanButton)

        // 2. Кнопка «Скопировать весь текст» (загорается зеленым только после успешного распознавания)
        copyButton = Button(this).apply {
            text = "📋 Скопировать весь текст"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#34C759")) // Сигнальный зеленый цвет
            setPadding(0, 35, 0, 35)
            visibility = Button.GONE // Скрыта по умолчанию, пока поле пустое
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 }
        }
        rootLayout.addView(copyButton)

        // 3. Область прокрутки (ScrollView) — бесконечный свиток, благодаря которому
        // весь гигантский массив текста накладной можно будет плавно листать пальцем вверх/вниз
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { topMargin = 30 }
        }

        // 4. Крупное текстовое поле внутри скролла (Сюда программа засунет весь текст)
        resultTextView = TextView(this).apply {
            text = "Нажмите синюю кнопку выше, зайдите в галерею смартфона и выберите любую из двух ваших сохраненных накладных."
            textSize = 16f // Крупный, комфортный для глаз размер шрифта
            setTextColor(Color.parseColor("#1C1C1E"))
            setPadding(10, 10, 10, 10)
            gravity = Gravity.START
        }
        
        scrollView.addView(resultTextView)
        rootLayout.addView(scrollView)
        setContentView(rootLayout)

        // При клике запускаем системный инструмент от Google
        scanButton.setOnClickListener {
            val options = GmsDocumentScannerOptions.Builder()
                .setResultFormats(RESULT_FORMAT_JPEG)
                // Режим FULL (BASE_WITH_FILTER): автовыравнивание листа, очистка теней и OCR текста
                .setScannerMode(SCANNER_MODE_BASE_WITH_FILTER) 
                .build()

            GmsDocumentScanning.getClient(options)
                .getStartScanIntent(this)
                .addOnSuccessListener { intentSender ->
                    // Открывается экран Google. На нем вам нужно нажать на значок ГАЛЕРЕИ в углу экрана!
                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Ошибка запуска библиотеки Google: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

        // Кнопка копирования всего массива текста в буфер обмена Android в один клик
        copyButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("WhereWare Extracted Text", extractedText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Весь текст успешно скопирован в буфер!", Toast.LENGTH_SHORT).show()
        }
    }
}
