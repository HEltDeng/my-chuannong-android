'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const {webcrypto} = require('node:crypto');
const {normalizeCategory} = require('../app/src/main/assets/secondhand/store.js');
const source = fs.readFileSync(require('node:path').join(__dirname, '../app/src/main/assets/secondhand/online.js'),'utf8');
function bridge(handler) {
  const context={window:{ChuannongContent:{request:(method,path,body)=>JSON.stringify(handler(method,path,JSON.parse(body)))}},setTimeout,crypto:webcrypto,SecondhandData:{matchesListing:()=>true,normalizeCategory}};
  vm.runInNewContext(source,context);
  return context.window.ChuannongMarket;
}
const disk={read:async()=>({preferences:{campus:'成都校区',tab:'sell'}}),write:async()=>{}};
test('legacy remote categories appear as other in feeds, details and edits while preserving review fields',async()=>{
  const original={id:'legacy',type:'sell',title:'课程笔记',description:'完整的课堂记录',category:'高速材料',campus:'成都校区',images:['data:image/png;base64,AAAA'],wechat:'legacy_contact',phone:'',mine:true,status:'PUBLISHED',moderationNote:'',createdAt:1};
  let edited;
  const store=await bridge((method,path,body)=>{
    if(path.includes('/notifications'))return {ok:true,data:{items:[],total:0}};
    if(method==='PATCH'){edited=body;return {ok:true,data:{...original,...body,status:'PENDING'}};}
    return {ok:true,data:path.includes('/legacy')?original:{items:[original],total:1}};
  }).connect(disk);
  for(const record of [store.listings({})[0],store.mine()[0],await store.detail('legacy')]){
    assert.equal(record.category,'其他');
    for(const key of ['id','title','description','campus','wechat','status','moderationNote'])assert.equal(record[key],original[key]);
    assert.equal(record.images[0],original.images[0]);
  }
  await store.update('legacy',{title:'更新的笔记'});
  assert.equal(edited.category,'其他');assert.equal(store.find('legacy').status,'PENDING');
  await store.refresh();assert.equal(store.find('legacy').category,'其他');
});
test('connection failures never fall back to unreviewed local publications',async()=>{
  const offline=bridge(()=>({ok:false,error:{code:'OFFLINE',message:'offline'}}));
  await assert.rejects(offline.connect(disk),{code:'OFFLINE'});
  const denied=bridge(()=>({ok:false,error:{code:'ACCOUNT_UNAVAILABLE',message:'restricted'}}));
  await assert.rejects(denied.connect(disk),{code:'ACCOUNT_UNAVAILABLE'});
});
test('hidden own records remain manageable but do not appear in the public feed',async()=>{
  const publicRow={id:'peer',status:'PUBLISHED',mine:false,createdAt:1};
  const ownRow={id:'own',status:'VIOLATION',mine:true,createdAt:2};
  const store=await bridge((method,path)=>({ok:true,data:{items:[path.includes('mine=true')?ownRow:publicRow],total:1}})).connect(disk);
  assert.equal(store.online,true);
  assert.equal(store.mine()[0].id,'own');assert.equal(store.listings({}).length,1);
  assert.equal(store.listings({})[0].id,'peer');assert.equal(store.owns('peer'),false);
});
test('failed remote writes leave the visible store unchanged and never write local data',async()=>{
  let localWrites=0;
  const store=await bridge((method)=>method==='GET'?{ok:true,data:{items:[{id:'own',status:'PUBLISHED',mine:true}],total:1}}:{ok:false,error:{code:'OFFLINE',message:'offline'}}).connect({read:disk.read,write:async()=>{localWrites++;}});
  for(const action of [()=>store.publish({title:'sample'}),()=>store.update('own',{title:'changed'}),()=>store.remove('own')])await assert.rejects(action(),{code:'OFFLINE'});
  assert.equal(store.snapshot().listings.length,1);assert.equal(store.find('own').title,undefined);assert.equal(localWrites,0);
});
test('review transitions and server deletion replace cached own and public records',async()=>{
  let current={id:'mine',mine:true,type:'want',status:'PENDING',createdAt:1,moderationNote:''};
  let notes=[];
  const store=await bridge((method,path,body)=>{
    if(path.includes('/notifications')){
      if(method==='PATCH')notes=notes.map(n=>body.ids.includes(n.id)?{...n,read_at:123}:n);
      return {ok:true,data:method==='GET'?{items:notes,total:notes.length}:{read:true}};
    }
    const items=current&&(path.includes('mine=true')||current.status==='PUBLISHED')?[current]:[];
    return {ok:true,data:{items,total:items.length}};
  }).connect(disk);
  assert.equal(store.mine()[0].status,'PENDING');assert.equal(store.listings({}).length,0);
  current={...current,status:'PUBLISHED'};await store.refresh();assert.equal(store.listings({}).length,1);
  current={...current,status:'VIOLATION',moderationNote:'请修正描述'};notes=[{id:'notice-1',post_id:'mine',title:'测试求购',status:'VIOLATION',reason:'请修正描述',read_at:null}];
  await store.refresh();assert.equal(store.listings({}).length,0);assert.equal(store.mine()[0].moderationNote,'请修正描述');assert.equal(store.notifications()[0].reason,'请修正描述');
  await store.markRead(['notice-1']);assert.ok(store.notifications()[0].read_at);
  current=null;notes=[];await store.refresh();assert.equal(store.mine().length,0);assert.equal(store.find('mine'),null);assert.equal(store.notifications().length,0);
});
test('loss of connection hides cached public rows until a successful refresh',async()=>{
  let offline=false;const store=await bridge((method,path)=>offline?{ok:false,error:{code:'OFFLINE'}}:{ok:true,data:{items:path.includes('/notifications')?[]:[{id:'a',status:'PUBLISHED'}],total:path.includes('/notifications')?0:1}}).connect(disk);
  assert.equal(store.listings({}).length,1);offline=true;await assert.rejects(store.refresh());assert.equal(store.listings({}).length,0);assert.equal(store.connected(),false);
  offline=false;await store.refresh();assert.equal(store.listings({}).length,1);
});
