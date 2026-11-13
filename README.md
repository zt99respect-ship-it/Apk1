# LiveTranslator — المترجم الفوري للبثوث (بدون إنترنت)

100% offline, free, and unlimited real-time Android system audio & stream translator to Arabic powered by **Vosk** (on-device STT) and **Google ML Kit** (on-device translation) with a draggable floating overlay.

ترجمة فورية مجانية ومفتوحة بدون إنترنت ولا مفاتيح API لصوت البثوث المباشرة والتطبيقات إلى اللغة العربية عبر **Vosk** و **Google ML Kit** مع فقاعة عائمة قابلة للسحب والتخصيص.

---

## English Guide

1. **First Launch Model Setup (One-Time Only)**:
   - On first launch, LiveTranslator automatically downloads and unpacks the selected **Vosk** small speech model (`~35–82 MB`) to internal app storage, and downloads the **ML Kit** on-device translation models (`Source Language <-> Arabic`).
   - Once downloaded, all speech recognition and translation run **100% offline, free, and unlimited**.
2. **Permissions**:
   - Complete the 3-step onboarding (**Display Over Other Apps**, **Screen/Audio Capture**, **Notifications**).
3. **Start Live Translation**:
   - Tap the big circular purple **Start** button on the Home screen (or **Quick Mic Test**).
   - Select the screen/app audio source (e.g., **Kick**, **YouTube**, **Twitch**).
   - A draggable **60dp purple floating bubble** appears above all apps:
     - **Drag**: Move the bubble anywhere on screen.
     - **Tap**: Expand the Arabic translation card (auto-hides after **6 seconds**).
     - **Long-press**: Hide the bubble for **5 minutes**.

### Build Instructions
```bash
./gradlew assembleDebug
```
Output APK: `app/build/outputs/apk/debug/app-debug.apk`
- **Minimum SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 14 (API 34)

---

## دليل الاستخدام (بالعربية)

1. **تنزيل النماذج لأول مرة فقط**:
   - عند فتح التطبيق لأول مرة، يقوم بتنزيل نموذج **Vosk** الصوتي وفك ضغطه في الذاكرة الداخلية للتطبيق، مع حزمة **Google ML Kit** للترجمة المحلية إلى العربية.
   - بعد ذلك يعمل التطبيق **100% بدون إنترنت** ومجاناً وبلا أي حدود أو مفاتيح API.
2. **بدء الترجمة الفورية**:
   - امنح أذونات **الظهور فوق التطبيقات** و**التقاط الصوت والشاشة** و**الإشعارات**.
   - اضغط الزر الدائري الكبير **«بدء صوت الشاشة»** في الرئيسية (أو **«تجربة سريعة عبر الميكروفون»**).
   - ستظهر **فقاعة بنفسجية عائمة (60dp)** فوق التطبيقات:
     - **اسحبها**: لتحريكها لأي مكان.
     - **ضغطة واحدة**: لفتح بطاقة الترجمة العربية (تختفي تلقائياً بعد **6 ثوانٍ**).
     - **ضغطة مطولة**: لإخفائها مؤقتاً لمدة **5 دقائق**.
