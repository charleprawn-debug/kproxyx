(async function () {
  const button = document.getElementById('download');
  const error = document.getElementById('error');
  const languageSwitch = document.getElementById('language-switch');
  if (!button || !error || !languageSwitch) return;

  const translations = {
    ar: {
      pageTitle: 'Kun cloak | تحميل التطبيق',
      description: 'Kun cloak — اتصال أسرع وخصوصية أفضل.',
      titleFirst: 'اتصل بحرية',
      titleSecond: 'وابقَ محميًا',
      subtitle: 'تجربة اتصال أكثر سلاسة وخصوصية، مصممة لتكون معك أينما ذهبت.',
      download: 'تحميل التطبيق',
      error: 'رابط التحميل غير متاح حاليًا. حاول مرة أخرى لاحقًا.',
      footer: 'خصوصيتك تبدأ من اتصالك',
      switchText: 'English',
      switchLabel: 'Switch to English',
      switchLanguage: 'en',
    },
    en: {
      pageTitle: 'Kun cloak | Download the app',
      description: 'Kun cloak — a faster connection and better privacy.',
      titleFirst: 'Connect freely',
      titleSecond: 'and stay protected',
      subtitle: 'A smoother, more private connection, designed to go wherever you do.',
      download: 'Download the app',
      error: 'The download link is currently unavailable. Please try again later.',
      footer: 'Your privacy starts with your connection',
      switchText: 'العربية',
      switchLabel: 'التبديل إلى العربية',
      switchLanguage: 'ar',
    },
  };
  const storageKey = 'kun-cloak-language';
  let language = 'ar';

  try {
    const savedLanguage = window.localStorage.getItem(storageKey);
    if (savedLanguage === 'ar' || savedLanguage === 'en') language = savedLanguage;
  } catch (_) {
    // The page remains usable when storage is blocked or unavailable.
  }

  function applyLanguage() {
    const copy = translations[language];
    document.documentElement.lang = language;
    document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr';
    document.title = copy.pageTitle;
    const description = document.querySelector('meta[name="description"]');
    if (description) description.setAttribute('content', copy.description);
    document.querySelectorAll('[data-i18n]').forEach((element) => {
      const key = element.getAttribute('data-i18n');
      if (Object.prototype.hasOwnProperty.call(copy, key)) element.textContent = copy[key];
    });
    languageSwitch.textContent = copy.switchText;
    languageSwitch.lang = copy.switchLanguage;
    languageSwitch.setAttribute('aria-label', copy.switchLabel);
  }

  languageSwitch.addEventListener('click', () => {
    language = language === 'ar' ? 'en' : 'ar';
    applyLanguage();
    try {
      window.localStorage.setItem(storageKey, language);
    } catch (_) {
      // Language switching still works for this page if storage is unavailable.
    }
  });
  applyLanguage();

  try {
    const response = await fetch('/api/v1/landing', {
      headers: { Accept: 'application/json' },
      cache: 'no-store',
    });
    const payload = await response.json();
    const downloadUrl = payload?.data?.downloadUrl;
    if (!response.ok || typeof downloadUrl !== 'string' || !/^https?:\/\//i.test(downloadUrl)) throw new Error('missing');
    button.href = downloadUrl;
    button.target = '_blank';
    button.rel = 'noopener noreferrer';
  } catch (_) {
    button.removeAttribute('href');
    button.setAttribute('aria-disabled', 'true');
    button.setAttribute('role', 'link');
    button.setAttribute('tabindex', '0');
    button.addEventListener('click', (event) => {
      event.preventDefault();
      error.classList.add('show');
    });
    button.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        error.classList.add('show');
      }
    });
  }
})();
