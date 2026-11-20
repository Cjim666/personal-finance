const yuan=value=>new Intl.NumberFormat('zh-CN',{style:'currency',currency:'CNY'}).format(value||0);
const today=new Date().toISOString().slice(0,10);
const sourceNames={ALIPAY:'支付宝',WECHAT:'微信支付',CMB:'招商银行'};
let categories=[],reportTransactions=[],reviewRange=null;

const period=document.querySelector('#period'),date=document.querySelector('#date'),from=document.querySelector('#from'),to=document.querySelector('#to'),dateField=document.querySelector('#date-field');
const income=document.querySelector('#income'),expense=document.querySelector('#expense'),balance=document.querySelector('#balance'),treemap=document.querySelector('#treemap');
const transactions=document.querySelector('#transactions'),transactionTitle=document.querySelector('#transaction-title'),transactionCount=document.querySelector('#transaction-count'),reportCategory=document.querySelector('#report-category'),directionFilter=document.querySelector('#direction-filter'),transactionFrom=document.querySelector('#transaction-from'),transactionTo=document.querySelector('#transaction-to'),sourceFilter=document.querySelector('#source-filter');
const batches=document.querySelector('#batches'),categoryForm=document.querySelector('#category-form'),categoryName=document.querySelector('#category-name'),categoryOrder=document.querySelector('#category-order'),categoryManager=document.querySelector('#category-manager');
const ruleForm=document.querySelector('#rule-form'),ruleCategory=document.querySelector('#rule-category'),ruleSource=document.querySelector('#rule-source'),ruleKeyword=document.querySelector('#rule-keyword'),rulePriority=document.querySelector('#rule-priority'),ruleManager=document.querySelector('#rule-manager');
const uploadForm=document.querySelector('#upload-form'),billFiles=document.querySelector('#bill-files'),uploadButton=document.querySelector('#upload-button'),scanButton=document.querySelector('#scan-button'),zipPassword=document.querySelector('#zip-password'),pendingFiles=document.querySelector('#pending-files'),uploadResults=document.querySelector('#upload-results'),importTrigger=document.querySelector('#import-trigger'),importModal=document.querySelector('#import-modal');
const periodChart=document.querySelector('#period-chart'),periodExpenseTitle=document.querySelector('#period-expense-title'),periodExpenseSubtitle=document.querySelector('#period-expense-subtitle');
const recategorize=document.querySelector('#recategorize'),refresh=document.querySelector('#refresh'),tabs=document.querySelectorAll('.nav-tab'),views=document.querySelectorAll('.view');

for(const id of ['date','from','to']) document.querySelector('#'+id).value=today;
sourceFilter.innerHTML='<option value="ALL">全部来源</option>'+Object.entries(sourceNames).map(([value,name])=>`<option value="${value}">${name}</option>`).join('');

async function request(url,options={}){
  const headers=options.body instanceof FormData?{}:{'Content-Type':'application/json'};
  const response=await fetch(url,{...options,headers:{...headers,...options.headers}});
  if(response.status===204)return null;
  const body=response.headers.get('content-type')?.includes('application/json')?await response.json():{message:await response.text()};
  if(!response.ok)throw new Error(body.message||'请求失败');
  return body;
}

function escape(value){return String(value??'').replace(/[&<>"']/g,char=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]))}
function directionName(value){return {INCOME:'收入',EXPENSE:'支出',NEUTRAL:'中性'}[value]||value}
function toggleDates(){const custom=period.value==='CUSTOM';document.querySelectorAll('.custom-field').forEach(element=>element.style.display=custom?'grid':'none');dateField.style.display=custom?'none':'grid'}
function showView(viewId){views.forEach(view=>view.classList.toggle('hidden',view.id!==viewId));tabs.forEach(tab=>tab.classList.toggle('active',tab.dataset.view===viewId))}

async function loadReport(){
  try{
    const query=new URLSearchParams({period:period.value});
    if(period.value==='CUSTOM'){query.set('from',from.value);query.set('to',to.value)}else query.set('date',date.value);
    const report=await request('/api/reports?'+query);
    reviewRange={from:report.from,to:report.to};
    document.querySelector("#duplicate-review").textContent="";
    income.textContent=yuan(report.income);expense.textContent=yuan(report.expense);balance.textContent=yuan(report.balance);renderPeriodExpense(report.expenseByPeriod||[]);
    reportTransactions=report.transactions||[];
    renderReportCategoryOptions();renderChart(report.expenseByCategory||{});renderTransactions();
  }catch(error){alert(error.message)}
}

function renderPeriodExpense(items){
  const titles={MONTH:['年内每月支出','所选日期所属年份'],YEAR:['近五年支出','以所选年份为结束年份'],CUSTOM:['支出趋势','当前自定义区间暂不拆分']};
  const weekMonth=date.value.slice(0,7).replace('-','');
  const [title,subtitle]=period.value==='WEEK'?['月内每周支出',weekMonth]:(titles[period.value]||titles.MONTH);periodExpenseTitle.textContent=title;periodExpenseSubtitle.textContent=subtitle;
  if(!items.length){periodChart.innerHTML='<div class="treemap-empty"><span>◌</span><b>暂无支出数据</b><small>确认账单后，这里会显示支出趋势</small></div>';return}
  const max=Math.max(...items.map(item=>Number(item.amount)),1),gridMax=Math.ceil(max/1000)*1000||1000;
  periodChart.innerHTML=`<div class="bar-chart"><div class="bar-grid"><span>${yuan(gridMax)}</span><span>${yuan(gridMax/2)}</span><span>¥0.00</span></div><div class="bars">${items.map((item,index)=>{const amount=Number(item.amount),height=amount/gridMax*100,label=period.value==='WEEK'?`week${index+1}`:item.label;return `<div class="bar-column"><b style="bottom:calc(${height}% + 22px)">${yuan(amount)}</b><div class="bar" style="height:${Math.max(amount?4:0,height)}%"></div><span>${escape(label)}</span></div>`}).join('')}</div></div>`;
}

function renderReportCategoryOptions(){
  const selected=reportCategory.value;
  reportCategory.innerHTML='<option value="">全部分类</option>'+categories.filter(category=>category.active).map(category=>`<option value="${escape(category.name)}">${escape(category.name)}</option>`).join('');
  reportCategory.value=categories.some(category=>category.name===selected&&category.active)?selected:'';
}

function renderChart(values){
  const items=Object.entries(values).sort((a,b)=>Number(b[1])-Number(a[1])),colors=['#176b47','#2f8c61','#62b884','#d2a64a','#c86b57','#8b6db1','#4b91b5','#dd8c4c'];
  if(!items.length){treemap.innerHTML='<div class="treemap-empty"><span>◌</span><b>暂无支出数据</b><small>确认账单后，这里会显示你的支出分布</small></div>';return}
  const total=Object.values(values).reduce((sum,value)=>sum+Number(value),0);
  treemap.innerHTML=items.map(([name,value],index)=>{const ratio=Number(value)/total;return `<button class="tree-tile ${reportCategory.value===name?'active':''}" data-category="${escape(name)}" style="--tile-size:${Math.max(19,Math.round(ratio*100))}%;--tile-color:${colors[index%colors.length]}"><span>${escape(name)}</span><b>${yuan(value)}</b><small>${Math.round(ratio*100)}%</small></button>`}).join('');
  treemap.querySelectorAll('.tree-tile').forEach(button=>button.onclick=()=>{reportCategory.value=reportCategory.value===button.dataset.category?'':button.dataset.category;renderChart(values);renderTransactions()});
}

function filteredTransactions(){
  const fromDate=transactionFrom.value?new Date(`${transactionFrom.value}T00:00:00`):null;
  const toDate=transactionTo.value?new Date(`${transactionTo.value}T23:59:59`):null;
  return reportTransactions.filter(transaction=>{
    const occurredAt=new Date(transaction.occurredAt);
    return (!reportCategory.value||transaction.category===reportCategory.value)&&
      (directionFilter.value==='ALL'||transaction.direction===directionFilter.value)&&
      (!fromDate||occurredAt>=fromDate)&&(!toDate||occurredAt<=toDate)&&
      (sourceFilter.value==='ALL'||transaction.source===sourceFilter.value);
  });
}

let sortField='occurredAt', ascending=false;
const sortButtons=document.querySelectorAll('button[data-sort]');
sortButtons.forEach(button=>button.onclick=()=>{
  ascending=button.dataset.sort===sortField ? !ascending : true;
  sortField=button.dataset.sort;
  sortButtons.forEach(header=>{
    const active=header===button;
    header.parentElement.setAttribute('aria-sort',active?(ascending?'ascending':'descending'):'none');
    header.querySelector('span').textContent=active?(ascending?'↑':'↓'):'↕';
  });
  renderTransactions();
});

function renderTransactions(){
  syncTransactionFilters();
  const rows=filteredTransactions().sort((left,right)=>{
    const amount=sortField==='amountUnits';
    const a=amount?BigInt(left.amountUnits):left.occurredAt;
    const b=amount?BigInt(right.amountUnits):right.occurredAt;
    const order=a<b?-1:a>b?1:0;
    return ascending?order:-order;
  });
  const filters=[reportCategory.value,directionFilter.value==='ALL'?'':directionName(directionFilter.value),sourceFilter.value==='ALL'?'':sourceNames[sourceFilter.value],transactionFrom.value||transactionTo.value?`${transactionFrom.value||'开始'}～${transactionTo.value||'现在'}`:''].filter(Boolean);
  transactionTitle.textContent=filters.length?`交易明细｜${filters.join('／')}`:'交易明细';
  transactionCount.textContent=`当前显示 ${rows.length} 笔交易`;
  transactions.innerHTML=rows.map(transaction=>`<tr><td>${escape(transaction.counterparty||transaction.summary||'-')}</td><td>${sourceNames[transaction.source]||transaction.source}</td><td class="${transaction.direction==='INCOME'?'income':transaction.direction==='EXPENSE'?'expense':'neutral'}">${directionName(transaction.direction)}</td><td><select onchange="assignCategory(${transaction.id},this.value)">${categories.filter(category=>category.active).map(category=>`<option value="${category.id}" ${category.name===transaction.category?'selected':''}>${escape(category.name)}</option>`).join('')}</select></td><td>${yuan(transaction.amount)}</td><td>${transaction.occurredAt.replace('T',' ')}</td></tr>`).join('')||'<tr><td colspan="6">没有符合筛选条件的交易</td></tr>';
}

function syncTransactionFilters(){
  document.querySelectorAll('[data-clear-filter]').forEach(button=>{
    const control=document.getElementById(button.dataset.clearFilter);
    const active=control.value!==(control.id==='direction-filter'||control.id==='source-filter'?'ALL':'');
    button.disabled=!active;
    button.closest('.filter-field').classList.toggle('is-active',active);
  });
  treemap.querySelectorAll('.tree-tile').forEach(button=>button.classList.toggle('active',reportCategory.value===button.dataset.category));
}

document.querySelectorAll('[data-clear-filter]').forEach(button=>button.onclick=()=>{
  const control=document.getElementById(button.dataset.clearFilter);
  control.value=control.id==='direction-filter'||control.id==='source-filter'?'ALL':'';
  renderTransactions();
  control.focus();
});

async function assignCategory(id,categoryId){try{const syncSimilar=confirm('是否需要同步所有相关类似的支付分类？');await request(`/api/transactions/${id}/category`,{method:'PUT',body:JSON.stringify({categoryId:Number(categoryId),syncSimilar})});await loadCategories();await loadReport()}catch(error){alert(error.message)}}
async function loadBatches(){const list=await request('/api/imports');batches.innerHTML=list.map(batch=>`<div class="batch"><b>${escape(batch.fileName)}</b><span>${batch.status}</span><span>解析 ${batch.totalCount} 条，去重 ${batch.duplicateCount} 条</span>${batch.status==='PENDING_CONFIRMATION'?`<button onclick="confirmBatch(${batch.id})">确认入账</button>`:''}</div>`).join('')||'暂无导入批次'}
async function confirmBatch(id){try{await request(`/api/imports/${id}/confirm`,{method:'POST'});await Promise.all([loadBatches(),loadReport()])}catch(error){alert(error.message)}}
function renderUploadResults(results){const statusName={UPLOADED:'上传成功',DUPLICATE:'文件重复',FAILED:'处理失败'};uploadResults.innerHTML=results.map(result=>`<div class="upload-result ${result.status.toLowerCase()}"><b>${escape(result.fileName)}</b><span>${statusName[result.status]||escape(result.status)}</span><small>${escape(result.message)}</small></div>`).join('')}
async function loadPendingFiles(){try{const files=await request('/api/imports/pending-files');pendingFiles.innerHTML=files.map(file=>`<div class="pending-file"><span title="${escape(file.fileName)}">${escape(file.fileName)}</span>${file.encrypted?`<label>密码<input type="password" autocomplete="off" data-zip-password="${escape(file.fileName)}" placeholder="请输入该 ZIP 密码" required></label>`:`<small>${file.zip?'无需密码':'直接解析'}</small>`}</div>`).join('')||'<p class="muted">文件夹中暂无支持的账单文件。</p>';scanButton.disabled=!files.length}catch(error){pendingFiles.innerHTML=`<p class="error">${escape(error.message)}</p>`;scanButton.disabled=true}}

async function loadCategories(){
  categories=await request('/api/categories');renderReportCategoryOptions();
  ruleCategory.innerHTML=categories.filter(category=>category.active).map(category=>`<option value="${category.id}">${escape(category.name)}</option>`).join('');
  categoryManager.innerHTML=categories.map(category=>{const system=['未分类','忽略'].includes(category.name),name=escape(JSON.stringify(category.name));return `<div class="manage"><span>${escape(category.name)}</span><small>排序 ${category.sortOrder}，${category.active?'启用':'停用'}${system?'，系统分类':''}</small>${system?'':`<button class="secondary" onclick="toggleCategory(${category.id},${name},${category.sortOrder},${!category.active})">${category.active?'停用':'启用'}</button><button class="danger" onclick="deleteCategory(${category.id},${name})">删除</button>`}</div>`}).join('');
  const rules=await request('/api/category-rules');
  ruleManager.innerHTML=rules.map(rule=>`<div class="manage"><span>${escape(rule.category.name)}：${rule.source||'全部来源'} ${escape(rule.keyword||'全部交易')}</span><small>优先级 ${rule.priority}</small><button class="secondary" onclick="deleteRule(${rule.id})">删除</button></div>`).join('');
}

async function toggleCategory(id,name,sortOrder,active){try{await request(`/api/categories/${id}`,{method:'PUT',body:JSON.stringify({name,sortOrder,active})});await loadCategories();await loadReport()}catch(error){alert(error.message)}}
async function deleteCategory(id,name){if(!confirm(`确定删除分类“${name}”吗？该分类下的交易会转为“未分类”，关联自动规则也会删除。`))return;try{const result=await request(`/api/categories/${id}`,{method:'DELETE'});alert(`分类已删除，${result.movedTransactions} 笔交易已转为未分类，${result.deletedRules} 条关联规则已删除。`);await loadCategories();await loadReport()}catch(error){alert(error.message)}}
async function deleteRule(id){if(confirm('删除该规则？')){try{await request(`/api/category-rules/${id}`,{method:'DELETE'});await loadCategories()}catch(error){alert(error.message)}}}

categoryForm.onsubmit=async event=>{event.preventDefault();try{await request('/api/categories',{method:'POST',body:JSON.stringify({name:categoryName.value,sortOrder:Number(categoryOrder.value),active:true})});categoryName.value='';await loadCategories();await loadReport()}catch(error){alert(error.message)}};
ruleForm.onsubmit=async event=>{event.preventDefault();try{await request('/api/category-rules',{method:'POST',body:JSON.stringify({categoryId:Number(ruleCategory.value),source:ruleSource.value||null,keyword:ruleKeyword.value||null,priority:Number(rulePriority.value),active:true})});ruleKeyword.value='';await loadCategories()}catch(error){alert(error.message)}};
recategorize.onclick=async()=>{try{await request('/api/categories/recategorize',{method:'POST'});await loadReport()}catch(error){alert(error.message)}};
function openImport(){importModal.classList.remove('hidden');loadPendingFiles()}
function closeImport(){importModal.classList.add('hidden')}
importTrigger.onclick=openImport;importModal.querySelectorAll('[data-close-import]').forEach(element=>element.onclick=closeImport);
uploadForm.onsubmit=async event=>{event.preventDefault();if(!billFiles.files.length)return;uploadButton.disabled=true;uploadButton.textContent='上传处理中…';uploadResults.innerHTML='<p class="muted">正在上传并解析账单，请稍候。</p>';try{const formData=new FormData();for(const file of billFiles.files)formData.append('files',file);if(zipPassword.value)formData.append('zipPassword',zipPassword.value);const results=await request('/api/imports/upload',{method:'POST',body:formData});renderUploadResults(results);billFiles.value='';zipPassword.value='';await Promise.all([loadBatches(),loadReport()])}catch(error){uploadResults.innerHTML=`<div class="upload-result failed"><b>上传失败</b><small>${escape(error.message)}</small></div>`}finally{uploadButton.disabled=false;uploadButton.textContent='上传并解析'}};
scanButton.onclick=async()=>{const passwordInputs=[...pendingFiles.querySelectorAll('[data-zip-password]')];if(passwordInputs.some(input=>!input.value)){uploadResults.innerHTML='<div class="upload-result failed"><b>无法开始解析</b><small>请填写每个加密 ZIP 的密码。</small></div>';passwordInputs.find(input=>!input.value)?.focus();return}scanButton.disabled=true;scanButton.textContent='解析中…';uploadResults.innerHTML='<p class="muted">正在解析待导入文件夹，请稍候。</p>';try{const passwords=Object.fromEntries(passwordInputs.map(input=>[input.dataset.zipPassword,input.value]));const results=await request('/api/imports/scan',{method:'POST',body:JSON.stringify({zipPasswords:passwords})});uploadResults.innerHTML=results.length?`<div class="upload-result uploaded"><b>文件夹解析完成</b><span>成功</span><small>已处理 ${results.length} 个账单文件，请检查下方待确认批次。</small></div>`:'<p class="muted">待导入文件夹中没有账单文件。</p>';await Promise.all([loadPendingFiles(),loadBatches(),loadReport()])}catch(error){uploadResults.innerHTML=`<div class="upload-result failed"><b>文件夹解析失败</b><small>${escape(error.message)}</small></div>`}finally{scanButton.disabled=false;scanButton.textContent='解析文件夹'}};
refresh.onclick=loadReport;period.onchange=()=>{toggleDates();loadReport()};reportCategory.onchange=renderTransactions;directionFilter.onchange=renderTransactions;transactionFrom.onchange=renderTransactions;transactionTo.onchange=renderTransactions;sourceFilter.onchange=renderTransactions;
tabs.forEach(tab=>tab.onclick=()=>showView(tab.dataset.view));
toggleDates();Promise.all([loadCategories(),loadReport(),loadBatches()]);

document.querySelector('#review-duplicates').onclick=async event=>{
  if(!reviewRange)return;
  const button=event.currentTarget,range={...reviewRange},output=document.querySelector('#duplicate-review');
  button.disabled=true;output.textContent='正在核对…';
  try{
    const candidates=await request('/api/reviews/duplicates?'+new URLSearchParams(range));
    if(range.from!==reviewRange.from||range.to!==reviewRange.to)return;
    output.innerHTML=candidates.length?candidates.map(group=>`<div class="batch"><b>${group.reason==='SAME_CONTENT'?'同来源内容相同':'跨渠道同日同金额'}</b><p>${escape(group.message)}</p>${group.entries.map(row=>`<p>记录 ${escape(row.id)} · ${escape(row.date)} · ${escape(sourceNames[row.source]||row.source)} · ${escape(directionName(row.direction))} · ${yuan(row.amount)}</p>`).join('')}</div>`).join(''):'当前规则未发现疑似重复；跨日入账等情况仍可能需要人工核对。';
  }catch(error){if(range.from===reviewRange.from&&range.to===reviewRange.to)output.textContent=error.message}
  finally{button.disabled=false}
};
