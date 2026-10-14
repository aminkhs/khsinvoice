# امضای انتشار

نسخه debug برای آزمون است: با کلید debug همان رایانه‌ای امضا می‌شود که آن را ساخته و قابل اشکال‌زدایی است. برای انتشار عمومی یک keystore خصوصی بسازید، آن را خارج از repository نگه دارید و از آن پشتیبان امن بگیرید. `.gitignore` فایل‌های `*.jks`، `*.keystore` و `keystore.properties` را کنار می‌گذارد.

```text
keytool -genkeypair -v -keystore khs-invoice-release.jks -alias khs-invoice -keyalg RSA -keysize 4096 -validity 10000
```

ساده‌ترین راه امضا، Android Studio است: Build > Generate Signed App Bundle or APK. از خط فرمان، `gradlew :app:assembleRelease` یک APK کوچک‌شده و بدون امضا در `app/build/outputs/apk/release` می‌سازد که با `apksigner` امضا می‌شود:

```text
apksigner sign --ks khs-invoice-release.jks --out khs-invoice.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

APK بدون امضا قابل نصب نیست. کلید، فایل signing و گذرواژه در کد قرار نگیرند.

نسخه‌هایی که با کلیدهای متفاوت امضا شده‌اند روی هم نصب نمی‌شوند. کسی که از نسخه debug به نسخه امضاشده با کلید دیگر می‌رود باید اول در برنامه پشتیبان رمزگذاری‌شده بگیرد، برنامه را حذف کند، نسخه جدید را نصب کند و پشتیبان را بازیابی کند؛ حذف برنامه داده‌های آن را پاک می‌کند. پس کلید انتشار را از اولین نسخه عمومی ثابت نگه دارید.

قبل از انتشار، آزمون‌ها، lint، بازیابی روی نصب تازه، مجوزهای ردشده، SMS چندبخشی و قفل صفحه روی گوشی واقعی بررسی شوند.
