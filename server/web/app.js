'use strict';
(() => {
  const byId = id => document.getElementById(id);
  const eventId = () => Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('');
  const session = localStorage.echo_session || (localStorage.echo_session = eventId());
  let token = '', point = null, track = null, timer = null;
  async function api(path, data) {
    const response = await fetch(path, {method: data ? 'POST' : 'GET',
      headers: {'Content-Type':'application/json', 'X-Echo-Token':token},
      body: data ? JSON.stringify(data) : undefined, signal: AbortSignal.timeout(8000)});
    const body = await response.json();
    if (!response.ok) throw new Error(response.status === 401 ? 'Укажите токен узла в разделе «Подключение».' : body.error);
    return body;
  }
  function musicRow(item) {
    const row = document.createElement('article');
    const title = document.createElement('strong');
    title.textContent = `${item.artist} — ${item.title}`;
    const genre = document.createElement('small');
    genre.textContent = item.genre || 'Жанр пока не указан';
    row.append(title, genre);
    return row;
  }
  async function refresh() {
    clearTimeout(timer);
    if (document.hidden) return;
    let delay = 7000;
    try {
      if (track) await api('/api/signal', {session_id:session, ...track, ...point, source:'manual'});
      const state = await api('/api/sync', {session_id:session});
      const air = byId('signals');
      air.replaceChildren();
      if (!state.signals.length) air.textContent = point ? 'Эфир тих. Оставьте свой трек рядом.' : 'Определите район и войдите в эфир, чтобы услышать музыку рядом.';
      for (const signal of state.signals) {
        const row = musicRow(signal), impulse = document.createElement('button');
        impulse.textContent = 'Тихий импульс';
        impulse.disabled = !track;
        impulse.addEventListener('click', async () => {
          impulse.disabled = true;
          try { await api('/api/impulses', {id:eventId(), sender_id:session, target_id:signal.session_id});
            byId('status').textContent = 'Импульс ушел в эфир.';
          } catch (error) { byId('status').textContent = error.message; }
          finally { setTimeout(() => { impulse.disabled = !track; }, 5000); }
        });
        row.append(impulse); air.append(row);
      }
      byId('status').textContent = 'Эфир рядом' + (track ? ' · ваш трек звучит' : ' · трансляция выключена');
      if (point) {
        const result = await api(`/api/capsules?latitude=${point.latitude}&longitude=${point.longitude}`);
        const capsules = byId('capsules'); capsules.replaceChildren();
        if (!result.capsules.length) capsules.textContent = 'Здесь еще не оставляли музыку.';
        result.capsules.forEach(capsule => { const row = musicRow(capsule);
          const note = document.createElement('p'); note.textContent = capsule.note; row.append(note); capsules.append(row); });
      }
      delay = 2500;
    } catch (error) { byId('status').textContent = `Эфир недоступен. ${error.message}`; }
    timer = setTimeout(refresh, delay);
  }
  byId('broadcast').addEventListener('submit', event => {
    event.preventDefault();
    const form = new FormData(event.target);
    track = {artist:form.get('artist').trim(), title:form.get('title').trim(), genre:form.get('genre').trim(), open_to_meet:form.has('open')};
    if (!track.artist || !track.title) { byId('status').textContent = 'Укажите исполнителя и трек.'; track = null; return; }
    refresh();
  });
  byId('leave').addEventListener('click', () => { track = null; byId('status').textContent = 'Трансляция завершена. След погаснет в течение 90 секунд.'; });
  byId('location').addEventListener('click', () => {
    if (!navigator.geolocation || !window.isSecureContext) {
      byId('location-status').textContent = 'Для определения района откройте ЭХО по HTTPS или на localhost.'; return;
    }
    navigator.geolocation.getCurrentPosition(position => {
      point = {latitude:Math.round(position.coords.latitude * 100) / 100, longitude:Math.round(position.coords.longitude * 100) / 100};
      byId('location-status').textContent = 'Район определен. Точная позиция не отправляется.'; refresh();
    }, () => { byId('location-status').textContent = 'Район недоступен. Разрешите геолокацию в настройках браузера.'; }, {timeout:10000, maximumAge:60000});
  });
  byId('save-token').addEventListener('click', () => { token = byId('token').value.trim(); byId('token').value = ''; refresh(); });
  document.addEventListener('visibilitychange', () => { clearTimeout(timer); if (!document.hidden) refresh(); });
  if ('serviceWorker' in navigator && window.isSecureContext) navigator.serviceWorker.register('/sw.js').catch(() => {
    byId('status').textContent = 'Офлайн-версия недоступна. Эфир работает при подключении.';
  });
  refresh();
})();
