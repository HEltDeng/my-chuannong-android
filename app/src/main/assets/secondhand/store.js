/* Shared listing, contact and search rules. New stores contain no operational content. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SecondhandData = factory();
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  const CATEGORIES = Object.freeze(['自行车', '书籍', '电动车', '四六级耳机', '宿舍用品', '数码产品', '其他']);
  const CAMPUSES = Object.freeze(['雅安校区', '成都校区', '都江堰校区']);
  const ALL_CAMPUSES = '全部校区';
  const SELF = Object.freeze({ id: 'local-self', name: '我' });
  const copy = value => JSON.parse(JSON.stringify(value));
  const text = value => typeof value === 'string' ? value : '';
  const normalizeCategory = value => value === '高速材料' ? '其他' : value;
  const normalizeQuery = value => text(value).normalize('NFKC').trim().toLocaleLowerCase('zh-CN').replace(/\s+/g, ' ');
  const validImage = value => typeof value === 'string' && /^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/.test(value) && value.length < 1_500_000;

  function contactIssue(value) {
    const wechat = text(value.wechat).trim(), phone = text(value.phone).trim();
    if (!wechat && !phone) return { field: 'wechat', message: '请至少填写微信号或联系电话' };
    if (wechat && (wechat.length > 64 || /\s/.test(wechat))) return { field: 'wechat', message: '请填写不含空格的微信号，最多 64 个字符' };
    if (phone && (!/^\+?[\d ()-]+$/.test(phone) || phone.length > 32 || phone.replace(/\D/g, '').length < 6 || phone.replace(/\D/g, '').length > 20)) return { field: 'phone', message: '请填写有效的联系电话' };
    return null;
  }

  function matchesListing(listing, { type = 'sell', campus = ALL_CAMPUSES, category = '全部', query = '' } = {}) {
    if (listing.type !== type || (campus !== ALL_CAMPUSES && listing.campus !== campus) || (category !== '全部' && listing.category !== category)) return false;
    const haystack = normalizeQuery([listing.title, listing.description, listing.category, listing.campus].join(' '));
    return normalizeQuery(query).split(' ').filter(Boolean).every(term => haystack.includes(term));
  }

  function cleanListing(value) {
    if (!value || !['sell', 'want'].includes(value.type) || !text(value.id) || !text(value.title).trim() || !text(value.description).trim()) return null;
    const category = normalizeCategory(value.category);
    if (!CATEGORIES.includes(category) || !CAMPUSES.includes(value.campus)) return null;
    return {
      id: value.id, type: value.type, title: value.title.trim().slice(0, 50),
      description: value.description.trim().slice(0, 1000), category, campus: value.campus,
      wechat: text(value.wechat).trim().slice(0, 64), phone: text(value.phone).trim().slice(0, 32),
      images: Array.isArray(value.images) ? value.images.filter(validImage).slice(0, 4) : [],
      illustration: value.type === 'sell' && ['bike', 'book', 'lamp', 'laptop', 'zap', 'file'].includes(value.illustration) ? value.illustration : '',
      ownerId: text(value.ownerId) || SELF.id, ownerName: text(value.ownerName).slice(0, 30) || SELF.name,
      createdAt: Number.isFinite(value.createdAt) ? value.createdAt : Date.now(),
      updatedAt: Number.isFinite(value.updatedAt) ? value.updatedAt : null,
    };
  }

  function cleanState(value) {
    if (value != null && (![1, 2].includes(value.version) || !Array.isArray(value.listings))) throw new Error('无法读取本地内容，请重新打开页面。');
    const listings = (value?.listings || []).map(cleanListing).filter(Boolean);
    return {
      version: 2, listings,
      preferences: {
        campus: [ALL_CAMPUSES, ...CAMPUSES].includes(value?.preferences?.campus) ? value.preferences.campus : ALL_CAMPUSES,
        tab: value?.preferences?.tab === 'want' ? 'want' : 'sell',
      },
    };
  }

  function makeId() {
    return typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36) + '-' + Math.random().toString(36).slice(2);
  }

  async function createStore(adapter) {
    if (typeof window !== "undefined" && window.ChuannongMarket) { const remote = await window.ChuannongMarket.connect(adapter); if (remote) return remote; }
    const saved = await adapter.read();
    let state = cleanState(saved);
    const migrateCategory = saved?.listings?.some(item => item?.category !== normalizeCategory(item?.category));
    if (saved?.version === 1 || migrateCategory) await adapter.write(state);
    let writing = Promise.resolve();
    function transaction(update) {
      const work = writing.then(async () => {
        const next = copy(state);
        const result = update(next);
        await adapter.write(next);
        state = next;
        return copy(result);
      });
      writing = work.catch(() => {});
      return work;
    }
    return {
      snapshot: () => copy(state),
      listings: options => state.listings.filter(item => matchesListing(item, options)).sort((a, b) => b.createdAt - a.createdAt).map(copy),
      find: id => copy(state.listings.find(item => item.id === id) || null),
      owns: id => state.listings.some(item => item.id === id && item.ownerId === SELF.id),
      mine: (type = 'all') => state.listings.filter(item => item.ownerId === SELF.id && (type === 'all' || item.type === type)).sort((a, b) => (b.updatedAt || b.createdAt) - (a.updatedAt || a.createdAt)).map(copy),
      preferences: () => copy(state.preferences),
      setPreferences: preferences => transaction(next => {
        next.preferences = cleanState({ ...next, preferences: { ...next.preferences, ...preferences } }).preferences;
        return next.preferences;
      }),
      publish: values => transaction(next => {
        const issue = contactIssue(values);
        if (issue) throw new Error(issue.message);
        const item = cleanListing({ ...values, id: makeId(), ownerId: SELF.id, ownerName: SELF.name, createdAt: Date.now() });
        if (!item) throw new Error('请填写完整的标题、描述、分类和校区。');
        next.listings.unshift(item);
        return item;
      }),
      update: (id, values) => transaction(next => {
        const index = next.listings.findIndex(item => item.id === id && item.ownerId === SELF.id);
        if (index < 0) throw new Error('只能编辑自己发布的信息。');
        const current = next.listings[index];
        const changes = {};
        for (const key of ['title', 'description', 'category', 'campus', 'images', 'wechat', 'phone']) {
          if (Object.prototype.hasOwnProperty.call(values, key)) changes[key] = values[key];
        }
        const merged = { ...current, ...changes, updatedAt: Date.now() };
        const issue = contactIssue(merged);
        if (issue) throw new Error(issue.message);
        const item = cleanListing(merged);
        if (!item) throw new Error('请填写完整的标题、描述、分类和校区。');
        next.listings[index] = item;
        return item;
      }),
      remove: id => transaction(next => {
        const index = next.listings.findIndex(item => item.id === id && item.ownerId === SELF.id);
        if (index < 0) throw new Error('只能删除自己发布的信息。');
        return next.listings.splice(index, 1)[0];
      }),
    };
  }
  return { CATEGORIES, CAMPUSES, ALL_CAMPUSES, contactIssue, normalizeQuery, normalizeCategory, matchesListing, createStore };
});
