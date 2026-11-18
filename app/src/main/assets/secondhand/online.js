/* Backend-reviewed records never fall back to publishing unreviewed local data. */
window.ChuannongMarket = {
  async connect(adapter) {
    if (!window.ChuannongContent) return null;
    const call = async (method,path,body={}) => {
      await new Promise(resolve=>setTimeout(resolve,0));
      const result=JSON.parse(window.ChuannongContent.request(method,'community/secondhand'+path,JSON.stringify(body)));
      if(!result.ok){const e=new Error(result.error?.message||'操作失败');e.code=result.error?.code;throw e;}
      return result.data;
    };
    const blockedUsers=()=>{try{return JSON.parse(window.ChuannongContent.blockedUsers?.()||'[]').map(user=>user.id);}catch{return [];}};
    let rows=[],notifications=[],connected=true,work=Promise.resolve(),blocked=blockedUsers();
    const queue=fn=>{const next=work.then(fn);work=next.catch(()=>{});return next;};
    async function pages(path){
      const first=await call('GET',path+'limit=100');const items=[...first.items];
      while(items.length<first.total){const page=await call('GET',path+'limit=100&offset='+items.length);if(!page.items.length)break;items.push(...page.items);}
      return items;
    }
    async function refresh(){
      try {
        const publicRows=await pages('?');const ownRows=await pages('?mine=true&');const notices=await pages('/notifications?');
        const previous=new Map(rows.map(r=>[r.id,r]));
        const next=[...new Map([...publicRows,...ownRows].map(r=>[r.id,r])).values()].map(r=>({...previous.get(r.id),...r,category:SecondhandData.normalizeCategory(r.category),wechat:r.wechat||previous.get(r.id)?.wechat||'',phone:r.phone||previous.get(r.id)?.phone||''}));
        const nextBlocked=blockedUsers();
        const changed=JSON.stringify([rows,notifications,connected,blocked])!==JSON.stringify([next,notices,true,nextBlocked]);
        rows=next;notifications=notices;blocked=nextBlocked;connected=true;return changed;
      }catch(error){connected=false;throw error;}
    }
    await refresh();
    let preferences=(await adapter.read())?.preferences||{campus:'全部校区',tab:'sell'};
    const copy=value=>JSON.parse(JSON.stringify(value));
    const replace=value=>{const item={...value,category:SecondhandData.normalizeCategory(value.category)};rows=rows.filter(r=>r.id!==item.id);rows.unshift(item);return copy(item);};
    return {
      online:true,
      connected:()=>connected,
      refresh:()=>queue(refresh),
      snapshot:()=>({version:2,listings:copy(rows),preferences:copy(preferences)}),
      notifications:()=>copy(notifications),
      async markRead(ids){return queue(async()=>{await call('PATCH','/notifications',{ids});notifications=notifications.map(n=>ids.includes(n.id)?{...n,read_at:n.read_at||Date.now()}:n);});},
      preferences:()=>copy(preferences),
      async setPreferences(value){preferences={...preferences,...value};const old=await adapter.read();if(old)await adapter.write({...old,preferences});},
      listings:options=>connected?rows.filter(r=>r.status==='PUBLISHED'&&(!r.ownerId||!blockedUsers().includes(r.ownerId))&&SecondhandData.matchesListing(r,options)).sort((a,b)=>b.createdAt-a.createdAt).map(copy):[],
      find:id=>copy(rows.find(r=>r.id===id)||null),
      isBlocked:item=>!!item?.ownerId&&!item.mine&&blockedUsers().includes(item.ownerId),
      owns:id=>rows.some(r=>r.id===id&&r.mine),
      mine:(type='all')=>rows.filter(r=>r.mine&&(type==='all'||r.type===type)).map(copy),
      async detail(id){return queue(async()=>{try{return replace(await call('GET','/'+id));}catch(error){if(error.code==='NOT_FOUND')rows=rows.filter(r=>r.id!==id);throw error;}});},
      async publish(values){return queue(async()=>replace(await call('POST','',{...values,clientId:values.clientId||crypto.randomUUID()})));},
      async update(id,values){return queue(async()=>replace(await call('PATCH','/'+id,{...rows.find(r=>r.id===id),...values})));},
      async remove(id){return queue(async()=>{const old=rows.find(r=>r.id===id);await call('DELETE','/'+id);rows=rows.filter(r=>r.id!==id);notifications=notifications.filter(n=>n.post_id!==id);return copy(old);});},
    };
  }
};
