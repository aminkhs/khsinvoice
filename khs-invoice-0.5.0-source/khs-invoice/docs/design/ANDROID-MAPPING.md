# KHS Design System در KHS Invoice

ظاهر برنامه از KHS Design System نسخه ۱.۱ می‌آید. tokens و متن راهنمای آن در همین پوشه‌اند: [tokens.json](tokens.json) و [design-system.md](design-system.md).

* پالت: lime `#D4FA56` برای اکشن پرشده با متن ink؛ سطح feature به رنگ `#161715`؛ پس‌زمینه روشن `#F5F5F2`؛ کارت سفید با حاشیه آرام و سطح‌های ثانویه stone/mint.
* پوسته تیره از tokens.css: canvas `#10110F`، surface `#1C1E1A`، surface-alt `#2A2D27`، border `#3F453A`، muted `#BAC0B5`.
* قلم Estedad به صورت variable و وزن‌های ۱۰۰ تا ۹۰۰ در APK است. متن فارسی tracking صفر، سطرهای باز و ارقام tabular دارد. Inter نیز به عنوان قلم همراه لاتین بسته‌بندی شده است؛ متن مختلط فارسی با Estedad می‌ماند.
* شکل‌ها: کنترل ۱۲، فیلد ۲۰، کارت ۲۸، feature ۴۰ و اکشن pill. دکمه‌ها حداقل ۴۸ dp؛ فاصله‌های اصلی مضرب چهار و gutter تلفن ۱۶ dp، تبلت ۲۴ dp هستند.
* درآمد با success، هشدار با warning و خطا با error مستقل از lime نمایش داده می‌شوند. متن اکشن ثانویه ink است؛ lime روی سطح روشن برای متن معمولی استفاده نمی‌شود. حلقه فوکوس و فیلد فعال blue است.
* پنج صفحه، آغاز کار، ویرایشگر، انتخابگرها، اعلان/آیکن برنامه و نمودارها از لایه طراحی بومی استفاده می‌کنند. CSS یا WebView در برنامه اضافه نشده است.
* رنگ پویا همچنان انتخاب صریح کاربر است و پالت Material را جایگزین می‌کند. حالت پیش‌فرض هویت KHS را حفظ می‌کند.

کد مرجع: `app/src/main/java/ir/hesab/app/ui/Theme.kt` و `KhsComponents.kt`. مجوز OFL قلم‌ها داخل APK در `assets/licenses` است. منبع binaries: Google Fonts repository، `ofl/estedad/Estedad[wght].ttf` و `ofl/inter/Inter[opsz,wght].ttf`.

تصاویر QA فقط داده مصنوعی آزمون را نمایش می‌دهند؛ در نصب تازه هیچ تراکنش نمونه‌ای درج نمی‌شود.
