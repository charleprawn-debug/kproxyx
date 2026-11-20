/* Local UI copy only: never translate server data, form values, or API identifiers. */
(() => {
  'use strict';
  const english = {
    'لوحة الإدارة': 'Administration',
    'تسجيل الدخول': 'Sign in',
    'لوحة الإدارة الآمنة': 'Secure administration',
    'البريد الإلكتروني': 'Email address',
    'كلمة المرور': 'Password',
    'دخول آمن': 'Sign in securely',
    'مركز إدارة Kun cloak': 'Kun cloak administration',
    'إدارة المستخدمين والاشتراكات والرصيد من مكان واحد': 'Manage users, subscriptions and balances in one place',
    'تسجيل الخروج': 'Sign out',
    'نظرة عامة على المنصة': 'Platform overview',
    'تابع الحسابات والاشتراكات وطلبات الشحن واتخذ الإجراءات بسرعة وأمان.': 'Monitor accounts, subscriptions and top-up requests, and take action quickly and securely.',
    'الإحصائيات': 'Statistics', 'المستخدمون': 'Users', 'خطط الاشتراك': 'Subscription plans',
    'طلبات الشحن': 'Top-up requests', 'طرق الشحن': 'Top-up methods', 'الأسرار': 'Secrets', 'صفحة الهبوط': 'Landing page',
    'اتصال VPN على مستوى الجهاز': 'Device-wide VPN connection', 'الوضع المتقدم': 'Advanced mode',
    'إعدادات الشبكة والاتصال المتقدمة': 'Advanced network and connection settings',
    'أدوات وإعدادات شرائح SIM': 'SIM tools and settings', 'محاكاة الموقع': 'Location simulation',
    'صلاحية أدوات محاكاة الموقع': 'Access to location simulation tools', 'بث الشبكة': 'Network sharing', 'تشغيل ميزة بث الشبكة': 'Enable network sharing',
    'تعذر تنفيذ الطلب': 'Unable to complete the request', 'تعذر الاتصال بالخادم': 'Unable to connect to the server', 'حفظ التغييرات': 'Save changes', 'إلغاء': 'Cancel',
    'تم حفظ التغييرات بنجاح': 'Changes saved successfully', 'تعذر تحميل بيانات اللوحة': 'Unable to load dashboard data',
    'إجمالي المستخدمين': 'Total users', 'الاشتراكات النشطة': 'Active subscriptions', 'طلبات الشحن المعلقة': 'Pending top-up requests', 'جلسات الاتصال': 'Connection sessions',
    'لوحة الإحصائيات': 'Statistics dashboard', 'مؤشرات حية للحسابات، الأرصدة، النشاط والاشتراكات': 'Live metrics for accounts, balances, activity and subscriptions',
    'تحديث الإحصائيات': 'Refresh statistics', 'إجمالي الحسابات': 'Total accounts', 'كل الحسابات المسجلة': 'All registered accounts',
    'نشطون خلال 24 ساعة': 'Active in the last 24 hours', 'آخر دخول خلال يوم': 'Signed in within the last day',
    'نشطون خلال أسبوع': 'Active in the last week', 'آخر دخول خلال 7 أيام': 'Signed in within the last 7 days',
    'نشطون خلال شهر': 'Active in the last month', 'آخر دخول خلال 30 يوماً': 'Signed in within the last 30 days',
    'اشتراكات سارية حالياً': 'Currently active subscriptions', 'إجمالي الأرصدة': 'Total balances', 'محفظة منشأة': 'wallets created',
    'توزيع الاشتراكات حسب الباقة': 'Subscriptions by plan', 'لا توجد اشتراكات نشطة.': 'There are no active subscriptions.',
    'ملخص النشاط': 'Activity summary', 'نشاط اليوم': 'Today’s activity', 'نشاط الأسبوع': 'This week’s activity',
    'نشاط الشهر': 'This month’s activity', 'جلسات الاتصال خلال الشهر': 'Connection sessions this month',
    'أسرار بوابات الدفع': 'Payment gateway secrets', 'القيم تحفظ مشفرة ولا تظهر كاملة بعد الحفظ.': 'Values are encrypted and are not displayed in full after saving.',
    'لا تضع هذه المفاتيح داخل GitHub أو ملفات Android. بعد الحفظ يستخدمها السيرفر مباشرة في إنشاء عناوين OxaPay والتحقق من سرياتيل كاش وشام كاش.': 'Do not put these keys in GitHub or Android files. Once saved, the server uses them to create OxaPay addresses and verify Syriatel Cash and Sham Cash payments.',
    'اتركه فارغًا للإبقاء على القيمة الحالية': 'Leave blank to keep the current value', 'العمولة على الدافع': 'Payer pays the fee',
    'حالة OxaPay:': 'OxaPay status:', 'حالة API Syria:': 'API Syria status:', 'مهيأة': 'Configured', 'غير مهيأة': 'Not configured',
    'استعلام حساب سرياتيل أو GSM': 'Syriatel account or GSM query', 'حفظ الأسرار والإعدادات': 'Save secrets and settings',
    'تم حفظ إعدادات بوابات الدفع بشكل مشفر': 'Payment gateway settings saved securely',
    'صفحة الهبوط والتحديث': 'Landing page and updates', 'تحكم برابط تحميل التطبيق وسياسة التحديث الإجباري.': 'Manage the app download link and mandatory update policy.',
    'فتح الصفحة': 'Open page', 'عند تفعيل التحديث الإجباري، أي نسخة رقمها أقل من الحد الأدنى ستتوقف عن العمل وتظهر لها شاشة تحديث فقط.': 'When mandatory updates are enabled, versions below the minimum will stop working and display only the update screen.',
    'رابط التحميل المباشر': 'Direct download URL', 'حفظ رابط التحميل': 'Save download URL', 'تفعيل التحديث الإجباري': 'Enable mandatory updates',
    'الحد الأدنى لـ versionCode': 'Minimum versionCode', 'رابط التحديث': 'Update URL',
    'اتركه مساويًا لرابط APK أو ضع رابطًا مختلفًا': 'Use the APK URL or enter a different URL', 'رسالة التحديث': 'Update message',
    'حفظ إعدادات التحديث': 'Save update settings', 'حساب الدعم على Telegram': 'Telegram support account',
    'يظهر للمستخدم كرابط تواصل مباشر بدل كشف أي كلمة مرور.': 'Shown to users as a direct contact link without exposing a password.',
    'حفظ حساب الدعم': 'Save support account', 'تم تحديث رابط تحميل التطبيق': 'App download URL updated',
    'تم حفظ سياسة التحديث': 'Update policy saved', 'تم تحديث حساب الدعم': 'Support account updated',
    'حساب ظاهر': 'accounts shown', 'بحث بالاسم أو البريد': 'Search by name or email', 'بحث': 'Search',
    'الرصيد معروض مباشرة': 'Balances shown live', 'رسالة للجميع': 'Message everyone', 'تحديث البيانات': 'Refresh data',
    'المستخدم': 'User', 'الصلاحية': 'Role', 'الحالة': 'Status', 'آخر دخول': 'Last sign-in', 'الرصيد': 'Balance', 'إجراءات': 'Actions',
    'بدون اسم': 'Unnamed', 'الميزات': 'Features', 'تفعيل خطة': 'Activate plan', 'السجلات': 'Activity',
    'تعيين كلمة مرور': 'Set password', 'حذف الحساب': 'Delete account', 'إلغاء الحظر': 'Unban', 'حظر': 'Ban',
    'لا يوجد مستخدمون حتى الآن.': 'There are no users yet.',
    'إرسال رسالة إلى جميع المستخدمين': 'Send a message to all users',
    'ستصل الرسالة إلى جميع الحسابات النشطة داخل التطبيق. لا تستخدمها للبيانات الحساسة.': 'The message will reach all active accounts in the app. Do not use it for sensitive information.',
    'العنوان': 'Title', 'الرسالة': 'Message', 'إرسال الرسالة': 'Send message', 'تم إرسال الرسالة إلى {count} مستخدم': 'Message sent to {count} users',
    'سجلات المستخدم': 'User activity', 'لا توجد سجلات لهذا المستخدم.': 'There is no activity for this user.', 'إغلاق': 'Close',
    'تم تحديث حالة الحساب': 'Account status updated', 'كلمة المرور الجديدة': 'New password', 'حفظ كلمة المرور': 'Save password',
    'لا يمكن عرض كلمة المرور الحالية لأن نظام المصادقة يخزنها بشكل غير قابل للاسترجاع. يمكنك تعيين كلمة مرور جديدة مباشرة.': 'The current password cannot be displayed because the authentication system stores it irreversibly. You can set a new password instead.',
    'سيتم حذف حساب {email} نهائيًا وتحرير ربط الجهاز. هل تريد المتابعة؟': 'The account {email} will be permanently deleted and its device unlinked. Continue?',
    'تم حذف الحساب وتحرير الجهاز': 'Account deleted and device unlinked', 'ميزات الحساب': 'Account features',
    'حدد الميزات التي تريد منحها للحساب. إزالة التحديد تعطل الميزة فورًا، بدون الحاجة لكتابة اسمها يدويًا.': 'Select the features to grant this account. Clearing a selection disables the feature immediately, without typing its name.',
    'تاريخ انتهاء اختياري': 'Optional expiration date', 'اتركه فارغًا لتفعيل الميزة بدون انتهاء.': 'Leave blank to enable the feature without an expiration date.', 'حفظ الميزات': 'Save features',
    'يوم': 'days', 'ميزات أساسية': 'Basic features', 'أساسية': 'Basic',
    'اختر الخطة من القائمة وحدد مدة التفعيل. سيتم تسجيل العملية ضمن اشتراكات الحساب.': 'Choose a plan and activation period. The action will be recorded in the account’s subscriptions.',
    'لا توجد خطط مفعلة.': 'There are no active plans.', 'مدة التفعيل بالأيام': 'Activation period in days', 'تفعيل الخطة': 'Activate plan',
    'رصيد المستخدم': 'User balance', 'الرصيد الحالي': 'Current balance', 'لا توجد حركات رصيد.': 'There are no balance transactions.',
    'للتعديل استخدم رقمًا موجبًا للإضافة أو سالبًا للخصم. لا يمكن أن يصبح الرصيد سالبًا.': 'Use a positive amount to add funds or a negative amount to deduct funds. The balance cannot become negative.',
    'تعديل الرصيد': 'Adjust balance', 'مثال: 10 أو -5': 'For example: 10 or -5', 'أدخل مبلغًا غير صفري': 'Enter a non-zero amount', 'حفظ الرصيد': 'Save balance',
    'اسم الباقة': 'Plan name', 'السعر': 'Price', 'العملة': 'Currency', 'المدة المدفوعة بالأيام': 'Paid duration in days',
    'مدة المجانية بالدقائق': 'Free duration in minutes', '60 = ساعة، 1440 = يوم. اتركها 0 لاستخدام مدة الأيام.': '60 = one hour, 1440 = one day. Leave at 0 to use the duration in days.',
    'الوصف': 'Description', 'تفعيل مجاني لمرة واحدة لكل حساب': 'One free activation per account', 'الباقة مفعلة': 'Plan enabled', 'باقة مميزة': 'Featured plan', 'ميزات الباقة': 'Plan features',
    'تعديل خطة الاشتراك': 'Edit subscription plan', 'إضافة خطة اشتراك': 'Add subscription plan', 'حفظ التعديل': 'Save changes', 'إنشاء الباقة': 'Create plan',
    'أنشئ الباقات وعدّل ميزاتها وحالتها من مكان واحد': 'Create plans and manage their features and status in one place',
    'إضافة باقة': 'Add plan', 'تحديث': 'Refresh', 'الباقة': 'Plan', 'المدة': 'Duration', 'التفعيل المجاني': 'Free activation',
    'بدون وصف': 'No description', 'مرة واحدة': 'Once', 'دقيقة': 'minutes', 'مدفوعة': 'Paid', 'مفعلة': 'Enabled', 'متوقفة': 'Disabled', 'مميزة': 'Featured',
    'تعديل': 'Edit', 'حذف': 'Delete', 'لا توجد خطط اشتراك. ابدأ بإضافة أول باقة.': 'There are no subscription plans. Add your first plan to get started.',
    'هل تريد حذف الباقة «{name}»؟': 'Delete the plan “{name}”?', 'تم حذف الباقة': 'Plan deleted',
    'اضبط العنوان والحد الأدنى وسعر الصرف والتعليمات لكل طريقة': 'Configure the address, minimum amount, exchange rate and instructions for each method',
    'الطريقة': 'Method', 'الحد الأدنى': 'Minimum', 'سعر الصرف': 'Exchange rate', 'التعليمات': 'Instructions', 'إجراء': 'Action',
    'عنوان الاستلام': 'Receiving address', 'تعليمات الدفع': 'Payment instructions', 'حفظ': 'Save', 'تم حفظ طريقة الشحن': 'Top-up method saved',
    'راجع الطلبات قبل إضافة الرصيد': 'Review requests before adding funds', 'المبلغ': 'Amount', 'رقم العملية': 'Transaction reference', 'القيمة': 'Value',
    'موافقة': 'Approve', 'رفض': 'Reject', 'لا توجد طلبات شحن معلقة.': 'There are no pending top-up requests.',
    'ملاحظة التحقق (اختيارية):': 'Verification note (optional):', 'سبب الرفض:': 'Reason for rejection:', 'تم تحديث طلب الشحن': 'Top-up request updated',
    'بيانات الدخول غير صحيحة': 'Invalid sign-in credentials'
  };
  const storage = {
    get(key) { try { return localStorage.getItem(key); } catch (_) { return null; } },
    set(key, value) { try { localStorage.setItem(key, value); return true; } catch (_) { return false; } },
    remove(key) { try { localStorage.removeItem(key); } catch (_) {} }
  };
  let language = storage.get('admin_language') === 'en' ? 'en' : 'ar';
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' }[c]));
  const t = (key, params = {}) => (language === 'en' ? english[key] || key : key).replace(/\{(\w+)\}/g, (match, name) => Object.prototype.hasOwnProperty.call(params, name) ? String(params[name]) : match);
  const h = key => Object.prototype.hasOwnProperty.call(english, key) ? `<span data-i18n="${escape(key)}">${escape(t(key))}</span>` : escape(key);

  // Tag only literal HTML text and placeholders. Interpolated data is deliberately
  // not inspected, so even server text identical to a UI label is left untouched.
  function html(strings, ...values) {
    const state = { inTag: false, quote: '' };
    const translateText = text => {
      const match = text.match(/^(\s*)([\s\S]*?)(\s*)$/);
      return match && Object.prototype.hasOwnProperty.call(english, match[2]) ? match[1] + h(match[2]) + match[3] : text;
    };
    const literal = part => {
      part = part.replace(/placeholder="([^"]*)"/g, (all, key) => Object.prototype.hasOwnProperty.call(english, key) ? `placeholder="${escape(t(key))}" data-i18n-placeholder="${escape(key)}"` : all);
      let out = '', text = '';
      for (const char of part) {
        if (!state.inTag) {
          if (char === '<') { out += translateText(text) + char; text = ''; state.inTag = true; }
          else text += char;
        } else {
          out += char;
          if (state.quote) { if (char === state.quote) state.quote = ''; }
          else if (char === '"' || char === "'") state.quote = char;
          else if (char === '>') state.inTag = false;
        }
      }
      return out + translateText(text);
    };
    return strings.map((part, index) => literal(part) + (index < values.length ? values[index] : '')).join('');
  }
  const staticHtml = value => html([value]);
  function refresh(root = document) {
    root.querySelectorAll('[data-i18n]').forEach(node => {
      let params = {};
      try { params = JSON.parse(node.dataset.i18nParams || '{}'); } catch (_) {}
      node.textContent = t(node.dataset.i18n, params);
    });
    root.querySelectorAll('[data-i18n-placeholder]').forEach(node => { node.placeholder = t(node.dataset.i18nPlaceholder); });
    root.querySelectorAll('[data-i18n-date]').forEach(node => { node.textContent = formatDate(node.dataset.i18nDate); });
    root.querySelectorAll('[data-i18n-time]').forEach(node => { node.textContent = formatTime(node.dataset.i18nTime); });
  }
  function formatDate(value) { return value ? new Date(value).toLocaleDateString(language, { year: 'numeric', month: 'short', day: 'numeric' }) : '—'; }
  function formatTime(value) { return value ? new Date(value).toLocaleTimeString(language, { hour: '2-digit', minute: '2-digit' }) : ''; }
  function apply() {
    document.documentElement.lang = language;
    document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr';
    document.title = t('لوحة الإدارة');
    const button = document.getElementById('languageToggle');
    button.textContent = language === 'ar' ? 'English' : 'العربية';
    button.setAttribute('aria-label', language === 'ar' ? 'Switch to English' : 'التبديل إلى العربية');
    refresh();
  }
  function setLanguage(next) {
    if (!['ar', 'en'].includes(next)) return;
    language = next;
    storage.set('admin_language', language);
    apply();
  }
  window.AdminI18n = { t, h, html, staticHtml, refresh, formatDate, formatTime, storage, setLanguage, get language() { return language; } };
  document.getElementById('languageToggle').addEventListener('click', () => setLanguage(language === 'ar' ? 'en' : 'ar'));
  apply();
})();
