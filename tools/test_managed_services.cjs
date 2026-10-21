'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

async function open(module, response, route = '#/gym') {
  const root = {innerHTML:'正在加载…', children:[], replaceChildren(){this.children=[];this.innerHTML='';}, append(...nodes){this.children.push(...nodes);}, addEventListener(){}};
  let retries=0;
  const context = {
    window:{}, location:{pathname:`/assets/${module}/index.html`,hash:route,reload(){retries++;}},
    document:{getElementById:()=>root,createElement:()=>({setAttribute(){}})},
    setTimeout:callback=>callback(),
  };
  if(response)context.window.ChuannongContent={request:(method,url)=>JSON.stringify(response(method,url))};
  await vm.runInNewContext(fs.readFileSync(path.join(__dirname,`../app/src/main/assets/${module}/online.js`),'utf8'),context);
  return {root,context,retries:()=>retries};
}

for(const module of ['driving-school','campus-services']) {
  test(`${module}: missing bridge has no prototype or booking fallback`,async()=>{
    const page=await open(module);
    assert.equal(page.root.children[0].textContent,'请从我的川农 APP 打开此页面');
    assert.equal(page.context.window.ChuannongManaged.back(),false);
  });
  test(`${module}: failed load exposes a safe retry without fake providers`,async()=>{
    const page=await open(module,()=>({ok:false,error:{message:'<img onerror=invalid> 网络异常'}}));
    assert.equal(page.root.innerHTML,'');
    assert.equal(page.root.children[0].textContent,'<img onerror=invalid> 网络异常');
    assert.equal(page.root.children[1].textContent,'重新连接');
    page.root.children[1].onclick();assert.equal(page.retries(),1);
    assert.equal(page.context.window.ChuannongManaged.back(),false);
  });
  test(`${module}: an empty backend stays empty`,async()=>{
    const requests=[];
    const page=await open(module,(method,url)=>{requests.push([method,url]);return {ok:true,data:{items:[]}};});
    assert.deepEqual(requests,[['GET','services/providers?category='+(module==='driving-school'?'driving_school':'gym')]]);
    assert.match(page.root.innerHTML,/机构资料准备中/);
    assert.doesNotMatch(page.root.innerHTML,/data-online="detail"/);
    assert.equal(page.context.window.ChuannongManaged.back(),false);
  });
  test(`${module}: backend cards preserve the managed layout and escape content`,async()=>{
    const page=await open(module,()=>({ok:true,data:{items:[{id:'provider',name:'<b>机构</b>',campus:'成都校区',summary:'课程'}]}}));
    assert.match(page.root.innerHTML,/class="managed-card"/);
    assert.match(page.root.innerHTML,/&lt;b&gt;机构&lt;\/b&gt;/);
    assert.doesNotMatch(page.root.innerHTML,/<b>机构<\/b>/);
  });
}
