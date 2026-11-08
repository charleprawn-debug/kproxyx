(async function () {
  const button = document.getElementById('download');
  const error = document.getElementById('error');
  if (!button || !error) return;

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
    button.addEventListener('click', (event) => {
      event.preventDefault();
      error.classList.add('show');
    });
  }
})();
