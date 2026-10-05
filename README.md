# حساب‌یار — پروژه Android + ساخت آنلاین APK

این پروژه برای ساخت APK بدون نصب Android Studio آماده شده است.

## ساخت APK با GitHub Actions
1. یک Repository در GitHub بساز.
2. تمام محتویات این پوشه را داخل Repository آپلود کن.
3. به تب **Actions** برو.
4. Workflow با نام **Build HesabYar APK** را انتخاب کن.
5. روی **Run workflow** بزن.
6. بعد از موفقیت Build، در صفحه اجرای Workflow بخش **Artifacts** را باز کن.
7. فایل `HesabYar-APK` را دانلود و از حالت ZIP خارج کن.
8. فایل `app-debug.apk` را روی گوشی اندرویدی نصب کن.

Workflow از Java 17 و Gradle 8.11 استفاده می‌کند و APK Debug تولید می‌کند.

<!-- Runner test: 2026-10-05 -->
