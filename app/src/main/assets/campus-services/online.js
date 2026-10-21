/* Managed providers and real intention records. Never fall back to sample content. */
(async()=>{
  const root=document.getElementById('managed-service');
  const showFailure=message=>{
    root.replaceChildren();
    const note=document.createElement('p');note.className='managed-empty';note.setAttribute('role','alert');note.textContent=message;
    const retry=document.createElement('button');retry.className='managed-primary';retry.textContent='重新连接';retry.onclick=()=>location.reload();
    root.append(note,retry);
  };
  window.ChuannongManaged={back:()=>false};
  if(!window.ChuannongContent){showFailure('请从我的川农 APP 打开此页面');return;}
  const key=location.pathname.includes('driving-school')?'driving_school':({language:'ielts_toefl',exam:'civil_postgrad',gym:'gym'}[location.hash.replace(/^#\/?/,'').split('/')[0]]);
  if(!key){showFailure('服务入口无效，请返回后重试');return;}
  const call=async(method,path,body={})=>{
    await new Promise(r=>setTimeout(r,0));
    const result=JSON.parse(window.ChuannongContent.request(method,path,JSON.stringify(body)));
    if(!result.ok)throw new Error(result.error?.message||'服务暂不可用');return result.data;
  };
  let rows;
  try{rows=(await call('GET','services/providers?category='+key)).items;}catch(error){showFailure(error.message||'暂时无法连接，请重试');return;}
  const E=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const names={driving_school:'驾校',ielts_toefl:'雅思托福',civil_postgrad:'考公考研',gym:'健身房'};
  let current=null,view='list';
  const safeImage=v=>/^\/uploads\/[0-9a-f-]+\.(png|jpg|webp)$/.test(v||'');
  const courses=r=>Array.isArray(r.details?.courses)?r.details.courses:[];
  const textList=items=>(items||[]).map(v=>`<li>${E(typeof v==='object'?JSON.stringify(v):v)}</li>`).join('');
  function list(){view='list';current=null;root.innerHTML=`<header><div><small>校外园地</small><h1>${names[key]}</h1></div><button data-online="bookings">我的预约</button></header><p class="managed-note">机构资料由我的川农后台维护</p><div class="managed-list">${rows.map(r=>`<button class="managed-card" data-online="detail" data-id="${E(r.id)}">${safeImage(r.image)?`<img src="${E(r.image)}" alt="${E(r.name)}">`:'<span class="managed-mark">'+names[key]+'</span>'}<div><h2>${E(r.name)}</h2><p>${E(r.campus)}</p><p>${E(r.summary)}</p>${r.isDemo?'<small>演示机构 · 不接受真实预约</small>':''}</div><span>›</span></button>`).join('')||'<div class="managed-empty">机构资料准备中，发布后会显示在这里。</div>'}</div>`;}
  function detail(r){view='detail';current=r;root.innerHTML=`<header><button data-online="home">‹ 返回列表</button><small>${E(r.campus)}</small></header>${safeImage(r.image)?`<img class="managed-hero" src="${E(r.image)}" alt="${E(r.name)}">`:''}<h1>${E(r.name)}</h1><p>${E(r.summary)}</p><article><h2>机构介绍</h2><p>${E(r.details?.description||'机构暂未填写详细介绍')}</p><p>${E(r.address)}</p><p>${E(r.phone)}</p><p>${E(r.details?.hours)}</p><p>${E(r.details?.pickup)}</p></article><article><h2>课程与服务</h2>${courses(r).map(c=>`<div class="managed-course"><strong>${E(c.title||c.name||'课程')}</strong><p>${E(c.description||'')}</p>${c.price!==undefined?`<span>参考价格 ¥${E(c.price)}</span>`:''}</div>`).join('')||'<p>请向机构咨询当前课程。</p>'}</article>${r.details?.facilities?.length?`<article><h2>场地设施</h2><ul>${textList(r.details.facilities)}</ul></article>`:''}<p class="managed-note">${r.isDemo?'当前为演示机构':r.bookingEnabled?'提交后由运营人员联系确认，不收取费用':'暂未开放线上预约'}</p>${r.bookingEnabled&&!r.isDemo?'<button class="managed-primary" data-online="book">登记预约意向</button>':''}`;}
  function bookingForm(){view='form';const choices=courses(current);root.innerHTML=`<header><button data-online="back-detail">‹ 返回机构</button></header><h1>登记预约意向</h1><p>${E(current.name)}</p><form id="managed-booking"><label>课程 / 服务<select name="course">${choices.length?choices.map(c=>`<option>${E(c.title||c.name||'课程咨询')}</option>`).join(''):'<option>课程咨询</option>'}</select></label><label>称呼<input name="name" maxlength="40" autocomplete="name" required></label><label>联系电话<input name="phone" maxlength="32" type="tel" autocomplete="tel" required></label><label>意向日期<input name="preferredDate" type="date" required></label><label>备注<textarea name="note" maxlength="500"></textarea></label><label class="managed-consent"><input name="consented" type="checkbox" required>同意将以上联系方式用于本次预约联系</label><p id="managed-error" role="alert"></p><button class="managed-primary">提交预约意向</button></form><p class="managed-note">需要运营人员确认，提交不会产生付款。</p>`;
    const clientId=crypto.randomUUID();root.querySelector('form').onsubmit=async e=>{e.preventDefault();const form=e.target;const submit=form.querySelector('button');submit.disabled=true;try{const value=Object.fromEntries(new FormData(form));await call('POST','services/bookings',{...value,consented:true,providerId:current.id,clientId});await bookings('已提交，等待运营人员确认');}catch(error){root.querySelector('#managed-error').textContent=error.message;submit.disabled=false;}};
  }
  async function bookings(message=''){const data=await call('GET','services/bookings?limit=100');view='bookings';const status={PENDING:'待确认',CONFIRMED:'已确认',COMPLETED:'已完成',CANCELLED:'已取消',REJECTED:'未通过'};root.innerHTML=`<header><button data-online="home">‹ 返回列表</button></header><h1>我的预约</h1><p class="managed-note">${E(message)}</p>${data.items.map(r=>`<article><small>${E(status[r.status])}</small><h2>${E(r.provider_name)}</h2><p>${E(r.course)} · ${E(r.preferred_date)}</p><p>${E(r.reply)}</p>${['PENDING','CONFIRMED'].includes(r.status)?`<button data-online="cancel" data-id="${E(r.id)}">取消预约</button>`:''}</article>`).join('')||'<div class="managed-empty">暂时没有预约</div>'}`;}
  root.addEventListener('click',async e=>{const b=e.target.closest('[data-online]');if(!b)return;try{const action=b.dataset.online;if(action==='home')list();else if(action==='detail')detail(rows.find(r=>r.id===b.dataset.id));else if(action==='back-detail')detail(current);else if(action==='book')bookingForm();else if(action==='bookings')await bookings();else if(action==='cancel'){b.disabled=true;await call('PATCH','services/bookings/'+b.dataset.id,{status:'CANCELLED'});await bookings('预约已取消');}}catch(error){b.disabled=false;let note=root.querySelector('[role=alert]');if(!note){note=document.createElement('p');note.setAttribute('role','alert');root.prepend(note);}note.textContent=error.message;}});
  window.ChuannongManaged={back(){if(view==='list')return false;if(view==='form')detail(current);else list();return true;}};
  list();
})();
