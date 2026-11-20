const analysisYear=document.querySelector('#analysis-year');
const analysisContent=document.querySelector('#analysis-content');
const analysisIssues=document.querySelector('#analysis-issues');
let analysisLoad=0,analysisArchives=[],analysisSelectedMonth='';
analysisYear.innerHTML=`<option value="${new Date().getFullYear()}">${new Date().getFullYear()} 年</option>`;
const analysisPercent=value=>value==null?'暂无可比数据':`${Number(value).toFixed(2)}%`;
const analysisText=value=>escape(String(value??'').replace(/—/g,'--').replace(/([\u4e00-\u9fff])([A-Za-z0-9])/g,'$1 $2').replace(/([A-Za-z0-9])([\u4e00-\u9fff])/g,'$1 $2'));
// Exact decimal units for chart ratios; only pixel coordinates become JavaScript numbers.
function analysisUnits(value){
  const match=String(value??0).match(/^(-?)(\d+)(?:\.(\d+))?$/);
  if(!match)return 0n;
  return BigInt(match[2]+(match[3]||'').padEnd(4,'0').slice(0,4))*(match[1]?-1n:1n);
}
const analysisAbs=value=>value<0n?-value:value;
const analysisRatio=(value,total,size)=>total?Number(value*BigInt(size*100)/total)/100:0;
const analysisSentences=text=>String(text||'').match(/[^。！？\n]+[。！？]?/g)||[];

function renderAnalysisIssues(){
  analysisIssues.innerHTML=analysisArchives.filter(item=>item.month.slice(0,4)===analysisYear.value).map(item=>`<button type="button" data-analysis-month="${escape(item.month)}" aria-label="${escape(item.month)} 月度总结" aria-pressed="${item.month===analysisSelectedMonth}"><strong>${Number(item.month.slice(5))}</strong> <small>月</small></button>`).join('');
}

function showAnalysisYear(){
  const items=analysisArchives.filter(item=>item.month.slice(0,4)===analysisYear.value);
  analysisSelectedMonth=items.some(item=>item.month===analysisSelectedMonth)?analysisSelectedMonth:(items[0]?.month||'');
  renderAnalysisIssues();
  if(!items.length){
    ++analysisLoad;
    analysisContent.innerHTML=`<div class="journal-empty"><span>${escape(analysisYear.value)} 年</span><h3>本年暂无月度总结</h3><p>在对话中要求总结已导入账单的月份，同步后即可在这里阅读。</p></div>`;
    return;
  }
  return loadAnalysis();
}

async function loadAnalyses(){
  const selected=analysisYear.value,load=++analysisLoad;
  analysisContent.innerHTML='<p class="journal-state">正在展开月度归档…</p>';
  try{
    const items=await request('/api/analyses');
    if(load!==analysisLoad)return;
    analysisArchives=[...items].sort((a,b)=>a.month.localeCompare(b.month));
    const years=[...new Set([String(new Date().getFullYear()),...items.map(item=>item.month.slice(0,4))])].sort().reverse();
    analysisYear.innerHTML=years.map(year=>`<option value="${escape(year)}">${escape(year)} 年</option>`).join('');
    analysisYear.value=years.includes(selected)?selected:String(new Date().getFullYear());
    await showAnalysisYear();
  }catch(error){if(load===analysisLoad)analysisContent.innerHTML=`<p class="journal-state" role="alert">${escape(error.message)}</p>`}
}

function analysisCashChart(item,previous){
  const metrics=[['收入','income'],['支出','expense'],['结余','balance']];
  const values=metrics.flatMap(([,key])=>[analysisUnits(item.current[key]),...(previous?[analysisUnits(previous[key])]:[])]);
  const positive=values.reduce((max,v)=>v>max?v:max,0n),negative=values.reduce((max,v)=>v<0n&&-v>max?-v:max,0n);
  const range=positive+negative||1n,baseline=32+analysisRatio(positive,range,180);
  const bars=metrics.map(([label,key],index)=>{
    const x=58+index*166,current=analysisUnits(item.current[key]);
    const bar=(amount,start,old)=>{
      const height=analysisRatio(analysisAbs(amount),range,180),top=amount<0n?baseline:baseline-height;
      return `<rect x="${start}" y="${top}" width="${previous?44:60}" height="${height}" rx="3" class="${old?'journal-bar-previous':amount<0n?'journal-bar-negative':'journal-bar-current'}"><title>${old?'上月':'本月'}${label}：${yuan(old?previous[key]:item.current[key])}</title></rect>`;
    };
    const top=current<0n?baseline+analysisRatio(-current,range,180)+17:baseline-analysisRatio(current,range,180)-11;
    return `${previous?bar(analysisUnits(previous[key]),x,true):''}${bar(current,x+(previous?52:17),false)}<text x="${x+(previous?74:47)}" y="${top}" text-anchor="middle" class="journal-plot-value">${yuan(item.current[key])}</text><text x="${x+48}" y="252" text-anchor="middle" class="journal-plot-label">${label}</text>`;
  }).join('');
  const description=metrics.map(([label,key])=>`${label}：本月 ${yuan(item.current[key])}${previous?'，上月 '+yuan(previous[key]):''}`).join('；');
  return `<svg class="journal-cash-chart" viewBox="0 0 560 272" role="img" aria-label="${escape(description)}"><line x1="24" y1="${baseline}" x2="536" y2="${baseline}" class="journal-zero-line"/>${bars}</svg>`;
}

function analysisCategoryChart(item,previous){
  const rows=item.categories;
  if(!rows.length)return '<p class="journal-state">本月没有已记录的分类支出。</p>';
  const max=rows.reduce((value,row)=>[analysisUnits(row.amount),...(previous?[analysisUnits(row.previousAmount)]:[])].reduce((m,v)=>v>m?v:m,value),0n)||1n;
  const rowMarkup=row=>`<div class="journal-category ${row.category==='未分类'?'is-unclassified':''}"><div class="journal-category-label"><span>${analysisText(row.category)}</span><div><strong>${yuan(row.amount)}</strong><small>${row.share==null?'无占比':analysisPercent(row.share)}</small></div></div><div class="journal-category-bars" role="img" aria-label="${escape(row.category)}：本月 ${yuan(row.amount)}${previous?'，上月 '+yuan(row.previousAmount):''}"><div class="journal-category-track"><span style="width:${analysisRatio(analysisUnits(row.amount),max,100)}%"></span></div>${previous?`<div class="journal-category-track previous"><span style="width:${analysisRatio(analysisUnits(row.previousAmount),max,100)}%"></span></div>`:''}</div></div>`;
  return `<div class="journal-category-chart">${rows.slice(0,5).map(rowMarkup).join('')}${rows.length>5?`<details class="journal-more-categories"><summary>展开其余 ${rows.length-5} 个分类 <span aria-hidden="true">＋</span></summary>${rows.slice(5).map(rowMarkup).join('')}</details>`:''}</div>`;
}

function renderMonthlyJournal(item){
  const current=item.current,partial=/非完整月/.test(item.coverage),previous=partial?null:item.previous;
  const isNegative=analysisUnits(current.balance)<0n;
  const [year,month]=item.month.split('-');
  const observation=item.sections[0],action=item.sections[1],remaining=item.sections.slice(2);
  const legend=`<div class="journal-legend"><span><i></i>本月</span>${previous?'<span><i class="previous"></i>上月</span>':''}</div>`;
  const paragraphs=section=>analysisSentences(section?.text).map(text=>`<p>${analysisText(text)}</p>`).join('');
  return `<article class="journal-article">
    <header class="journal-cover"><div class="journal-issue-number" aria-hidden="true"><span>${escape(year)} / MONTHLY</span><b>${escape(month)}</b><small>月度财务观察</small></div><div class="journal-cover-copy"><div class="journal-edition"><span>个人财务 · ${escape(year)} 年 ${escape(month)} 月</span><span class="journal-status ${partial?'partial':''}">${partial?'非完整月份':'完整性待确认'}</span></div><h3>${analysisText(item.title)}</h3><p>${analysisText(item.overview)}</p></div></header>
    <section class="journal-numbers" aria-label="本月关键收支"><div><span>本月收入</span><strong>${yuan(current.income)}</strong><small>已导入并确认的收入</small></div><div><span>本月支出</span><strong>${yuan(current.expense)}</strong><small>${partial?'非完整月，暂不比较':item.expenseChangeRate==null?'暂无上月可比支出':'较上月 '+analysisPercent(item.expenseChangeRate)}</small></div><div class="${isNegative?'is-negative':'is-positive'}"><span>本月${isNegative?'缺口':'结余'}</span><strong>${yuan(isNegative?String(current.balance).replace(/^-/,''):current.balance)}</strong><small>账面结余率 ${analysisPercent(current.balanceRate)}</small></div></section>
    <div class="journal-chart-spread"><section class="journal-chart-block"><div class="journal-section-title"><div><span class="journal-index">01 / CASH FLOW</span><h4>收与支，一眼看清</h4></div>${legend}</div><p class="journal-caption">${partial?'本月记录不完整，暂不展示上月对比。':previous?'同一金额刻度，对比本月与上月的已导入记录。':'暂无上月记录，先看本月的收支轮廓。'}</p>${analysisCashChart(item,previous)}<div class="journal-chart-note"><span>${isNegative?'支出高于收入':'收入与支出的距离'}</span><p>${isNegative?'本月已记录支出超过收入。先核对账单和资金用途，再决定如何安排下月。':'结余是账面收支之差，仍需结合资金往来与账单覆盖情况理解。'}</p></div></section>
    <section class="journal-chart-block"><div class="journal-section-title"><div><span class="journal-index">02 / SPENDING</span><h4>钱，花在了哪里</h4></div>${legend}</div><p class="journal-caption">按本月支出排序，条形长度采用同一金额刻度。</p>${analysisCategoryChart(item,previous)}</section></div>
    <div class="journal-reading-spread"><section class="journal-observations"><span class="journal-index">03 / OBSERVATIONS</span><h4>数字背后的变化</h4>${paragraphs(observation)}</section><section class="journal-actions"><span class="journal-index">04 / NEXT MONTH</span><h4>下个月，可以这样做</h4><ol>${analysisSentences(action?.text).map(text=>`<li>${analysisText(text)}</li>`).join('')}</ol></section></div>
    <footer class="journal-footer"><div><span>本期依据</span><p>交易日期 ${escape(item.dataFrom)} 至 ${escape(item.dataThrough)} · 已确认 ${escape(item.confirmedTransactionCount)} 笔</p><p>${analysisText(item.coverage)}</p></div><details><summary>阅读口径与数据说明 <span aria-hidden="true">＋</span></summary><p>${analysisText(item.basis)}</p>${remaining.map(section=>`<p><strong>${analysisText(section.heading)}：</strong>${analysisText(section.text)}</p>`).join('')}<p>本期内容为生成时保存的快照。补录账单或修改分类后，重新总结该月份即可同步覆盖。</p></details><div class="journal-colophon"><span>AI 对话总结 · 本地归档</span><time>更新于 ${escape(item.generatedAt)}</time></div></footer>
  </article>`;
}

async function loadAnalysis(){
  const load=++analysisLoad;
  renderAnalysisIssues();
  analysisContent.innerHTML='<p class="journal-state">正在展开这一期月报…</p>';
  try{
    const item=await request('/api/analyses/'+encodeURIComponent(analysisSelectedMonth));
    if(load!==analysisLoad)return;
    analysisContent.innerHTML=renderMonthlyJournal(item);
    const notice=document.createElement('p');
    notice.className='journal-notice';
    notice.textContent='正在核对本月归档…';
    notice.setAttribute('role','status');
    analysisContent.prepend(notice);
    try{
      const status=await request('/api/analyses/'+encodeURIComponent(item.month)+'/status');
      if(load===analysisLoad){notice.textContent=status.message;if(status.stale){notice.className='journal-notice is-warning';notice.setAttribute('role','alert')}}
    }catch(error){if(load===analysisLoad){notice.className='journal-notice is-warning';notice.textContent='暂时无法核对归档是否过期，可稍后刷新。'}}
  }catch(error){if(load===analysisLoad)analysisContent.innerHTML=`<p class="journal-state" role="alert">${escape(error.message)}</p>`}
}
analysisYear.onchange=showAnalysisYear;
analysisIssues.onclick=event=>{const button=event.target.closest('[data-analysis-month]');if(button){analysisSelectedMonth=button.dataset.analysisMonth;loadAnalysis()}};
document.querySelector('#analysis-refresh').onclick=loadAnalyses;
document.querySelector('[data-view="analysis-view"]').addEventListener('click',loadAnalyses);
