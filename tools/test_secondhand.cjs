'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {createStore, matchesListing, contactIssue, ALL_CAMPUSES} = require('../app/src/main/assets/secondhand/store.js');
const listing = (values = {}) => ({id:'a',type:'sell',title:'课程整理笔记',description:'含高速材料课堂记录与英文 ABC 术语',category:'书籍',campus:'雅安校区',images:[],wechat:'secondhand_test',phone:'',ownerId:'peer',ownerName:'发布人',createdAt:1,...values});
const adapter = (initial, fail = false) => {
  let saved = initial;
  return {read:async()=>saved,write:async value=>{if(fail)throw new Error('disk full');saved=structuredClone(value);},saved:()=>saved};
};
const fixture = listings => ({version:2,listings,preferences:{campus:ALL_CAMPUSES,tab:'sell'}});

test('new stores contain no listings or conversation data or APIs',async()=>{
  const store=await createStore(adapter());
  assert.deepEqual(store.snapshot().listings,[]);assert.equal('threads' in store.snapshot(),false);
  assert.equal(store.send,undefined);assert.equal(store.messages,undefined);assert.equal(store.snapshot().version,2);
  assert.equal(store.preferences().campus,ALL_CAMPUSES);
});
test('search matches exact words in description beyond broad categories',()=>{
  assert.ok(matchesListing(listing(),{query:'高速材料'}));
  assert.ok(matchesListing(listing(),{query:' 高速材料  ＡＢＣ '}));
  assert.ok(matchesListing(listing(),{query:'课程整理'}));
  assert.ok(matchesListing(listing(),{query:'书籍'}));
  assert.ok(matchesListing(listing(),{query:'雅安校区'}));
  assert.equal(matchesListing(listing(),{query:'不存在的内容'}),false);
});
test('query, type, campus and category filters are applied together',()=>{
  assert.ok(matchesListing(listing(),{type:'sell',campus:'雅安校区',category:'书籍',query:'高速材料'}));
  for(const options of [{type:'want'},{campus:'成都校区'},{category:'自行车'}])assert.equal(matchesListing(listing(),{query:'高速材料',...options}),false);
});
test('search also matches specific keywords in wanted content',()=>{
  assert.ok(matchesListing(listing({type:'want'}),{type:'want',query:'高速材料'}));
});
test('retired category migrates sell and wanted records to other without losing content',async()=>{
  const originals=['sell','want'].map(type=>listing({id:type,type,category:'高速材料',images:['data:image/png;base64,AAAA']}));
  const disk=adapter(fixture(originals));const store=await createStore(disk);
  for(const original of originals){
    assert.deepEqual(store.find(original.id),{...original,category:'其他',illustration:'',updatedAt:null});
    assert.equal(store.listings({type:original.type,category:'其他',query:'高速材料'}).length,1);
  }
  assert.ok(disk.saved().listings.every(item=>item.category==='其他'));
  assert.deepEqual((await createStore(disk)).snapshot(),store.snapshot());
});
test('campus preference survives tab changes and store recreation',async()=>{
  const disk=adapter();const store=await createStore(disk);
  await store.setPreferences({campus:'都江堰校区'});await store.setPreferences({tab:'want'});
  const reopened=await createStore(disk);assert.deepEqual(reopened.preferences(),{campus:'都江堰校区',tab:'want'});
});
test('published listings persist without structured price or budget fields',async()=>{
  const disk=adapter();const store=await createStore(disk);
  const entry=await store.publish({...listing(),title:'<img src=x> 高速材料',price:80,budget:'80元以内'});
  assert.equal(entry.title,'<img src=x> 高速材料');assert.equal('price' in entry,false);assert.equal('budget' in entry,false);
  assert.equal((await createStore(disk)).find(entry.id).description,listing().description);
});
test('wanted publishing preserves reference images without price or budget fields',async()=>{
  const store=await createStore(adapter());
  const entry=await store.publish({...listing({type:'want'}),images:['data:image/png;base64,AAAA'],price:'面议',budget:80});
  assert.deepEqual(entry.images,['data:image/png;base64,AAAA']);assert.equal('price' in entry,false);assert.equal('budget' in entry,false);
});
test('untrusted image URLs are not restored',async()=>{
  const store=await createStore(adapter(fixture([listing({images:['https://example.com/a.png','javascript:alert(1)','data:image/svg+xml;base64,AAAA','data:image/png;base64,AAAA']})])));
  assert.deepEqual(store.find('a').images,['data:image/png;base64,AAAA']);
});
test('wechat-only, phone-only and combined contacts persist for sell and want',async()=>{
  for(const type of ['sell','want'])for(const contacts of [{wechat:' contact_test ',phone:''},{wechat:'',phone:' +86 138-0000-0000 '},{wechat:'contact_test',phone:'13800000000'}]){
    const disk=adapter();const store=await createStore(disk);const entry=await store.publish(listing({type,...contacts}));
    const reopened=await createStore(disk);
    assert.equal(reopened.find(entry.id).wechat,contacts.wechat.trim());assert.equal(reopened.find(entry.id).phone,contacts.phone.trim());
  }
});
test('publishing requires at least one valid contact',async()=>{
  const store=await createStore(adapter());
  for(const type of ['sell','want']){
    await assert.rejects(store.publish(listing({type,wechat:'  ',phone:''})),/至少填写/);
    await assert.rejects(store.publish(listing({type,wechat:'a b'})),/不含空格/);
    await assert.rejects(store.publish(listing({type,phone:'abc'})),/有效/);
    await assert.rejects(store.publish(listing({type,phone:'123'})),/有效/);
    await assert.rejects(store.publish(listing({type,wechat:'a'.repeat(65)})),/最多/);
  }
  assert.deepEqual(store.snapshot().listings,[]);assert.equal(contactIssue({wechat:'contact_test'}),null);
});
test('legacy state migrates without losing listings, images or preferences and removes conversations',async()=>{
  const oldItem=listing({wechat:undefined,phone:undefined,images:['data:image/png;base64,AAAA']});
  const value={version:1,listings:[oldItem],threads:[{listingId:'a',messages:[{id:'old',text:'old content'}]}],preferences:{campus:'成都校区',tab:'want'}};
  const disk=adapter(value);const store=await createStore(disk);
  assert.equal(store.find('a').title,oldItem.title);assert.equal(store.find('a').wechat,'');assert.equal(store.find('a').phone,'');
  assert.deepEqual(store.find('a').images,oldItem.images);assert.deepEqual(store.preferences(),value.preferences);
  assert.equal(disk.saved().version,2);assert.equal('threads' in disk.saved(),false);
  assert.equal((await createStore(disk)).find('a').title,oldItem.title);
});
test('concurrent publications serialize writes without dropping listings or contacts',async()=>{
  const store=await createStore(adapter());
  await Promise.all(Array.from({length:20},(_,index)=>store.publish(listing({wechat:'contact_'+index}))));
  assert.equal(store.snapshot().listings.length,20);assert.equal(new Set(store.snapshot().listings.map(x=>x.id)).size,20);
  assert.equal(new Set(store.snapshot().listings.map(x=>x.wechat)).size,20);
});
test('failed writes do not show a successful publish',async()=>{
  const store=await createStore(adapter(fixture([listing()]),true));
  await assert.rejects(store.publish(listing()),/disk full/);assert.equal(store.snapshot().listings.length,1);
});
test('blank titles and descriptions are rejected',async()=>{
  const store=await createStore(adapter(fixture([listing()])));
  await assert.rejects(store.publish({...listing(),title:'   '}));await assert.rejects(store.publish({...listing(),description:'\n'}));
});
test('failed migration leaves previously saved content intact',async()=>{
  const value={version:1,listings:[listing()],threads:[{listingId:'a',messages:[]}],preferences:{}};
  const disk=adapter(value,true);await assert.rejects(createStore(disk),/disk full/);assert.deepEqual(disk.saved(),value);
});
test('malformed persistent data is reported instead of silently overwritten',async()=>{
  const disk=adapter({version:99,listings:[],threads:[]});await assert.rejects(createStore(disk));assert.equal(disk.saved().version,99);
});
test('my publications include both types and exclude other publishers with the same display name',async()=>{
  const store=await createStore(adapter(fixture([
    listing({id:'mine-sell',ownerId:'local-self'}),listing({id:'mine-want',type:'want',ownerId:'local-self'}),listing({id:'peer',ownerName:'我'}),
  ])));
  assert.equal(store.mine().length,2);assert.equal(store.mine('sell')[0].id,'mine-sell');assert.equal(store.mine('want')[0].id,'mine-want');
  assert.equal(store.owns('peer'),false);assert.equal(store.owns('mine-sell'),true);
});
test('editing persists fields and images while keeping publisher, id, type and original date',async()=>{
  const disk=adapter(fixture([listing({ownerId:'local-self',images:['data:image/png;base64,AAAA']})]));const store=await createStore(disk);
  const edited=await store.update('a',{title:'修改后的笔记本',description:'适合记录课堂笔记。',category:'数码产品',campus:'成都校区',wechat:'updated_test',phone:'13800000000',id:'fake',ownerId:'peer',ownerName:'别人',type:'want',createdAt:99,price:80});
  assert.equal(edited.id,'a');assert.equal(edited.ownerId,'local-self');assert.equal(edited.ownerName,'发布人');assert.equal(edited.type,'sell');assert.equal(edited.createdAt,1);
  assert.deepEqual(edited.images,['data:image/png;base64,AAAA']);assert.equal(edited.wechat,'updated_test');assert.equal(edited.phone,'13800000000');assert.ok(edited.updatedAt);assert.equal('price' in edited,false);
  const reopened=await createStore(disk);assert.deepEqual(reopened.find('a'),edited);assert.equal(reopened.listings({query:'修改后的笔记本',campus:'成都校区'}).length,1);
  assert.equal(reopened.listings({query:'课程整理'}).length,0);
  const wanted=await reopened.publish(listing({type:'want'}));const result=await reopened.update(wanted.id,{description:'更新后的求购内容',images:['data:image/png;base64,AAAA'],type:'sell',budget:80});
  assert.equal(result.type,'want');assert.deepEqual(result.images,['data:image/png;base64,AAAA']);assert.equal('budget' in result,false);
});
test('invalid edits are rejected without changing the saved publication',async()=>{
  const store=await createStore(adapter(fixture([listing({ownerId:'local-self'})])));const before=store.find('a');
  for(const changes of [{title:' '},{description:''},{wechat:'',phone:''},{phone:'bad'},{campus:'不存在的校区'}]){
    await assert.rejects(store.update('a',changes));assert.deepEqual(store.find('a'),before);
  }
  assert.equal((await store.update('a',{title:'修正后的标题'})).title,'修正后的标题');
});
test('edit and delete reject missing posts and posts belonging to someone else',async()=>{
  const disk=adapter(fixture([listing({ownerName:'我'})]));const store=await createStore(disk);
  for(const id of ['a','missing']){await assert.rejects(store.update(id,{title:'冒名修改'}));await assert.rejects(store.remove(id));}
  assert.equal(store.find('a').title,listing().title);assert.equal(store.snapshot().listings.length,1);
});
test('deleted own publications stay deleted after reopening and other records remain untouched',async()=>{
  const disk=adapter(fixture([listing()]));const store=await createStore(disk);
  const own=await store.publish(listing());await store.remove(own.id);
  const reopened=await createStore(disk);assert.equal(reopened.find(own.id),null);assert.equal(reopened.mine().length,0);assert.equal(reopened.snapshot().listings.length,1);
  await assert.rejects(reopened.remove('a'));assert.ok(reopened.find('a'));
});
test('failed edit or delete writes keep both memory and saved records unchanged',async()=>{
  const saved=fixture([listing({ownerId:'local-self'})]);const disk=adapter(saved,true);const store=await createStore(disk);const before=store.find('a');
  await assert.rejects(store.update('a',{title:'无法保存的修改'}),/disk full/);assert.deepEqual(store.find('a'),before);
  await assert.rejects(store.remove('a'),/disk full/);assert.deepEqual(store.find('a'),before);assert.deepEqual(disk.saved(),saved);
});
