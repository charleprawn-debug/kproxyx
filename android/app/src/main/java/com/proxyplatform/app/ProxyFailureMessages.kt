package com.proxyplatform.app

import java.util.Locale

/** Turns technical connection failures into concise localized explanations and next steps. */
internal object ProxyFailureMessages {
    private data class Advice(val reason: String, val action: String)

    fun connection(advanced: Boolean, details: String?): String {
        val raw = details.orEmpty()
        if ((raw.contains("\nالسبب:") && raw.contains("\nالحل:")) ||
            (raw.contains("\nReason:") && raw.contains("\nAction:"))) return raw
        val text = raw.lowercase(Locale.ROOT)
        val advice = when {
            has(text, "407", "authentication failed", "authentication rejected", "auth failed", "credentials rejected", "رفض البروكسي بيانات المصادقة", "رفض خادم socks5", "مصادقة", "كلمة المرور", "كلمة مرور", "اسم المستخدم", "اسم مستخدم") -> Advice(
                tr("خادم البروكسي رفض بيانات تسجيل الدخول أو أن المصادقة مفعّلة/معطّلة بشكل غير مطابق.", "The proxy server rejected the credentials, or the authentication setting does not match the server."),
                tr("راجع اسم المستخدم وكلمة المرور من مزوّد البروكسي، وتأكد أن خيار المصادقة يطابق بيانات الخادم ثم أعد المحاولة.", "Check the username and password provided by your proxy provider, make sure the authentication setting matches the server, and try again."),
            )
            has(text, "udp associate", "udp relay", "does not support udp", "لا يمنح udp", "لا يدعم udp", "رفض udp") -> Advice(
                tr("خادم SOCKS5 لم يوفّر مرحّل UDP المطلوب لبعض الاتصالات مثل WebRTC.", "The SOCKS5 server did not provide the UDP relay required for some connections, such as WebRTC."),
                tr("استخدم ملف SOCKS5 يدعم UDP، أو اختر ملفًا آخر. لن يمرّر وضع VPN هذا المرور مباشرةً خارج البروكسي.", "Use a SOCKS5 profile that supports UDP, or choose another profile. VPN mode will not route this traffic directly outside the proxy."),
            )
            has(text, "unknownhostexception", "unable to resolve host", "unknown host", "name or service not known", "nodename nor servname", "تعذر حل اسم", "تعذر العثور على المضيف") -> Advice(
                tr("تعذّر العثور على عنوان خادم البروكسي؛ قد يكون اسم المضيف مكتوبًا خطأ أو أن DNS غير متاح.", "The proxy server address could not be resolved. The hostname may be incorrect, or DNS may be unavailable."),
                tr("راجع عنوان الخادم، وتأكد من اتصال الإنترنت، ثم جرّب مجددًا أو استخدم عنوان IP الذي يقدّمه مزوّدك.", "Check the server address and your internet connection, then try again or use the IP address supplied by your provider."),
            )
            advanced && has(text, "timezone", "time zone", "set-timezone", "منطقة زمنية", "المنطقة الزمنية") -> Advice(
                tr("لم يتمكن Android من تطبيق المنطقة الزمنية أو استعادتها عبر التصحيح اللاسلكي.", "Android could not apply or restore the time zone using wireless debugging."),
                tr("فعّل Wireless debugging، وأبقِ الهاتف والتطبيق متصلين بالشبكة نفسها، ثم أعد المحاولة. إذا فشلت الاستعادة فأعد اتصال ADB واضغط استعادة الإعدادات.", "Enable Wireless debugging and keep the phone and app on the same network, then try again. If restoration fails, reconnect ADB and select Restore settings."),
            )
            advanced && has(text, "adb", "wireless debugging", "wireless adb", "remote shell", "shell command", "settings put", "global_http_proxy", "http_proxy", "بروكسي النظام", "إعداد النظام", "أمر shell", "أمر adb", "التصحيح اللاسلكي") -> Advice(
                tr("اتصال ADB انقطع أو رفض Android أمر إعداد بروكسي النظام.", "The ADB connection was interrupted, or Android rejected the system proxy configuration command."),
                tr("تأكد أن Wireless debugging مفعّل والهاتف والتطبيق على الشبكة نفسها، ثم اضغط «إعادة الاتصال عبر ADB» أو أعد الاقتران.", "Make sure Wireless debugging is enabled and the phone and app are on the same network, then select Reconnect via ADB or pair again."),
            )
            has(text, "permission is not granted", "vpn permission", "لم يتم منح إذن vpn", "ألغى android إذن vpn", "vpn consent") -> Advice(
                tr("Android لم يمنح التطبيق إذن إنشاء اتصال VPN أو تم إلغاؤه.", "Android did not grant the app permission to create a VPN connection, or that permission was revoked."),
                tr("اضغط اتصال ووافق على نافذة طلب VPN. إذا رفضت الطلب سابقًا، أعد المحاولة ووافق عليه.", "Select Connect and approve the VPN request. If you previously declined, try again and approve it."),
            )
            has(text, "address already in use", "eaddrinuse", "bindexception", "cannot assign requested address", "المنفذ المحلي مستخدم") -> Advice(
                tr("المنفذ المحلي المطلوب مستخدم أو غير متاح على الهاتف.", "The required local port is already in use or unavailable on the phone."),
                tr("أوقف أي جلسة بروكسي سابقة وأعد المحاولة. إذا استمرت المشكلة، أعد تشغيل التطبيق والهاتف.", "Stop any previous proxy session and try again. If the problem persists, restart the app and the phone."),
            )
            has(text, "sockettimeoutexception", "connectexception", "failed to connect", "connection refused", "connection timed out", "timed out", "timeout", "network is unreachable", "no route to host", "reset by peer", "انتهت المهلة", "تعذر الاتصال بالخادم", "أغلق خادم البروكسي") -> Advice(
                tr("الهاتف لم يصل إلى خادم البروكسي؛ قد يكون الخادم متوقفًا أو العنوان/المنفذ غير صحيح أو الشبكة تحجب الاتصال.", "The phone could not reach the proxy server. The server may be offline, the address or port may be incorrect, or the network may be blocking the connection."),
                tr("راجع عنوان الخادم والمنفذ، وتأكد من اتصال الإنترنت ومن أن خادم البروكسي يعمل، ثم جرّب شبكة أخرى أو أعد المحاولة لاحقًا.", "Check the server address and port, verify your internet connection and that the proxy server is running, then try another network or try again later."),
            )
            has(text, "http 403", "response 403", "الاستجابة 403", "رفض البروكسي إنشاء نفق") -> Advice(
                tr("خادم البروكسي رفض فتح اتصال الإنترنت المطلوب؛ قد تكون الوجهة محجوبة أو أن صلاحية الخادم لا تسمح بها.", "The proxy server refused to open the requested internet connection. The destination may be blocked or not permitted by the server."),
                tr("تحقق من حالة اشتراك البروكسي وسياسة الوصول لدى مزوّد الخدمة، ثم جرّب خادمًا أو ملفًا آخر.", "Check your proxy subscription and the provider’s access policy, then try another server or profile."),
            )
            has(text, "protocol", "بروتوكول", "invalid config", "configuration is invalid", "إعداد sing-box") -> Advice(
                tr("نوع البروتوكول أو إعداد الخادم لا يطابق ما يقبله مزوّد البروكسي.", "The protocol or server configuration does not match your proxy provider’s requirements."),
                tr("تحقق هل الملف SOCKS5 أم HTTP، ثم اختر النوع المطابق وأعد إدخال بيانات الخادم من المصدر.", "Check whether the profile uses SOCKS5 or HTTP, select the matching type, and re-enter the server details from your provider."),
            )
            has(text, "sing-box", "libbox", "native engine", "محرك البروكسي", "محرك vpn") -> Advice(
                tr("محرك البروكسي لم يتمكن من تجهيز إعداد الاتصال على هذا الجهاز.", "The proxy engine could not prepare the connection on this device."),
                tr("تحقق من نوع البروكسي وبياناته، أوقف أي جلسة سابقة، ثم أعد المحاولة. إذا تكرر الخطأ صدّر سجل التشخيص.", "Check the proxy type and details, stop any previous session, and try again. If the error persists, export the diagnostic log."),
            )
            else -> Advice(
                if (advanced) tr("تعذّر إعداد بروكسي النظام أو تشغيل خدمته.", "Could not configure the system proxy or start its service.") else tr("تعذّر إعداد نفق VPN أو تشغيل خدمته.", "Could not configure the VPN tunnel or start its service."),
                tr("تأكد من صحة ملف البروكسي واتصال الإنترنت، ثم أعد المحاولة. إذا تكرر الخطأ، صدّر سجل التشخيص لتحديد الخطوة التي فشلت.", "Check the proxy profile and your internet connection, then try again. If the error persists, export the diagnostic log to identify the failed step."),
            )
        }
        val title = if (advanced) tr("تعذّر تشغيل الوضع المتقدم", "Could not start advanced mode") else tr("تعذّر تشغيل اتصال VPN", "Could not start the VPN connection")
        return format(title, advice)
    }

    fun fromThrowable(advanced: Boolean, failure: Throwable?): String =
        connection(advanced, throwableDetails(failure))

    fun mockLocation(details: String?): String {
        val text = details.orEmpty().lowercase(Locale.ROOT)
        val advice = when {
            has(text, "access_coarse_location", "coarse location", "approximate location", "الموقع التقريبي") -> Advice(
                tr("Google Play Services يحتاج إذن الموقع التقريبي لقبول الإحداثيات الوهمية.", "Google Play Services needs approximate location permission to accept mock coordinates."),
                tr("اسمح بإذن الموقع التقريبي عند طلبه. التطبيق لا يقرأ إحداثيات موقعك الحقيقي. إذا رفضت الإذن، فعّله من إعدادات أذونات التطبيق ثم أعد الاتصال.", "Grant approximate location permission when prompted. The app does not read your actual location coordinates. If you denied permission, enable it in the app permission settings and reconnect."),
            )
            has(text, "appop", "mock location app", "access_mock_location", "mock location disabled", "test provider", "لم يتم اختيار", "لم يعتمد اختيار", "اختيار التطبيق", "مزوّد الموقع الوهمي") -> Advice(
                tr("Android لم يعتمد Kun cloak كتطبيق الموقع الوهمي.", "Android has not selected Kun cloak as the mock location app."),
                tr("افتح خيارات المطوّر ← اختيار تطبيق الموقع الوهمي، واختر Kun cloak، ثم ارجع وأعد التحقق.", "Open Developer options → Select mock location app, choose Kun cloak, then return and check again."),
            )
            has(text, "fused", "google play", "setmockmode", "setmocklocation", "play services") -> Advice(
                tr("خدمات الموقع المدمجة في Google لم تقبل الإحداثيات الوهمية أو لا تعمل بشكل سليم.", "Google’s fused location services did not accept the mock coordinates or are not working correctly."),
                tr("تأكد من اختيار Kun cloak في خيارات المطوّر، ومنح إذن الموقع التقريبي، وتحديث Google Play Services؛ ثم أعد تشغيل الموقع الوهمي.", "Select Kun cloak in Developer options, grant approximate location permission, and update Google Play Services, then restart mock location."),
            )
            has(text, "ipwho", "geoip", "unknownhostexception", "sockettimeout", "timed out", "تعذر تحديد موقع البروكسي") -> Advice(
                tr("تعذّر الحصول على موقع خروج البروكسي؛ غالبًا تعذر الوصول إلى خدمة تحديد الموقع عبر البروكسي.", "Could not determine the proxy exit location. The location service may be unreachable through the proxy."),
                tr("تأكد أولًا أن البروكسي متصل ويصل إلى الإنترنت. أو اختر الوضع اليدوي وأدخل خط العرض والطول.", "First, make sure the proxy is connected and has internet access. Alternatively, select manual mode and enter the latitude and longitude."),
            )
            has(text, "latitude", "longitude", "خط العرض", "خط الطول", "coordinate", "إحداثيات") -> Advice(
                tr("الإحداثيات المدخلة خارج النطاق الجغرافي المسموح أو غير مكتملة.", "The coordinates are incomplete or outside the valid geographic range."),
                tr("أدخل خط عرض بين ‎-90 و90‎ وخط طول بين ‎-180 و180‎، مع التأكد من استخدام أرقام عشرية صحيحة.", "Enter a latitude between -90 and 90 and a longitude between -180 and 180, using valid decimal numbers."),
            )
            else -> Advice(
                tr("تعذّر إرسال موقع البروكسي الوهمي إلى Android أو Google Maps.", "Could not send the proxy’s mock location to Android or Google Maps."),
                tr("تحقق من اختيار التطبيق وإذن الموقع التقريبي واتصال البروكسي، ثم أوقف الموقع الوهمي وأعد تشغيله. التفاصيل الفنية محفوظة في سجل التشخيص.", "Check the selected mock location app, approximate location permission, and proxy connection, then stop and restart mock location. Technical details are saved in the diagnostic log."),
            )
        }
        return format(tr("تعذّر تفعيل الموقع الوهمي", "Could not enable mock location"), advice)
    }

    fun pairing(details: String?): String {
        val text = details.orEmpty().lowercase(Locale.ROOT)
        val advice = when {
            has(text, "invalid pairing code", "wrong pairing code", "pairing code rejected", "رمز الاقتران غير صحيح", "انتهت صلاحية الرمز", "رمز اقتران غير صالح") -> Advice(
                tr("رمز الاقتران غير صحيح أو انتهت صلاحيته.", "The pairing code is incorrect or has expired."),
                tr("افتح Pair device with pairing code من جديد واستخدم الرمز الأحدث قبل إغلاق الشاشة؛ الرمز صالح لجلسة قصيرة فقط.", "Reopen Pair device with pairing code and use the latest code before closing the screen. The code is valid only briefly."),
            )
            has(text, "unsupported_android_version", "android 11", "غير مدعوم") -> Advice(
                tr("هذا الجهاز أو إصدار Android لا يدعم الاقتران اللاسلكي المطلوب للوضع المتقدم.", "This device or Android version does not support the wireless pairing required for advanced mode."),
                tr("استخدم وضع VPN، أو حدّث Android إذا كان تحديث رسمي متاحًا.", "Use VPN mode, or update Android if an official update is available."),
            )
            has(text, "sockettimeoutexception", "connectexception", "failed to connect", "connection refused", "timed out", "timeout", "network is unreachable") -> Advice(
                tr("الهاتف لم يكمل اتصال التصحيح اللاسلكي؛ قد يكون الاتصال متوقفًا أو الشبكة تغيّرت.", "The phone could not complete the wireless debugging connection. The connection may have stopped or the network may have changed."),
                tr("فعّل Wireless debugging، وأبقِ شاشة الاقتران مفتوحة، وتأكد أن الهاتف والتطبيق على الشبكة نفسها. أعد الاقتران بالرمز الجديد إذا لزم.", "Enable Wireless debugging, keep the pairing screen open, and make sure the phone and app are on the same network. Pair again with a new code if necessary."),
            )
            else -> Advice(
                tr("لم يكتمل اقتران ADB أو لم يقبل Android رمز الاقتران.", "ADB pairing did not complete, or Android rejected the pairing code."),
                tr("تأكد من تفعيل Wireless debugging، واستخدم رمزًا جديدًا من شاشة Pair device with pairing code، ثم أعد المحاولة.", "Make sure Wireless debugging is enabled, use a new code from Pair device with pairing code, and try again."),
            )
        }
        return format(tr("تعذّر اقتران التصحيح اللاسلكي", "Could not pair wireless debugging"), advice)
    }

    fun advancedRestore(details: String?): String {
        val text = details.orEmpty().lowercase(Locale.ROOT)
        val advice = if (has(text, "timezone", "time zone", "منطقة زمنية", "المنطقة الزمنية")) {
            Advice(
                tr("لم يتمكن ADB من إعادة المنطقة الزمنية أو إعداداتها السابقة.", "ADB could not restore the previous time zone or its settings."),
                tr("فعّل Wireless debugging وأعد اتصال ADB، ثم اضغط «استعادة إعدادات الجلسة السابقة». أبقِ التطبيق مفتوحًا حتى يؤكد اكتمال الاستعادة.", "Enable Wireless debugging and reconnect ADB, then select Restore previous session settings. Keep the app open until restoration is confirmed."),
            )
        } else {
            Advice(
                tr("Android لم يقبل استعادة إعداد بروكسي النظام، لذلك أبقى التطبيق الخدمة المحلية نشطة لتجنّب قطع المرور.", "Android did not accept restoration of the system proxy setting, so the app kept the local service running to avoid interrupting traffic."),
                tr("لا توقف التطبيق بالقوة. أعد تفعيل Wireless debugging واتصال ADB، ثم اضغط «إعادة الاتصال» وأعد محاولة إيقاف/استعادة البروكسي.", "Do not force-stop the app. Re-enable Wireless debugging and reconnect ADB, then select Reconnect and try stopping or restoring the proxy again."),
            )
        }
        return format(tr("لم تكتمل استعادة إعدادات الهاتف", "Phone settings restoration did not complete"), advice)
    }

    fun settingsPage(pageName: String): String = format(
        tr("تعذّر فتح صفحة الإعدادات المطلوبة", "Could not open the requested settings page"),
        Advice(
            tr("إصدار Android أو الشركة المصنّعة لا يوفّر اختصارًا مباشرًا لهذه الصفحة.", "This Android version or device manufacturer does not provide a direct shortcut to this page."),
            tr("افتح إعدادات الهاتف يدويًا وابحث عن «$pageName»، ثم ارجع إلى التطبيق للمتابعة.", "Open the phone settings manually and look for “$pageName”, then return to the app to continue."),
        ),
    )

    fun savedProxy(details: String?, action: String): String {
        val text = details.orEmpty().lowercase(Locale.ROOT)
        val advice = if (has(text, "decrypt", "keystore", "badpadding", "tag mismatch", "فك التشفير", "التشفير")) {
            Advice(
                tr("تعذّر فتح بيانات البروكسي المحفوظة؛ قد تكون مفاتيح الحماية تغيّرت بعد استعادة نسخة احتياطية أو إعادة تثبيت التطبيق.", "Could not open the saved proxy data. Security keys may have changed after restoring a backup or reinstalling the app."),
                tr("أعد إدخال بيانات البروكسي واحفظها من جديد. لا تُرسل كلمة المرور لأي شخص.", "Re-enter and save the proxy details. Do not share your password with anyone."),
            )
        } else {
            Advice(tr("تعذّر $action بسبب مشكلة في قاعدة البيانات المحلية.", "Could not complete $action because of a problem with the local database."), tr("أعد المحاولة. إذا استمرت المشكلة، احفظ بيانات البروكسي خارج التطبيق ثم أعد إدخال الملف.", "Try again. If the problem persists, save the proxy details outside the app and re-enter the profile."))
        }
        return format(tr("تعذّر $action", "Could not complete $action"), advice)
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
        tr("$title\nالسبب: ${advice.reason}\nالحل: ${advice.action}", "$title\nReason: ${advice.reason}\nAction: ${advice.action}")

    private fun has(text: String, vararg candidates: String): Boolean =
        candidates.any { it.lowercase(Locale.ROOT) in text }
}
