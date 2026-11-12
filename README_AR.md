# VirtualCam — النسخة الأولية

تطبيق Android تجريبي بلغة Kotlin، يعمل دون Root.

## ما الذي يعمل في هذه النسخة؟
- اختيار فيديو من الهاتف عبر منتقي ملفات النظام.
- تشغيل الفيديو داخل التطبيق باستخدام AndroidX Media3.
- تكرار الفيديو أو إيقاف التكرار.
- مشاركة الفيديو الأصلي إلى التطبيقات المتوافقة، ومنها TikTok عند ظهوره في قائمة المشاركة.

## ما الذي لا تدّعيه هذه النسخة؟
- لا تستبدل كاميرا النظام أو كاميرا TikTok.
- لا تنشئ بث RTMP بعد.
- لا تعيد ترميز الفيديو أو تغيّر أبعاده في هذه المرحلة؛ المشاركة ترسل الملف الأصلي.

## المتطلبات
- JDK 17
- Android SDK Platform 36
- Gradle 8.13
- اتصال إنترنت لتنزيل اعتماديات Gradle أول مرة

## البناء
من مجلد المشروع:

```bash
gradle wrapper --gradle-version 8.13
./gradlew assembleDebug
```

على Termux قد تحتاج إلى إعداد Android SDK وJava 17 أولاً. مكان APK بعد نجاح البناء:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## هوية التطبيق
- الاسم: VirtualCam
- الحزمة: com.virtualcam.app
- minSdk: 23
- targetSdk: 35
- Kotlin: 2.2.21
- Android Gradle Plugin: 8.13.2
- Media3: 1.11.1

## الخطوة التالية
اختبار البناء والتثبيت على الهاتف، ثم إضافة مكتبة الوسائط ومعاينة الصور والتصدير العمودي 9:16.


## البناء عبر GitHub Actions
يوجد ملف سير العمل `.github/workflows/android-build.yml`.
بعد رفع المشروع إلى مستودع GitHub على الفرع `main`:
1. افتح تبويب Actions.
2. اختر Build VirtualCam APK.
3. اضغط Run workflow أو انتظر البناء بعد push.
4. عند نجاح المهمة، نزّل artifact باسم `VirtualCam-debug-apk`.

ملاحظة: ملف APK الناتج Debug للتجربة، وليس إصداراً موقعاً للنشر.
