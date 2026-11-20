import type { Request, Response } from 'express';

// Only application-owned, fixed error strings belong here. Never pass provider,
// database, administrator or user text through this function.
const englishErrorMessages = {
  'تعذر تحميل حالة إعدادات الدفع.': 'Could not load payment settings status.',
  'رابط OxaPay غير صالح.': 'The OxaPay URL is invalid.',
  'رابط Webhook يجب أن يكون HTTPS.': 'The webhook URL must use HTTPS.',
  'رابط مزود الدفع السوري غير صالح.': 'The Syrian payment provider URL is invalid.',
  'تعذر حفظ إعدادات الدفع.': 'Could not save payment settings.',
  'تعذر تحميل إعدادات صفحة الهبوط.': 'Could not load landing page settings.',
  'أدخل رابط تحميل صحيحًا.': 'Enter a valid download URL.',
  'يجب أن يكون رابط التحميل HTTPS أو HTTP وبطول صالح.': 'The download URL must use HTTP or HTTPS and have a valid length.',
  'تعذر حفظ رابط التحميل.': 'Could not save the download URL.',
  'تعذر تحميل حساب الدعم.': 'Could not load the support account.',
  'أدخل اسم حساب Telegram صالحًا.': 'Enter a valid Telegram username.',
  'تعذر حفظ حساب الدعم.': 'Could not save the support account.',
  'تعذر تحميل إعدادات التحديث.': 'Could not load update settings.',
  'أدخل رقم إصدار ورسالة تحديث صحيحة.': 'Enter a valid version number and update message.',
  'أدخل رابط تحديث صحيحًا.': 'Enter a valid update URL.',
  'تعذر حفظ إعدادات التحديث.': 'Could not save update settings.',
  'تعذر تحميل إحصائيات الأرصدة والاشتراكات.': 'Could not load balance and subscription statistics.',
  'تعذر تحميل رصيد المستخدم.': 'Could not load the user balance.',
  'تعذر تحميل حركة الرصيد.': 'Could not load the balance history.',
  'العنوان والرسالة مطلوبان.': 'A title and message are required.',
  'تعذر تحميل المستخدمين.': 'Could not load users.',
  'تعذر إرسال الرسالة.': 'Could not send the message.',
  'كلمة المرور يجب أن تكون بين 8 و128 محرفًا.': 'The password must be between 8 and 128 characters.',
  'المستخدم غير موجود.': 'User not found.',
  'تعذر تغيير كلمة مرور المستخدم.': 'Could not change the user password.',
  'لا يمكن حذف حساب الأدمن الحالي.': 'The current admin account cannot be deleted.',
  'تعذر تحرير ربط الجهاز.': 'Could not release the device binding.',
  'تعذر حذف ملف المستخدم.': 'Could not delete the user profile.',
  'تم حذف ملف المستخدم لكن تعذر حذف سجل المصادقة؛ راجع السجلات.': 'The user profile was deleted, but the authentication record could not be deleted; check the logs.',
  'تعذر إلغاء اشتراكات هذه الباقة.': 'Could not cancel subscriptions for this plan.',
  'الباقة غير موجودة.': 'Plan not found.',
  'لا يمكن إنشاء الحساب دون التحقق من الجهاز.': 'An account cannot be created without verifying the device.',
  'تم إنشاء حساب مسبقًا من هذا الجهاز. استخدم تسجيل الدخول للحساب الحالي.': 'An account was already created on this device. Sign in to the existing account.',
  'تعذر التحقق من الجهاز حاليًا.': 'Could not verify the device right now.',
  'تعذر تثبيت الحساب على الجهاز.': 'Could not bind the account to the device.',
  'أدخل بريدًا إلكترونيًا صالحًا.': 'Enter a valid email address.',
  'أدخل البريد وكلمة مرور جديدة صالحة.': 'Enter an email address and a valid new password.',
  'هذا البريد غير مرتبط بهذا الجهاز.': 'This email address is not linked to this device.',
  'تعذر تغيير كلمة المرور حاليًا.': 'Could not change the password right now.',
  'تعذر تحميل الإشعارات.': 'Could not load notifications.',
  'تعذر تحميل رسائل الدعم.': 'Could not load support messages.',
  'الإشعار غير موجود.': 'Notification not found.',
  'تعذر تحميل السجل.': 'Could not load the activity log.',
  'تعذر تحميل البروكسيات المحفوظة.': 'Could not load saved proxies.',
  'تعذر فك تشفير بيانات البروكسي.': 'Could not decrypt proxy credentials.',
  'بيانات البروكسي غير صالحة.': 'The proxy details are invalid.',
  'تعذر تحديث الاختيار الحالي.': 'Could not update the current selection.',
  'تعذر حفظ البروكسي.': 'Could not save the proxy.',
  'تعذر حذف البروكسي المحفوظ.': 'Could not delete the saved proxy.',
  'تعذر تحديث الاختيار.': 'Could not update the selection.',
  'البروكسي غير موجود.': 'Proxy not found.',
  'لا يمكن تحديد مزود البروكسي.': 'Could not determine the proxy provider.',
  'هذه العملية غير مدعومة لهذا المزود.': 'This action is not supported by this provider.',
  'إدارة هذا المزود غير متاحة من API.': 'This provider cannot be managed through the API.',
  'تعذر تنفيذ العملية لدى المزود.': 'Could not perform the action with the provider.',
  'تعذر تحميل طرق الشحن.': 'Could not load top-up methods.',
  'تعذر تحميل طلبات الشحن.': 'Could not load top-up requests.',
  'أدخل مبلغًا صالحًا.': 'Enter a valid amount.',
  'طريقة الشحن غير متاحة حاليًا.': 'This top-up method is currently unavailable.',
  'لم يجهز الأدمن عنوان هذه الطريقة بعد.': 'The admin has not configured an address for this method yet.',
  'سعر الصرف غير صالح.': 'The exchange rate is invalid.',
  'الدفع بالـUSDT غير مهيأ حاليًا.': 'USDT payments are not currently configured.',
  'تعذر إنشاء طلب الشحن.': 'Could not create the top-up request.',
  'تعذر إنشاء عنوان USDT حاليًا. حاول لاحقًا.': 'Could not create a USDT address right now. Try again later.',
  'أدخل رقم عملية صالحًا.': 'Enter a valid transaction reference.',
  'طلب الشحن غير موجود.': 'Top-up request not found.',
  'تمت معالجة طلب الشحن مسبقًا.': 'The top-up request has already been processed.',
  'رقم العملية مستخدم مسبقًا.': 'The transaction reference has already been used.',
  'مزود التحقق غير مهيأ حاليًا.': 'The verification provider is not currently configured.',
  'لم يتم العثور على عملية مطابقة. تأكد من الرقم والمبلغ ثم حاول مجددًا.': 'No matching transaction was found. Check the reference and amount, then try again.',
  'تم التحقق من العملية لكن تعذر تحديث الرصيد. حاول تحديث الصفحة.': 'The transaction was verified, but the balance could not be updated. Try refreshing the page.',
  'اختر الدولة والمدة قبل المتابعة.': 'Select a country and duration before continuing.',
  'هذا الطلب قيد المعالجة أو تم استخدامه سابقًا.': 'This request is being processed or has already been used.',
  'رصيد المحفظة غير كافٍ لإتمام الشراء.': 'The wallet balance is insufficient to complete the purchase.',
  'تعذر إنشاء البروكسي وتمت إعادة الرصيد.': 'Could not create the proxy; the balance has been refunded.',
  'المزود غير صالح.': 'The provider is invalid.',
  'تعذر تحميل خيارات المزود.': 'Could not load provider options.',
  'البروتوكول غير صالح.': 'The protocol is invalid.',
  'تعذر تحميل خيارات الشراء.': 'Could not load purchase options.',
  'المزود لا يحتاج إلى تسعير خارجي.': 'This provider does not require external pricing.',
  'خيار الشراء غير صالح.': 'The purchase option is invalid.',
  'اسم المستخدم يجب أن يكون بين 3 و64 محرفًا.': 'The username must be between 3 and 64 characters.',
  'كلمة المرور يجب أن تكون بين 6 و128 محرفًا.': 'The password must be between 6 and 128 characters.',
  'الخطة أو الموقع لم يعد متاحًا. حدّث الخيارات وحاول مجددًا.': 'The plan or location is no longer available. Refresh the options and try again.',
  'تعذر التحقق من السعر.': 'Could not verify the price.',
  'استخدم كتالوج الشراء الجديد.': 'Use the new purchase catalog.',
  'تعذر التحقق من الموقع لدى المزود.': 'Could not verify the location with the provider.',
  'تعذر الحصول على السعر من المزود.': 'Could not obtain the price from the provider.',
  'تعذر حساب السعر.': 'Could not calculate the price.',
  'لا توجد مواقع متاحة لهذا البروتوكول حاليًا.': 'No locations are currently available for this protocol.',
  'المزود لم يرجع سعرًا صالحًا.': 'The provider did not return a valid price.',
  'منتج Static Residential IPv4 غير متاح في حساب Proxy-Cheap حاليًا.': 'Static Residential IPv4 is not currently available in the Proxy-Cheap account.',
  'الباقة المختارة غير متاحة لدى المزود حاليًا.': 'The selected plan is not currently available from the provider.',
  'لم يرجع المزود رقم الطلب/القائمة.': 'The provider did not return an order/list ID.',
  'حساب Proxy-Seller لا يحتوي على باقة Residential مفعّلة. يجب إنشاء باقة Resident من لوحة المزود أولًا، لأن API العام لا يشتري هذه الباقة.': 'The Proxy-Seller account does not have an active Residential package. Create a Resident package in the provider dashboard first; the public API cannot purchase it.',
  'الدولة المختارة غير متاحة لهذه الباقة. أعد اختيار الموقع ثم حاول مرة أخرى.': 'The selected country is not available for this plan. Choose a location and try again.',
  'الولاية المختارة غير متاحة لهذه الباقة. أعد اختيار الموقع ثم حاول مرة أخرى.': 'The selected state is not available for this plan. Choose a location and try again.',
  'المدينة المختارة غير متاحة لهذه الباقة أو لا تتبع الولاية المختارة. أعد اختيار الموقع ثم حاول مرة أخرى.': 'The selected city is unavailable for this plan or does not belong to the selected state. Choose a location and try again.',
  'المزود المختار غير متاح في هذا الموقع لهذه الباقة. أعد اختيار الموقع ثم حاول مرة أخرى.': 'The selected ISP is unavailable at this location for this plan. Choose a location and try again.',
  'هذا المنتج لا يدعم تحديد هذا النوع من المواقع. أعد اختيار الموقع ثم حاول مرة أخرى.': 'This product does not support selecting this type of location. Choose a location and try again.',
  'الموقع المختار غير متاح لدى المزود. أعد اختيار الموقع ثم حاول مرة أخرى.': 'The selected location is unavailable from the provider. Choose a location and try again.',
} as const;

export type FixedErrorMessage = keyof typeof englishErrorMessages;

// Express's language negotiation honors q-values, regional English variants,
// and request ordering. Arabic is the fallback for absent or unsupported languages.
function wantsEnglish(req: Request, res: Response): boolean {
  res.vary('Accept-Language');
  return req.acceptsLanguages('ar', 'en') === 'en';
}

export function localizeError(req: Request, res: Response, message: FixedErrorMessage): string {
  return wantsEnglish(req, res) ? englishErrorMessages[message] : message;
}

// These are English originals, so they must remain English for legacy clients
// with no header. Only pass literal, application-owned messages, never external text.
const arabicErrorMessages = {
  'Valid email and password of at least 8 characters are required': 'يجب إدخال بريد إلكتروني صالح وكلمة مرور من 8 محارف على الأقل.',
  'Could not create user': 'تعذر إنشاء المستخدم.',
  'Could not create user profile': 'تعذر إنشاء ملف المستخدم.',
  'Email or password is incorrect': 'البريد الإلكتروني أو كلمة المرور غير صحيحة.',
  'Refresh token is invalid': 'رمز تحديث الجلسة غير صالح.',
  'Bearer token required': 'رمز المصادقة Bearer مطلوب.',
  'Invalid or expired token': 'رمز المصادقة غير صالح أو منتهي الصلاحية.',
  'User profile not found': 'ملف المستخدم غير موجود.',
  'Insufficient permissions': 'الصلاحيات غير كافية.',
  'Could not load profile': 'تعذر تحميل الملف الشخصي.',
  'Could not load subscriptions': 'تعذر تحميل الاشتراكات.',
  'Could not load subscription plans': 'تعذر تحميل خطط الاشتراك.',
  'Could not load wallet': 'تعذر تحميل المحفظة.',
  'Could not load wallet history': 'تعذر تحميل سجل المحفظة.',
  'Could not load saved proxies': 'تعذر تحميل البروكسيات المحفوظة.',
  'Could not decrypt proxy credentials': 'تعذر فك تشفير بيانات البروكسي.',
  'Wallet top-up methods are not available yet': 'طرق شحن المحفظة غير متاحة بعد.',
  'Could not load feature access': 'تعذر تحميل صلاحيات الميزات.',
  'Could not activate plan': 'تعذر تفعيل الباقة.',
  'Could not load products': 'تعذر تحميل المنتجات.',
  'Could not load product': 'تعذر تحميل المنتج.',
  'Product not found': 'المنتج غير موجود.',
  'Invalid provider price': 'سعر المزود غير صالح.',
  'Could not load users': 'تعذر تحميل المستخدمين.',
  'Valid status or role is required': 'يجب إدخال حالة أو دور صالح.',
  'A non-zero wallet amount is required': 'يجب إدخال مبلغ للمحفظة غير صفري.',
  'Wallet unavailable': 'المحفظة غير متاحة.',
  'Wallet cannot become negative': 'لا يمكن أن يصبح رصيد المحفظة سالبًا.',
  'planId is required': 'معرّف الباقة planId مطلوب.',
  'Subscription plan not found or inactive': 'خطة الاشتراك غير موجودة أو غير مفعلة.',
  'Could not load user access': 'تعذر تحميل صلاحيات المستخدم.',
  'Unsupported feature': 'الميزة غير مدعومة.',
  'features object is required': 'كائن الميزات features مطلوب.',
  'Could not load plans': 'تعذر تحميل الباقات.',
  'Subscription plan name is required': 'اسم خطة الاشتراك مطلوب.',
  'No editable fields supplied': 'لم تُقدَّم حقول قابلة للتعديل.',
  'Plan name and protocol are required': 'اسم الباقة والبروتوكول مطلوبان.',
  'Could not load top-up methods': 'تعذر تحميل طرق الشحن.',
  'Invalid minimum amount': 'الحد الأدنى للمبلغ غير صالح.',
  'Invalid exchange rate': 'سعر الصرف غير صالح.',
  'Invalid sort order': 'ترتيب الفرز غير صالح.',
  'Could not load top-up requests': 'تعذر تحميل طلبات الشحن.',
  'Decision must be approve or reject': 'يجب أن يكون القرار approve أو reject.',
  'Could not approve top-up': 'تعذر الموافقة على طلب الشحن.',
  'Endpoint not found': 'نقطة النهاية غير موجودة.',
  'Internal server error': 'حدث خطأ داخلي في الخادم.',
  'Landing settings migration is pending': 'ترحيل إعدادات صفحة الهبوط لم يكتمل بعد.',
} as const;

export type FixedEnglishMessage = keyof typeof arabicErrorMessages;

export function localizeEnglishMessage(req: Request, res: Response, message: FixedEnglishMessage): string {
  res.vary('Accept-Language');
  // Unlike the older Arabic-original messages, a missing or wildcard-only
  // header must not change the legacy English response. Respect q=0 and the
  // relative preference/order of explicitly requested Arabic and English.
  const explicitlyRequestsArabic = (req.get('Accept-Language') ?? '')
    .split(',').some((part) => /^\s*ar(?:-[a-z0-9]+)*\s*(?:;|$)/i.test(part));
  return explicitlyRequestsArabic && req.acceptsLanguages('ar', 'en') === 'ar'
    ? arabicErrorMessages[message] : message;
}

// Preserve the original English in logs, while distinguishing errors constructed
// here from an external error with exactly the same text.
export class FixedEnglishApiError extends Error {
  constructor(public readonly fixedMessage: FixedEnglishMessage) {
    super(fixedMessage);
  }
}

// Only interpolate values into an application-owned template, never translate or
// otherwise transform the dynamic amount or currency.
export function localizeMinimumTopup(req: Request, res: Response, amount: unknown, currency: unknown): string {
  return wantsEnglish(req, res)
    ? `The minimum top-up is ${amount} ${currency}.`
    : `الحد الأدنى للشحن هو ${amount} ${currency}.`;
}

// Marks errors created by this application, not errors propagated by providers.
// Their original Arabic message remains available to existing logs/storage.
export class FixedApiError extends Error {
  constructor(public readonly fixedMessage: FixedErrorMessage) {
    super(fixedMessage);
  }
}

export function localizeCaughtError(req: Request, res: Response, error: unknown, fallback: FixedErrorMessage): string {
  if (error instanceof FixedApiError) return localizeError(req, res, error.fixedMessage);
  if (error instanceof FixedEnglishApiError) return localizeEnglishMessage(req, res, error.fixedMessage);
  return error instanceof Error ? error.message : localizeError(req, res, fallback);
}
