<p align="center">
  <img src="docs/logo.gif" width="150" alt="Magen animated logo">
</p>

<h1 align="center">Magen Download</h1>

<p align="center">
  <img src="docs/alisha.png" width="420" alt="Alisha character">
</p>

<p align="center"><strong>مرحبًا! أنا أليشا. أرسل أي رابط وسأساعدك على تنزيل الفيديو أو الصوت بسهولة.</strong></p>
<p align="center"><em>Hello! I’m Alisha. Send me a link and I’ll help you download video or audio with ease.</em></p>

<p align="center">
  <a href="https://github.com/magen-gillan/Magen-download/releases/download/v2.2.11/Magen-download-2.2.11-arm64.apk"><img src="https://img.shields.io/badge/%E2%AC%87%EF%B8%8F_%D8%AA%D8%AD%D9%85%D9%8A%D9%84_%D8%A7%D9%84%D8%AA%D8%B7%D8%A8%D9%8A%D9%82-00C853?style=for-the-badge&logo=android&logoColor=white" alt="تحميل آخر إصدار"></a>
</p>

<p align="center"><a href="https://github.com/magen-gillan/Magen-download/releases/latest">عرض جميع الإصدارات | View all releases</a></p>

---

## عن التطبيق | About

**العربية:** Magen Download تطبيق Android لتحميل الفيديوهات والأغاني من آلاف المواقع، معتمد على yt-dlp وواجهة عربية/إنجليزية واضحة. اختر الوضع البسيط للتحميل السريع أو الوضع المتقدم للتحكم الكامل في الصيغة والجودة والترجمات.

**English:** Magen Download is an Android app for downloading videos and audio from thousands of websites. Powered by yt-dlp, it offers a clear Arabic/English interface, a simple mode for quick downloads, and an advanced mode for full format, quality, subtitle, and audio-conversion control.

## المميزات | Features

- مشاركة أي رابط من التطبيقات الأخرى لبدء التحميل مباشرة.
- Share a link from another app to start downloading immediately.
- تحميل في الخلفية مع إشعار يوضح التقدم.
- Background downloads with progress notifications.
- استكمال التحميلات المتوقفة أو المتقطعة.
- Resume interrupted or paused downloads.
- تحويل الفيديو إلى MP3 ودمج الترجمات.
- Convert videos to MP3 and merge subtitles.
- اقتراح تلقائي عند نسخ الرابط.
- Automatic link detection after copying a URL.
- وضع بسيط ووضع متقدم يناسبان المبتدئ والمستخدم المحترف.
- Simple and advanced modes for beginners and power users.
- أذونات مرتبة من خلال خطوة واحدة عند التشغيل الأول.
- Streamlined one-step permission flow on first launch.
- بدون إعلانات أو تتبع، ومفتوح المصدر.
- No ads or tracking; fully open source.

## التثبيت | Installation

1. اضغط زر التحميل الأخضر أعلاه لتنزيل أحدث APK لمعمارية arm64-v8a.
2. ثبّت الملف واسمح بالتثبيت من مصادر غير معروفة.
3. اختر الوضع، امنح الأذونات، ثم شارك رابط فيديو أو صوت.

1. Press the green button above to download the latest arm64-v8a APK.
2. Install it and allow installation from unknown sources.
3. Choose a mode, grant permissions, and share a video or audio link.

**الإصدار الحالي | Current release:** v2.2.11.

## البناء والترخيص | Build & License

```bash
./gradlew :app:assembleGenericRelease -PnoSplits "-PABI_FILTERS=arm64-v8a"
```

مرخص تحت GPL-3.0، ومبني على [Seal](https://github.com/JunkFood02/Seal) وyt-dlp.

Licensed under GPL-3.0; based on [Seal](https://github.com/JunkFood02/Seal) and yt-dlp.