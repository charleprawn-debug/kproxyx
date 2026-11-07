package com.proxyplatform.app

import java.util.Locale

/** Turns technical connection failures into concise Arabic explanations and next steps. */
internal object ProxyFailureMessages {
    private data class Advice(val reason: String, val action: String)

    fun connection(advanced: Boolean, details: String?): String {
        val raw = details.orEmpty()
        if (raw.contains("\nالسبب:") && raw.contains("\nالحل:")) return raw
        val text = raw.lowercase(Locale.ROOT)
        val advice = when {
            has(text, "407", "authentication failed", "authentication rejected", "auth failed", "credentials rejected", "رفض البروكسي بيانات المصادقة", "رفض خادم socks5", "مصادقة", "كلمة المرور", "كلمة مرور", "اسم المستخدم", "اسم مستخدم") -> Advice(
                "خادم البروكسي رفض بيانات تسجيل الدخول أو أن المصادقة مفعّلة/معطّلة بشكل غير مطابق.",
                "راجع اسم المستخدم وكلمة المرور من مزوّد البروكسي، وتأكد أن خيار المصادقة يطابق بيانات الخادم ثم أعد المحاولة.",
            )
            has(text, "udp associate", "udp relay", "does not support udp", "لا يمنح udp", "لا يدعم udp", "رفض udp") -> Advice(
                "خادم SOCKS5 لم يوفّر مرحّل UDP المطلوب لبعض الاتصالات مثل WebRTC.",
                "استخدم ملف SOCKS5 يدعم UDP، أو اختر ملفًا آخر. لن يمرّر وضع VPN هذا المرور مباشرةً خارج البروكسي.",
            )
            has(text, "unknownhostexception", "unable to resolve host", "unknown host", "name or service not known", "nodename nor servname", "تعذر حل اسم", "تعذر العثور على المضيف") -> Advice(
                "تعذّر العثور على عنوان خادم البروكسي؛ قد يكون اسم المضيف مكتوبًا خطأ أو أن DNS غير متاح.",
                "راجع عنوان الخادم، وتأكد من اتصال الإنترنت، ثم جرّب مجددًا أو استخدم عنوان IP الذي يقدّمه مزوّدك.",
            )
            advanced && has(text, "timezone", "time zone", "set-timezone", "منطقة زمنية", "المنطقة الزمنية") -> Advice(
                "لم يتمكن Android من تطبيق المنطقة الزمنية أو استعادتها عبر التصحيح اللاسلكي.",
                "فعّل Wireless debugging، وأبقِ الهاتف والتطبيق متصلين بالشبكة نفسها، ثم أعد المحاولة. إذا فشلت الاستعادة فأعد اتصال ADB واضغط استعادة الإعدادات.",
            )
            advanced && has(text, "adb", "wireless debugging", "wireless adb", "remote shell", "shell command", "settings put", "global_http_proxy", "http_proxy", "بروكسي النظام", "إعداد النظام", "أمر shell", "أمر adb", "التصحيح اللاسلكي") -> Advice(
                "اتصال ADB انقطع أو رفض Android أمر إعداد بروكسي النظام.",
                "تأكد أن Wireless debugging مفعّل والهاتف والتطبيق على الشبكة نفسها، ثم اضغط «إعادة الاتصال عبر ADB» أو أعد الاقتران.",
            )
            has(text, "permission is not granted", "vpn permission", "لم يتم منح إذن vpn", "ألغى android إذن vpn", "vpn consent") -> Advice(
                "Android لم يمنح التطبيق إذن إنشاء اتصال VPN أو تم إلغاؤه.",
                "اضغط اتصال ووافق على نافذة طلب VPN. إذا رفضت الطلب سابقًا، أعد المحاولة ووافق عليه.",
            )
            has(text, "address already in use", "eaddrinuse", "bindexception", "cannot assign requested address", "المنفذ المحلي مستخدم") -> Advice(
                "المنفذ المحلي المطلوب مستخدم أو غير متاح على الهاتف.",
                "أوقف أي جلسة بروكسي سابقة وأعد المحاولة. إذا استمرت المشكلة، أعد تشغيل التطبيق والهاتف.",
            )
            has(text, "sockettimeoutexception", "connectexception", "failed to connect", "connection refused", "connection timed out", "timed out", "timeout", "network is unreachable", "no route to host", "reset by peer", "انتهت المهلة", "تعذر الاتصال بالخادم", "أغلق خادم البروكسي") -> Advice(
                "الهاتف لم يصل إلى خادم البروكسي؛ قد يكون الخادم متوقفًا أو العنوان/المنفذ غير صحيح أو الشبكة تحجب الاتصال.",
                "راجع عنوان الخادم والمنفذ، وتأكد من اتصال الإنترنت ومن أن خادم البروكسي يعمل، ثم جرّب شبكة أخرى أو أعد المحاولة لاحقًا.",
            )
            has(text, "http 403", "response 403", "الاستجابة 403", "رفض البروكسي إنشاء نفق") -> Advice(
                "خادم البروكسي رفض فتح اتصال الإنترنت المطلوب؛ قد تكون الوجهة محجوبة أو أن صلاحية الخادم لا تسمح بها.",
                "تحقق من حالة اشتراك البروكسي وسياسة الوصول لدى مزوّد الخدمة، ثم جرّب خادمًا أو ملفًا آخر.",
            )
            has(text, "protocol", "بروتوكول", "invalid config", "configuration is invalid", "إعداد sing-box") -> Advice(
                "نوع البروتوكول أو إعداد الخادم لا يطابق ما يقبله مزوّد البروكسي.",
                "تحقق هل الملف SOCKS5 أم HTTP، ثم اختر النوع المطابق وأعد إدخال بيانات الخادم من المصدر.",
            )
            has(text, "sing-box", "libbox", "native engine", "محرك البروكسي", "محرك vpn") -> Advice(
                "محرك البروكسي لم يتمكن من تجهيز إعداد الاتصال على هذا الجهاز.",
                "تحقق من نوع البروكسي وبياناته، أوقف أي جلسة سابقة، ثم أعد المحاولة. إذا تكرر الخطأ صدّر سجل التشخيص.",
            )
            else -> Advice(
                if (advanced) "تعذّر إعداد بروكسي النظام أو تشغيل خدمته." else "تعذّر إعداد نفق VPN أو تشغيل خدمته.",
                "تأكد من صحة ملف البروكسي واتصال الإنترنت، ثم أعد المحاولة. إذا تكرر الخطأ، صدّر سجل التشخيص لتحديد الخطوة التي فشلت.",
            )
        }
        val title = if (advanced) "تعذّر تشغيل الوضع المتقدم" else "تعذّر تشغيل اتصال VPN"
        return format(title, advice)
    }

    fun fromThrowable(advanced: Boolean, failure: Throwable?): String =
        connection(advanced, throwableDetails(failure))

    fun mockLocation(details: String?): String {
        val text = details.orEmpty().lowercase(Locale.ROOT)
        val advice = when {
            has(text, "access_coarse_location", "coarse location", "approximate location", "الموقع التقريبي") -> Advice(
                "Google Play Services يحتاج إذن الموقع التقريبي لقبول الإحداثيات الوهمية.",
                "اسمح بإذن الموقع التقريبي عند طلبه. التطبيق لا يقرأ إحداثيات موقعك الحقيقي. إذا رفضت الإذن، فعّله من إعدادات أذونات التطبيق ثم أعد الاتصال.",
            )
            has(text, "appop", "mock location app", "access_mock_location", "mock location disabled", "test provider", "لم يتم اختيار", "لم يعتمد اختيار", "اختيار التطبيق", "مزوّد الموقع الوهمي") -> Advice(
                "Android لم يعتمد Proxy Platform كتطبيق الموقع الوهمي.",
                "افتح خيارات المطوّر ← اختيار تطبيق الموقع الوهمي، واختر Proxy Platform، ثم ارجع وأعد التحقق.",
            )
            has(text, "fused", "google play", "setmockmode", "setmocklocation", "play services") -> Advice(
                "خدمات الموقع المدمجة في Google لم تقبل الإحداثيات الوهمية أو لا تعمل بشكل سليم.",
                "تأكد من اختيار Proxy Platform في خيارات المطوّر، ومنح إذن الموقع التقريبي، وتحديث Google Play Services؛ ثم أعد تشغيل الموقع الوهمي.",
            )
            has(text, "ipwho", "geoip", "unknownhostexception", "sockettimeout", "timed out", "تعذر تحديد موقع البروكسي") -> Advice(
                "تعذّر الحصول على موقع خروج البروكسي؛ غالبًا تعذر الوصول إلى خدمة تحديد الموقع عبر البروكسي.",
                "تأكد أولًا أن البروكسي متصل ويصل إلى الإنترنت. أو اختر الوضع اليدوي وأدخل خط العرض والطول.",
            )
            has(text, "latitude", "longitude", "خط العرض", "خط الطول", "coordinate", "إحداثيات") -> Advice(
                "الإحداثيات المدخلة خارج النطاق الجغرافي المسموح أو غير مكتملة.",
                "أدخل خط عرض بين ‎-90 و90‎ وخط طول بين ‎-180 و180‎، مع التأكد من استخدام أرقام عشرية صحيحة.",
            )
            else -> Advice(
                "تعذّر إرسال موقع البروكسي الوهمي إلى Android أو Google Maps.",
                "تحقق من اختيار التطبيق وإذن الموقع التقريبي واتصال البروكسي، ثم أوقف الموقع الوهمي وأعد تشغيله. التفاصيل الفنية محفوظة في سجل التشخيص.",
            )
        }
        return format("تعذّر تفعيل الموقع الوهمي", advice)
    }

    fun pairing(details: String?): String {
        val text = details.orEmpty().lowercase(Locale.ROOT)
        val advice = when {
            has(text, "invalid pairing code", "wrong pairing code", "pairing code rejected", "رمز الاقتران غير صحيح", "انتهت صلاحية الرمز", "رمز اقتران غير صالح") -> Advice(
                "رمز الاقتران غير صحيح أو انتهت صلاحيته.",
                "افتح Pair device with pairing code من جديد واستخدم الرمز الأحدث قبل إغلاق الشاشة؛ الرمز صالح لجلسة قصيرة فقط.",
            )
            has(text, "unsupported_android_version", "android 11", "غير مدعوم") -> Advice(
                "هذا الجهاز أو إصدار Android لا يدعم الاقتران اللاسلكي المطلوب للوضع المتقدم.",
                "استخدم وضع VPN، أو حدّث Android إذا كان تحديث رسمي متاحًا.",
            )
            has(text, "sockettimeoutexception", "connectexception", "failed to connect", "connection refused", "timed out", "timeout", "network is unreachable") -> Advice(
                "الهاتف لم يكمل اتصال التصحيح اللاسلكي؛ قد يكون الاتصال متوقفًا أو الشبكة تغيّرت.",
                "فعّل Wireless debugging، وأبقِ شاشة الاقتران مفتوحة، وتأكد أن الهاتف والتطبيق على الشبكة نفسها. أعد الاقتران بالرمز الجديد إذا لزم.",
            )
            else -> Advice(
                "لم يكتمل اقتران ADB أو لم يقبل Android رمز الاقتران.",
                "تأكد من تفعيل Wireless debugging، واستخدم رمزًا جديدًا من شاشة Pair device with pairing code، ثم أعد المحاولة.",
            )
        }
        return format("تعذّر اقتران التصحيح اللاسلكي", advice)
    }

    fun advancedRestore(details: String?): String {
        val text = details.orEmpty().lowercase(Locale.ROOT)
        val advice = if (has(text, "timezone", "time zone", "منطقة زمنية", "المنطقة الزمنية")) {
            Advice(
                "لم يتمكن ADB من إعادة المنطقة الزمنية أو إعداداتها السابقة.",
                "فعّل Wireless debugging وأعد اتصال ADB، ثم اضغط «استعادة إعدادات الجلسة السابقة». أبقِ التطبيق مفتوحًا حتى يؤكد اكتمال الاستعادة.",
            )
        } else {
            Advice(
                "Android لم يقبل استعادة إعداد بروكسي النظام، لذلك أبقى التطبيق الخدمة المحلية نشطة لتجنّب قطع المرور.",
                "لا توقف التطبيق بالقوة. أعد تفعيل Wireless debugging واتصال ADB، ثم اضغط «إعادة الاتصال» وأعد محاولة إيقاف/استعادة البروكسي.",
            )
        }
        return format("لم تكتمل استعادة إعدادات الهاتف", advice)
    }

    fun settingsPage(pageName: String): String = format(
        "تعذّر فتح صفحة الإعدادات المطلوبة",
        Advice(
            "إصدار Android أو الشركة المصنّعة لا يوفّر اختصارًا مباشرًا لهذه الصفحة.",
            "افتح إعدادات الهاتف يدويًا وابحث عن «$pageName»، ثم ارجع إلى التطبيق للمتابعة.",
        ),
    )

    fun savedProxy(details: String?, action: String): String {
        val text = details.orEmpty().lowercase(Locale.ROOT)
        val advice = if (has(text, "decrypt", "keystore", "badpadding", "tag mismatch", "فك التشفير", "التشفير")) {
            Advice(
                "تعذّر فتح بيانات البروكسي المحفوظة؛ قد تكون مفاتيح الحماية تغيّرت بعد استعادة نسخة احتياطية أو إعادة تثبيت التطبيق.",
                "أعد إدخال بيانات البروكسي واحفظها من جديد. لا تُرسل كلمة المرور لأي شخص.",
            )
        } else {
            Advice("تعذّر $action بسبب مشكلة في قاعدة البيانات المحلية.", "أعد المحاولة. إذا استمرت المشكلة، احفظ بيانات البروكسي خارج التطبيق ثم أعد إدخال الملف.")
        }
        return format("تعذّر $action", advice)
    }

    private fun throwableDetails(failure: Throwable?): String {
        if (failure == null) return ""
        val chain = generateSequence(failure) { it.cause }.take(6)
            .map { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
            .distinct()
            .toList()
        return chain.joinToString(" | ")
    }

    private fun format(title: String, advice: Advice): String =
        "$title\nالسبب: ${advice.reason}\nالحل: ${advice.action}"

    private fun has(text: String, vararg candidates: String): Boolean =
        candidates.any { it.lowercase(Locale.ROOT) in text }
}
