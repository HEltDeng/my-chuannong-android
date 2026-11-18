(() => {
  'use strict';
  const { CATEGORIES, CAMPUSES, ALL_CAMPUSES, contactIssue, createStore } = window.SecondhandData;
  const app = document.getElementById('app');
  const overlay = document.getElementById('overlay-root');
  const categoryIcons = { '自行车': 'bike', '书籍': 'book', '电动车': 'zap', '四六级耳机': 'headphones', '宿舍用品': 'lamp', '数码产品': 'laptop', '其他': 'package' };
  const esc = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
  const icon = (name, extra = '') => `<img class="icon ${extra}" src="./icons/${name}.svg" alt="" aria-hidden="true">`;
  const newDraft = () => ({ clientId: crypto.randomUUID(), title: '', description: '', category: '书籍', campus: '雅安校区', images: [], wechat: '', phone: '' });
  const state = { tab: 'sell', campus: ALL_CAMPUSES, category: '全部', query: '', searchOpen: false, drafts: { sell: newDraft(), want: newDraft() }, edit: null, mineFilter: 'all', deleteId: null, deleting: false, sheet: null, beforeFocus: null, publishing: false, processingImages: false };
  let store, lastRoute = null, toastTimer;
  const reviewLabels = {PENDING:'待审核',PUBLISHED:'已发布',VIOLATION:'违规'};
  const reviewBadge = item => item.status ? `<span class="review-status ${esc(item.status.toLowerCase())}">${reviewLabels[item.status]||esc(item.status)}</span>` : '';
  const reviewReason = item => item.moderationNote ? `<div class="review-reason"><strong>处理原因</strong><p>${esc(item.moderationNote)}</p></div>` : '';
  let syncing=false, seenNotices=new Set();
  function renderSyncBar() {
    const node=document.getElementById('market-sync');if(!node)return;
    const unread=(store.notifications?.()||[]).filter(n=>!n.read_at);
    node.innerHTML=store.online?`<span>${store.connected()?'审核通过后公开展示':'连接已中断，重新连接后同步状态'}</span><div><button data-sync="notices">处理通知${unread.length?' ('+unread.length+')':''}</button><button data-sync="refresh">刷新</button></div>`:'本机演示 · 不会提交到后台';
  }
  async function refreshRemote(manual=false) {
    if(!store?.refresh||syncing||document.hidden)return;
    syncing=true;
    try {
      const changed=await store.refresh();
      const viewer=document.getElementById('market-photo-viewer');
      if(viewer?.open&&!store.find(viewer.dataset.post))viewer.close();
      const unread=store.notifications().filter(n=>!n.read_at&&!seenNotices.has(n.id));
      if(unread.length)toast(`收到 ${unread.length} 条处理通知，请查看具体结果和原因`);
      store.notifications().forEach(n=>seenNotices.add(n.id));
      if(changed&&!state.sheet&&!route().startsWith('publish/')&&(!editing()||!store.find(routeId()))){const y=scrollY;renderRoute();scrollTo(0,y);}
      if(manual)toast('已同步最新审核状态');
    }catch(error){if(!route())renderListings();if(manual)toast(error.message||'同步失败，请重试');}
    finally{syncing=false;renderSyncBar();}
  }
  function renderNotifications(){
    const notices=store.notifications?.()||[];
    app.innerHTML=`<main class="wrap"><div class="mine-intro"><div><h2>处理通知</h2><p>查看哪条信息被处理，以及具体结果和原因。</p></div><button class="contact-entry" data-action="mine">我的发布</button></div>${notices.map(n=>`<article class="review-notice"><div>${reviewBadge(n)}<span class="muted">${n.post_type==='sell'?'出售':'求购'} · ${n.read_at?'已读':'未读'}</span></div><h3>${esc(n.title)}</h3><p>处理结果：${({approve:'审核通过',reject:'审核驳回',take_down:'已下架'})[n.action]}</p>${n.reason?`<div class="review-reason"><strong>具体原因</strong><p>${esc(n.reason)}</p></div>`:'<p>本次审核已通过，当前状态可在“我的发布”查看。</p>'}<p class="muted">${new Date(n.created_at).toLocaleString('zh-CN')}</p><div class="my-post-actions"><button data-action="detail" data-id="${esc(n.post_id)}">查看信息</button>${!n.read_at?`<button data-action="read-notice" data-id="${esc(n.id)}">标记已读</button>`:''}</div></article>`).join('')||'<p class="empty">暂时没有处理通知</p>'}</main>`;
  }
  function showImage(id,index){
    const item=store.find(id);if(!item?.images[index])return;
    let viewer=document.getElementById('market-photo-viewer');
    if(!viewer){viewer=document.createElement('dialog');viewer.id='market-photo-viewer';document.body.append(viewer);}
    // Replace the handler so opening another post cannot retain the previous post id.
    viewer.onclick=e=>{const b=e.target.closest('[data-viewer]');if(!b)return;if(b.dataset.viewer==='close')viewer.close();else showImage(viewer.dataset.post,Number(b.dataset.index));};
    viewer.dataset.post=id;
    viewer.innerHTML=`<header><span>${index+1} / ${item.images.length}</span><button data-viewer="previous" data-index="${index-1}" ${index===0?'disabled':''}>上一张</button><button data-viewer="next" data-index="${index+1}" ${index===item.images.length-1?'disabled':''}>下一张</button><button data-viewer="close">关闭大图</button></header><div><img src="${item.images[index]}" alt="第 ${index+1} 张完整图片"></div>`;
    if(!viewer.open)viewer.showModal();
  }

  function databaseAdapter() {
    const connection = new Promise((resolve, reject) => {
      const request = indexedDB.open('mychuannong-secondhand', 1);
      request.onupgradeneeded = () => request.result.createObjectStore('content');
      request.onsuccess = () => { request.result.onversionchange = () => request.result.close(); resolve(request.result); };
      request.onerror = () => reject(request.error);
      request.onblocked = () => reject(new Error('请关闭其他二手好物页面后重试。'));
    });
    return {
      async read() {
        const db = await connection;
        return new Promise((resolve, reject) => {
          const request = db.transaction('content', 'readonly').objectStore('content').get('state');
          request.onsuccess = () => resolve(request.result);
          request.onerror = () => reject(request.error);
        });
      },
      async write(value) {
        const db = await connection;
        return new Promise((resolve, reject) => {
          const transaction = db.transaction('content', 'readwrite');
          transaction.objectStore('content').put(value, 'state');
          transaction.oncomplete = () => resolve();
          transaction.onabort = transaction.onerror = () => reject(transaction.error || new Error('保存失败'));
        });
      },
    };
  }

  function toast(message) {
    const node = document.getElementById('toast');
    clearTimeout(toastTimer); node.textContent = message; node.hidden = false;
    toastTimer = setTimeout(() => { node.hidden = true; }, 2800);
  }
  function route() { return location.hash.replace(/^#\/?/, ''); }
  function routeId() { try { return decodeURIComponent(route().split('/')[1] || ''); } catch { return ''; } }
  function editing() { return route().startsWith('edit/'); }
  function mode() { return editing() ? store.find(routeId())?.type || 'sell' : routeId() === 'want' ? 'want' : 'sell'; }
  function currentDraft() { return editing() ? state.edit.draft : state.drafts[mode()]; }
  function navigate(path, replace = false) {
    document.activeElement?.blur();
    const depth = replace ? (history.state?.depth || 0) : (history.state?.depth || 0) + 1;
    history[replace ? 'replaceState' : 'pushState']({ secondhand: true, depth, route: path }, '', path ? '#' + path : location.pathname + location.search);
    renderRoute();
  }
  function back() {
    const viewer=document.getElementById('market-photo-viewer');if(viewer?.open){viewer.close();return true;}
    if (state.sheet) { closeSheet(); return true; }
    if (!route()) return false;
    if (history.state?.secondhand && history.state.depth > 0) history.back();
    else navigate('', true);
    return true;
  }
  function syncHistory() {
    if (state.sheet && !history.state?.sheet) hideSheet();
    if (lastRoute !== route()) renderRoute();
    if (!state.sheet && history.state?.sheet) { state.sheet = history.state.sheet; state.deleteId = history.state.deleteId || null; renderSheet(); }
  }

  function relativeTime(createdAt) {
    const minutes = Math.max(0, Math.floor((Date.now() - createdAt) / 60000));
    if (minutes < 1) return '刚刚';
    if (minutes < 60) return minutes + '分钟前';
    if (minutes < 1440) return Math.floor(minutes / 60) + '小时前';
    return new Date(createdAt).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' });
  }
  function person(item) {
    return `<div class="person"><span class="avatar">${esc(item.ownerName.slice(0, 1))}</span><span class="person-name">${esc(item.ownerName)}</span><time datetime="${new Date(item.createdAt).toISOString()}">${relativeTime(item.createdAt)}</time></div>`;
  }
  function photograph(item, full = false) {
    if (item.images.length) return `<img class="product-photo" src="${item.images[0]}" alt="${esc(item.title)}的图片" ${full ? '' : 'loading="lazy"'}>`;
    if (item.illustration) return `<div class="product-photo sample-photo cover-${item.illustration}" role="img" aria-label="${esc(item.category)}插画">${icon(item.illustration)}</div>`;
    return `<div class="product-photo no-photo">${icon(categoryIcons[item.category] || 'image')}<span>暂无图片</span></div>`;
  }
  function renderSearch() {
    const node = document.getElementById('search-area');
    if (!node) return;
    node.innerHTML = state.searchOpen ? `<form class="search-form" id="search-form" role="search">${icon('search')}<input type="search" name="keyword" aria-label="搜索物品" placeholder="搜索标题、内容、分类或校区" value="${esc(state.query)}" maxlength="100" autocomplete="off" enterkeyhint="search"><button type="button" class="search-cancel" data-action="close-search" aria-label="取消搜索">${icon('x')}</button><button class="search-submit" type="submit">搜索</button></form>` : `<button class="search-trigger" data-action="open-search" aria-label="搜索">${icon('search')}<span>搜一搜，发现需要的好物</span></button>`;
  }
  function renderHome() {
    app.innerHTML = `<main class="wrap"><section class="hero"><h2>闲置好物，校园新趣。</h2><p>让每一份喜欢，在同学之间继续。</p><img class="hero-plant" src="./hero-plant.svg" alt=""></section>
      <div class="tab-row"><div class="tabs" role="tablist" aria-label="好物类型"><button role="tab" aria-selected="${state.tab === 'sell'}" class="${state.tab === 'sell' ? 'active' : ''}" data-action="tab" data-value="sell">出售</button><button role="tab" aria-selected="${state.tab === 'want'}" class="${state.tab === 'want' ? 'active' : ''}" data-action="tab" data-value="want">求购</button><button role="tab" aria-selected="false" data-action="mine">我的</button></div>
      <button class="campus-filter" data-action="filter-campus" aria-label="筛选校区：${state.campus}" aria-haspopup="dialog">${icon('map-pin')}<span>${state.campus}</span>${icon('chevron-down', 'chevron')}</button><button class="publish-add" data-action="publish" aria-label="发布好物">${icon('plus')}</button></div>
      <div id="search-area"></div><div class="chips" aria-label="商品分类">${['全部', ...CATEGORIES].map(category => `<button data-action="category" data-value="${category}" aria-pressed="${state.category === category}" class="${state.category === category ? 'active' : ''}">${category}</button>`).join('')}</div>
      <div class="list-head"><h2>${state.tab === 'sell' ? '好物推荐' : '同学的心愿清单'}</h2><span id="result-count" class="result-count" aria-live="polite"></span></div><div id="listing"></div></main>`;
    renderSearch(); renderListings();
  }
  function renderListings() {
    const target = document.getElementById('listing');
    if (!target) return;
    const items = store.listings({ type: state.tab, campus: state.campus, category: state.category, query: state.query });
    document.getElementById('result-count').textContent = items.length ? items.length + ' 条信息' : '';
    if (!items.length) { target.innerHTML = `<div class="empty">${icon('brand', 'brand-mark')}暂无相关信息</div>`; return; }
    target.innerHTML = state.tab === 'sell' ? `<div class="grid">${items.map(item => `<button class="item" data-action="detail" data-id="${esc(item.id)}">${photograph(item)}<div class="item-content"><h3>${esc(item.title)}</h3><p class="summary">${esc(item.description)}</p>${person(item)}<div class="location">${esc(item.campus)}</div></div></button>`).join('')}</div>` : items.map(item => `<article class="wanted" data-listing-id="${esc(item.id)}">${person(item)}<button class="wanted-detail" data-action="detail" data-id="${esc(item.id)}"><h3>${esc(item.title)}</h3><p>${esc(item.description)}</p></button><footer><small>${esc(item.campus)}</small><button class="contact-entry" data-action="detail" data-id="${esc(item.id)}">查看联系方式</button></footer></article>`).join('');
  }
  function savePreferences() { store.setPreferences({ campus: state.campus, tab: state.tab }).catch(() => toast('筛选偏好未能保存，请稍后重试。')); }
  function setTab(type) { state.tab = type; state.category = '全部'; savePreferences(); renderHome(); refreshRemote(); }

  function renderMine() {
    const items = store.mine(state.mineFilter);
    app.innerHTML = `<main class="wrap"><div class="mine-intro"><div><h2 tabindex="-1">我的发布</h2><p>你发布的好物和求购，都在这里。</p></div><button class="publish-add" data-action="publish" aria-label="发布好物">${icon('plus')}</button></div>
      <div class="mine-filters" role="tablist" aria-label="我的发布类型">${[['all', '全部'], ['sell', '出售'], ['want', '求购']].map(([value, label]) => `<button role="tab" data-action="mine-filter" data-value="${value}" aria-selected="${state.mineFilter === value}" class="${state.mineFilter === value ? 'active' : ''}">${label}</button>`).join('')}</div>
      <div class="list-head"><h2>全部提交</h2><span class="result-count">${items.length} 条信息</span></div><div id="mine-listing">${items.length ? items.map(item => `<article class="my-post" data-id="${esc(item.id)}"><button class="my-post-detail" data-action="detail" data-id="${esc(item.id)}">${item.type === 'sell' ? `<div class="my-post-photo">${photograph(item)}</div>` : ''}<div class="my-post-content"><span class="tag">${item.type === 'sell' ? '出售' : '求购'}</span>${reviewBadge(item)}<h3>${esc(item.title)}</h3><p>${esc(item.description)}</p></div></button><div class="my-post-meta"><span>${esc(item.campus)} · ${esc(item.category)}</span><span>${relativeTime(item.updatedAt || item.createdAt)}${item.updatedAt ? '更新' : '发布'}</span></div>${reviewReason(item)}<div class="my-post-actions"><button class="edit-post" data-action="edit" data-id="${esc(item.id)}">${item.status==='VIOLATION'?'修改并重新提交':'编辑'}</button><button class="delete-post" data-action="delete" data-id="${esc(item.id)}">删除</button></div></article>`).join('') : `<div class="empty mine-empty">${icon('brand', 'brand-mark')}<p>${store.mine().length ? '暂无这类发布' : '还没有发布信息'}</p><button class="primary" data-action="publish">去发布</button></div>`}</div></main>`;
  }
  function beginEdit(id) {
    if (!store.owns(id)) { toast('只能编辑自己发布的信息。'); return; }
    state.edit = { id, draft: { ...store.find(id) } }; navigate('edit/' + encodeURIComponent(id));
  }

  function contactDetails(item) {
    const rows = [['wechat', '微信号', '复制微信'], ['phone', '联系电话', '复制电话']].filter(([key]) => item[key]);
    return `<div class="contact-details" aria-label="联系方式">${rows.length ? rows.map(([key, label, action]) => `<div class="contact-row"><div class="contact-text"><span class="contact-label">${label}</span><span class="contact-value">${esc(item[key])}</span></div><button class="copy-contact" data-action="copy-contact" data-id="${esc(item.id)}" data-field="${key}" aria-label="${action}">${action}</button></div>`).join('') : '<p class="contact-missing">发布人暂未留联系方式</p>'}</div>`;
  }
  async function copyContact(button) {
    const field = button.dataset.field;
    if (!['wechat', 'phone'].includes(field)) return;
    const value = store.find(button.dataset.id)?.[field];
    if (!value) return;
    button.disabled = true;
    try {
      let copied = false;
      try { await navigator.clipboard.writeText(value); copied = true; } catch { /* Older WebViews use the selection fallback below. */ }
      if (!copied) {
        const input = document.createElement('textarea');
        input.value = value; input.readOnly = true; input.setAttribute('aria-label', '待复制的联系方式');
        input.style.cssText = 'position:fixed;left:-9999px;top:0;opacity:0'; document.body.append(input);
        try { input.select(); input.setSelectionRange(0, value.length); copied = document.execCommand('copy'); }
        finally { input.remove(); button.focus({ preventScroll: true }); }
      }
      toast(copied ? (field === 'wechat' ? '微信号已复制，可前往微信添加' : '联系电话已复制') : '复制未成功，请长按联系方式复制');
    } catch { toast('复制未成功，请长按联系方式复制'); }
    finally { button.disabled = false; }
  }

  function renderDetail(item) {
    app.innerHTML = `<main class="wrap">${item.mine?`<div class="review-detail">${reviewBadge(item)}<span>${item.status==='PUBLISHED'?'其他同学可以查看':'仅自己可见'}</span>${reviewReason(item)}</div>`:''}
      ${store.online&&!item.mine?`<div class="community-actions"><button data-action="report-post" data-id="${esc(item.id)}">举报内容</button>${item.ownerId?`<button data-action="block-author" data-id="${esc(item.id)}">屏蔽发布者</button>`:''}</div>`:''}
      ${item.images.length?`<div class="detail-gallery">${item.images.map((source,index)=>`<button data-action="photo" data-id="${esc(item.id)}" data-index="${index}" aria-label="查看第 ${index+1} 张完整图片"><img class="product-photo" src="${source}" alt="${esc(item.title)}的第${index+1}张图片"><span>${index+1} / ${item.images.length} · 点击查看大图</span></button>`).join('')}</div>`:''}
      <section class="panel"><span class="tag">${item.type==='sell'?'出售':'求购'}</span><h2>${esc(item.title)}</h2><div class="info"><span class="tag">${esc(item.campus)}</span><span class="tag">${esc(item.category)}</span><span class="muted">${relativeTime(item.createdAt)}提交</span></div><p class="body">${esc(item.description)}</p></section>
      <section class="panel"><div class="owner"><span class="avatar">${esc(item.ownerName.slice(0,1))}</span><div><h3>${esc(item.ownerName)}</h3><p class="muted">${esc(item.campus)}</p></div></div><h3 class="contact-heading">联系方式</h3>${contactDetails(item)}</section></main>`;
  }

  function renderPublish() {
    const type = mode(), draft = currentDraft(), isEdit = editing();
    app.innerHTML = `<main class="wrap"><div class="publish-intro"><h2>${isEdit ? type === 'sell' ? '编辑出售' : '编辑求购' : '发布好物'}</h2></div>
      ${isEdit ? '' : `<div class="publish-modes" role="group" aria-label="发布类型"><button id="publish-sell" data-action="publish-mode" data-value="sell" class="${type === 'sell' ? 'active' : ''}" aria-pressed="${type === 'sell'}">发布出售</button><button id="publish-want" data-action="publish-mode" data-value="want" class="${type === 'want' ? 'active' : ''}" aria-pressed="${type === 'want'}">发布求购</button></div>`}
      <form class="publish-form" id="publish-form">${'<div class="field"><span class="field-label">参考图片<span class="field-hint">最多 4 张</span></span><div class="photo-grid" id="photo-grid"></div><input type="file" id="publish-images" accept="image/jpeg,image/png,image/webp,image/gif" multiple hidden aria-label="选择好物图片"></div>'}
      <label class="field"><span class="field-label">${type === 'sell' ? '好物标题' : '求购标题'}</span><input name="title" id="publish-title" required maxlength="50" value="${esc(draft.title)}" placeholder="${type === 'sell' ? '给闲置好物起个名字' : '你想寻找什么好物？'}"></label>
      <div class="field"><span class="field-label" id="category-label">物品分类</span><button class="field-picker" type="button" data-action="publish-category" aria-haspopup="dialog" aria-labelledby="category-label selected-category"><span class="picker-value">${icon(categoryIcons[draft.category])}<span id="selected-category">${draft.category}</span></span>${icon('chevron-down', 'chevron')}</button></div>
      <div class="field"><span class="field-label" id="campus-label">所在校区</span><button class="field-picker" type="button" data-action="publish-campus" aria-haspopup="dialog" aria-labelledby="campus-label selected-campus"><span class="picker-value">${icon('map-pin')}<span id="selected-campus">${draft.campus}</span></span>${icon('chevron-down', 'chevron')}</button></div>
      <label class="field"><span class="field-label">详细描述</span><textarea name="description" id="publish-description" required maxlength="1000" placeholder="${type === 'sell' ? '介绍物品的成色、使用情况和交接方式' : '说说你需要的物品、期望成色和交接方式'}">${esc(draft.description)}</textarea></label>
      <fieldset class="contact-fields"><legend>联系方式</legend><p id="contact-hint">微信号或联系电话至少填写一项，发布后展示在详情页。</p>
      <label class="field"><span class="field-label">微信号</span><input name="wechat" id="publish-wechat" maxlength="64" value="${esc(draft.wechat)}" placeholder="填写微信号，方便同学添加" autocapitalize="off" autocomplete="off" spellcheck="false" aria-describedby="contact-hint"></label>
      <label class="field"><span class="field-label">联系电话</span><input type="tel" name="phone" id="publish-phone" maxlength="32" value="${esc(draft.phone)}" placeholder="填写联系电话" autocomplete="tel" aria-describedby="contact-hint"></label></fieldset>
      <button class="primary" type="submit" ${state.processingImages || state.publishing ? 'disabled' : ''}>${isEdit ? '保存并重新审核' : '提交审核'}</button><p class="review-hint">出售和求购均须审核通过后公开展示；修改内容后会重新审核。</p></form></main>`;
    renderPhotos();
  }
  function rememberDraft(form) {
    const draft = currentDraft();
    for (const key of ['title', 'description', 'wechat', 'phone']) { draft[key] = form.elements[key].value; form.elements[key].setCustomValidity(''); }
  }
  function renderPhotos() {
    const node = document.getElementById('photo-grid');
    if (!node) return;
    const images = currentDraft().images;
    node.innerHTML = images.map((image, index) => `<div class="photo-thumb"><img src="${image}" alt="待发布的第${index + 1}张图片"><button type="button" class="photo-remove" data-action="remove-photo" data-index="${index}" aria-label="移除第${index + 1}张图片">${icon('x')}</button></div>`).join('') + (images.length < 4 ? `<button type="button" class="photo-add" data-action="add-photo" ${state.processingImages ? 'disabled' : ''}>${icon('image-plus')}<span>${state.processingImages ? '处理中…' : '添加图片'}</span></button>` : '');
  }
  async function readPhoto(file) {
    if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type) || file.size > 12 * 1024 * 1024) throw new Error('请选择 12MB 以内的 JPG、PNG 或 WebP 图片。');
    const source = await new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = reject; reader.readAsDataURL(file); });
    const image = new Image(); image.src = source; await image.decode();
    const scale = Math.min(1, 1200 / Math.max(image.naturalWidth, image.naturalHeight));
    const canvas = document.createElement('canvas'); canvas.width = Math.max(1, Math.round(image.naturalWidth * scale)); canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
    const context = canvas.getContext('2d'); context.fillStyle = '#ffffff'; context.fillRect(0, 0, canvas.width, canvas.height); context.drawImage(image, 0, 0, canvas.width, canvas.height);
    const result = canvas.toDataURL('image/jpeg', .8);
    if (result.length >= 1_500_000) throw new Error('这张图片较大，请换一张图片。');
    return result;
  }
  async function uploadPhotos(input) {
    if (state.processingImages) return;
    const files = Array.from(input.files || []); input.value = '';
    if (!files.length) return;
    const draft = currentDraft(), available = 4 - draft.images.length;
    if (files.length > available) toast('最多添加 4 张图片。');
    state.processingImages = true; renderPhotos();
    const submit = document.querySelector('#publish-form [type=submit]'); if (submit) submit.disabled = true;
    try { for (const file of files.slice(0, available)) draft.images.push(await readPhoto(file)); }
    catch (error) { toast(error.message || '图片无法读取，请更换图片。'); }
    finally { state.processingImages = false; renderPhotos(); const button = document.querySelector('#publish-form [type=submit]'); if (button) button.disabled = false; }
  }
  async function submitPublish(form) {
    if (state.publishing || state.processingImages) return;
    rememberDraft(form);
    const type = mode(), draft = currentDraft(), isEdit = editing(), editId = isEdit ? routeId() : null;
    for (const key of ['title', 'description']) {
      if (!draft[key].trim()) { form.elements[key].setCustomValidity(key === 'title' ? '请填写标题' : '请填写详细描述'); form.elements[key].reportValidity(); return; }
    }
    const issue = contactIssue(draft);
    if (issue) { form.elements[issue.field].setCustomValidity(issue.message); form.elements[issue.field].reportValidity(); return; }
    if (!form.reportValidity()) return;
    const button = form.querySelector('[type=submit]'); state.publishing = true; button.disabled = true; button.textContent = isEdit ? '正在保存…' : '正在发布…';
    try {
      if (isEdit) {
        await store.update(editId, draft);
        if (state.edit?.id === editId) state.edit = null;
        if (route() === 'edit/' + encodeURIComponent(editId)) { if (history.state?.depth > 0) history.back(); else navigate('mine', true); }
        toast(store.online?'修改已提交，等待重新审核':'修改已保存'); return;
      }
      const item = await store.publish({ ...draft, type });
      state.drafts[type] = newDraft(); state.tab = type; state.category = '全部'; state.query = ''; state.searchOpen = false; state.mineFilter = 'all';
      if (state.campus !== ALL_CAMPUSES) state.campus = item.campus;
      savePreferences();
      if (route().startsWith('publish/')) navigate('mine',true);
      toast(store.online?'已提交，等待管理员审核':'已保存到本机演示');
    } catch (error) { toast(error.message || '提交失败，请重试。'); }
    finally { state.publishing = false; if (button.isConnected) { button.disabled = false; button.textContent = isEdit ? '保存并重新审核' : '提交审核'; } }
  }

  async function confirmDelete() {
    if (state.deleting || !state.deleteId || !store.owns(state.deleteId)) return;
    state.deleting = true; renderSheet();
    try {
      await store.remove(state.deleteId);
      state.deleting = false; renderMine(); closeSheet(); toast('已删除');
    } catch { state.deleting = false; renderSheet(); toast('删除未能保存，请重试。'); }
  }
  function openSheet(kind, id = null) {
    state.beforeFocus = document.activeElement; document.activeElement?.blur(); state.sheet = kind; state.deleteId = id;
    history.pushState({ ...history.state, sheet: kind, deleteId: id }, '', location.href); renderSheet();
  }
  function renderSheet() {
    if (state.sheet === 'block-author' || state.sheet === 'report-post') {
      const item=store.find(state.deleteId);
      if (!item || item.mine) { closeSheet(); return; }
      const reporting=state.sheet==='report-post';
      overlay.innerHTML=`<div class="sheet-backdrop"><section class="sheet delete-sheet" role="dialog" aria-modal="true" aria-labelledby="sheet-title" tabindex="-1"><div class="sheet-handle" aria-hidden="true"></div><h2 id="sheet-title">${reporting?'举报内容':'屏蔽发布者？'}</h2>${reporting?'<label class="field"><span class="field-label">举报原因</span><textarea id="community-report-reason" maxlength="300" rows="4" placeholder="请说明需要处理的内容"></textarea></label>':'<p class="delete-summary">将在这台设备隐藏该用户在二手好物和失物招领中的发布，可在隐私与合规中解除。</p>'}<div class="delete-actions"><button data-action="close-sheet">取消</button><button class="confirm-delete" data-action="${reporting?'confirm-report':'confirm-block'}">${reporting?'提交举报':'屏蔽'}</button></div></section></div>`;
      app.inert=true;app.setAttribute('aria-hidden','true');document.body.classList.add('sheet-open');
      requestAnimationFrame(()=>overlay.querySelector(reporting?'textarea':'[data-action=close-sheet]')?.focus());return;
    }
    if (state.sheet === 'delete') {
      const item = store.find(state.deleteId);
      if (!item || !store.owns(item.id)) { closeSheet(); return; }
      overlay.innerHTML = `<div class="sheet-backdrop"><section class="sheet delete-sheet" role="dialog" aria-modal="true" aria-labelledby="sheet-title" tabindex="-1"><div class="sheet-handle" aria-hidden="true"></div><h2 id="sheet-title">删除这条发布？</h2><p class="delete-summary">${esc(item.title)}</p><p class="muted">删除后这条信息将不再展示。</p><div class="delete-actions"><button data-action="close-sheet" ${state.deleting ? 'disabled' : ''}>取消</button><button class="confirm-delete" data-action="confirm-delete" ${state.deleting ? 'disabled' : ''}>${state.deleting ? '正在删除…' : '删除'}</button></div></section></div>`;
      app.inert = true; app.setAttribute('aria-hidden', 'true'); document.body.classList.add('sheet-open');
      requestAnimationFrame(() => overlay.querySelector('[data-action=close-sheet]')?.focus()); return;
    }
    const category = state.sheet === 'publish-category', publishing = state.sheet !== 'filter-campus';
    const values = category ? CATEGORIES : publishing ? CAMPUSES : [...CAMPUSES, ALL_CAMPUSES];
    const selected = publishing ? currentDraft()[category ? 'category' : 'campus'] : state.campus;
    overlay.innerHTML = `<div class="sheet-backdrop"><section class="sheet" role="dialog" aria-modal="true" aria-labelledby="sheet-title" tabindex="-1"><div class="sheet-handle" aria-hidden="true"></div><div class="sheet-header"><h2 id="sheet-title">${category ? '选择物品分类' : '选择校区'}</h2><button class="sheet-close" data-action="close-sheet" aria-label="关闭选择面板">${icon('x')}</button></div><div class="${category ? 'choice-grid' : 'choice-list'}" role="radiogroup" aria-label="${category ? '物品分类' : '校区'}">${values.map(value => `<button class="choice-card ${value === selected ? 'selected' : ''}" role="radio" aria-checked="${value === selected}" data-action="choose" data-value="${value}"><span class="choice-icon">${icon(category ? categoryIcons[value] : value === ALL_CAMPUSES ? 'school' : 'map-pin')}</span><span class="choice-label">${value}</span>${value === selected ? icon('check', 'choice-check') : ''}</button>`).join('')}</div></section></div>`;
    app.inert = true; app.setAttribute('aria-hidden', 'true'); document.body.classList.add('sheet-open');
    requestAnimationFrame(() => overlay.querySelector('[aria-checked=true]')?.focus());
  }
  function hideSheet() {
    const kind = state.sheet;
    state.sheet = null; state.deleteId = null; overlay.innerHTML = ''; app.inert = false; app.removeAttribute('aria-hidden'); document.body.classList.remove('sheet-open');
    if (state.beforeFocus?.isConnected) state.beforeFocus.focus({ preventScroll: true });
    else if (kind === 'delete') app.querySelector('.mine-intro h2')?.focus({ preventScroll: true });
    else app.querySelector(`[data-action="${kind}"]`)?.focus({ preventScroll: true });
  }
  function closeSheet() { if (state.deleting) return; if (history.state?.sheet) history.back(); else hideSheet(); }
  function choose(value) {
    if (state.sheet === 'filter-campus') {
      state.campus = value; savePreferences();
      const button = app.querySelector('.campus-filter'); button.querySelector('span').textContent = value; button.setAttribute('aria-label', '筛选校区：' + value); renderListings();
    } else {
      currentDraft()[state.sheet === 'publish-category' ? 'category' : 'campus'] = value;
      renderPublish();
    }
    closeSheet();
  }

  function renderRoute() {
    if (!store) return;
    if (state.sheet) hideSheet();
    lastRoute = route();
    const [page] = lastRoute.split('/'), item = store.find(routeId());
    if (page === 'publish') renderPublish();
    else if (page === 'mine') renderMine();
    else if (page === 'notifications') renderNotifications();
    else if (page === 'edit' && item && store.owns(item.id)) {
      if (state.edit?.id !== item.id) state.edit = { id: item.id, draft: { ...item } };
      renderPublish();
    }
    else if (page === 'detail' && item && store.isBlocked?.(item)) app.innerHTML='<main class="wrap"><p class="empty">已屏蔽该用户</p><button class="primary" data-action="home">返回二手好物</button></main>';
    else if (page === 'detail' && item) renderDetail(item);
    else if (page === 'detail' || page === 'edit') app.innerHTML = '<main class="wrap"><p class="empty">这条信息暂时无法查看。</p><button class="primary" data-action="home">返回二手好物</button></main>';
    else renderHome();
    window.scrollTo(0, 0);
  }
  app.addEventListener('click', event => {
    const button = event.target.closest('[data-action]'); if (!button || button.disabled) return;
    const { action, value, id } = button.dataset;
    if (action === 'tab') setTab(value);
    else if (action === 'photo') showImage(id,Number(button.dataset.index));
    else if (action === 'read-notice') store.markRead([id]).then(()=>{renderNotifications();renderSyncBar();}).catch(e=>toast(e.message));
    else if (action === 'mine') { state.mineFilter = 'all'; navigate('mine'); refreshRemote(); }
    else if (action === 'mine-filter') { state.mineFilter = value; renderMine(); }
    else if (action === 'edit' && !state.publishing) beginEdit(id);
    else if (action === 'delete' && store.owns(id)) openSheet('delete', id);
    else if (action === 'report-post' || action === 'block-author') openSheet(action,id);
    else if (action === 'publish') { const type = route() === 'mine' && state.mineFilter === 'want' ? 'want' : state.tab; const draft = state.drafts[type]; if (!draft.title && state.campus !== ALL_CAMPUSES) draft.campus = state.campus; navigate('publish/' + type); }
    else if (action === 'detail') { if (store.detail) store.detail(id).then(() => navigate('detail/' + encodeURIComponent(id))).catch(e => toast(e.message)); else navigate('detail/' + encodeURIComponent(id)); }
    else if (action === 'copy-contact') copyContact(button);
    else if (action === 'home') navigate('', true);
    else if (action === 'category') { state.category = value; app.querySelectorAll('.chips button').forEach(node => { node.classList.toggle('active', node.dataset.value === value); node.setAttribute('aria-pressed', String(node.dataset.value === value)); }); renderListings(); }
    else if (action === 'open-search') { state.searchOpen = true; renderSearch(); document.querySelector('#search-form input').focus(); }
    else if (action === 'close-search') { state.searchOpen = false; state.query = ''; renderSearch(); renderListings(); }
    else if (action === 'publish-mode' && !editing() && !state.publishing) navigate('publish/' + value, true);
    else if (['filter-campus', 'publish-category', 'publish-campus'].includes(action)) openSheet(action);
    else if (action === 'add-photo') document.getElementById('publish-images').click();
    else if (action === 'remove-photo' && !state.processingImages) { currentDraft().images.splice(Number(button.dataset.index), 1); renderPhotos(); }
  });
  app.addEventListener('input', event => {
    if (event.target.form?.id === 'search-form') { state.query = event.target.value; renderListings(); }
    else if (event.target.form?.id === 'publish-form') rememberDraft(event.target.form);
  });
  app.addEventListener('change', event => { if (event.target.id === 'publish-images') uploadPhotos(event.target); });
  app.addEventListener('submit', event => {
    event.preventDefault();
    if (event.target.id === 'search-form') { state.query = event.target.elements.keyword.value; renderListings(); event.target.elements.keyword.blur(); }
    else if (event.target.id === 'publish-form') submitPublish(event.target);
  });
  overlay.addEventListener('click', event => {
    const button = event.target.closest('[data-action]');
    if (button?.dataset.action === 'choose') choose(button.dataset.value);
    else if (button?.dataset.action === 'confirm-delete') confirmDelete();
    else if (button?.dataset.action === 'confirm-block') {
      const item=store.find(state.deleteId);
      try {
        const result=JSON.parse(window.ChuannongContent.blockUser(item.ownerId,item.ownerName||'校园用户'));
        if(!result.ok)throw Error(result.error?.message||'屏蔽失败');
        hideSheet();navigate('',true);toast('已屏蔽发布者');
      } catch(error) { toast(error.message||'屏蔽失败，请重试'); }
    }
    else if (button?.dataset.action === 'confirm-report') {
      const reason=overlay.querySelector('#community-report-reason')?.value.trim();
      if(!reason){toast('请填写举报原因');return;}
      button.disabled=true;
      setTimeout(()=>{try {
        const result=JSON.parse(window.ChuannongContent.reportCommunity(state.deleteId,reason));
        if(!result.ok)throw Error(result.error?.message||'举报提交失败');
        hideSheet();history.replaceState({...history.state,sheet:null,deleteId:null},'',location.href);toast('举报已提交');
      } catch(error) { button.disabled=false;toast(error.message||'举报提交失败，请重试'); }},0);
    }
    else if (button?.dataset.action === 'close-sheet' || event.target.classList.contains('sheet-backdrop')) closeSheet();
  });
  document.addEventListener('keydown', event => {
    if (!state.sheet) return;
    if (event.key === 'Escape') { event.preventDefault(); closeSheet(); }
    if (event.key === 'Tab') {
      const buttons = Array.from(overlay.querySelectorAll('button'));
      if (event.shiftKey && document.activeElement === buttons[0]) { event.preventDefault(); buttons.at(-1).focus(); }
      else if (!event.shiftKey && document.activeElement === buttons.at(-1)) { event.preventDefault(); buttons[0].focus(); }
    }
  });
  window.addEventListener('popstate', syncHistory); window.addEventListener('hashchange', syncHistory);
  window.CampusSecondhand = { back, refresh: refreshRemote, ready: false };
  createStore(databaseAdapter()).then(value => {
    store = value; Object.assign(state, store.preferences());
    const current = route(); history.replaceState({ secondhand: true, depth: 0, route: current }, '', location.href);
    app.setAttribute('aria-busy', 'false'); window.CampusSecondhand.ready = true; renderRoute();
    const status = document.createElement('div'); status.id='market-sync';status.className = 'content-mode';document.body.prepend(status);renderSyncBar();
    status.addEventListener('click',e=>{const b=e.target.closest('[data-sync]');if(!b)return;if(b.dataset.sync==='notices'){navigate('notifications');refreshRemote();}else refreshRemote(true);});
    if(store.online){refreshRemote();setInterval(()=>refreshRemote(),10000);document.addEventListener('visibilitychange',()=>{if(!document.hidden)refreshRemote();});window.addEventListener('focus',()=>refreshRemote());}
  }).catch(error => {
    app.setAttribute('aria-busy', 'false'); app.innerHTML = `<div class="loading-error"><p>${esc(error.message||'暂时无法连接二手好物服务，请重新连接后提交审核。')}</p><button class="primary" id="retry-load">重新连接</button></div>`;
    document.getElementById('retry-load').addEventListener('click', () => location.reload());
  });
})();
