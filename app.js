const SUPABASE_URL="https://cwuncmdfxvanflbunlxx.supabase.co";
const SUPABASE_KEY="sb_publishable_mGd-RSSQeGsCMLrsFj5H5g_1xRESlGw";
const SESSION_KEY="hesabyar_supabase_session_v1";
let transactions=[],mode=true,editId=null,authMode="login";

const $=id=>document.getElementById(id);
function session(){try{return JSON.parse(localStorage.getItem(SESSION_KEY)||"null")}catch(e){return null}}
function setSession(s){if(s)localStorage.setItem(SESSION_KEY,JSON.stringify(s));else localStorage.removeItem(SESSION_KEY)}
async function api(path,options={}){
 const s=session(),headers={"apikey":SUPABASE_KEY,"Content-Type":"application/json",...(options.headers||{})};
 if(s?.access_token)headers.Authorization="Bearer "+s.access_token;
 const res=await fetch(SUPABASE_URL+path,{...options,headers});
 const text=await res.text();let data=null;try{data=text?JSON.parse(text):null}catch(e){data=text}
 if(!res.ok){const msg=data?.msg||data?.message||data?.error_description||"خطا در ارتباط با سرور";throw new Error(msg)}
 return data;
}
async function signIn(email,password){
 const data=await api("/auth/v1/token?grant_type=password",{method:"POST",body:JSON.stringify({email,password})});
 setSession(data);return data;
}
async function signUp(email,password){
 const data=await api("/auth/v1/signup",{method:"POST",body:JSON.stringify({email,password})});
 if(data.access_token){setSession(data);return data}
 return null;
}
async function refreshSession(){
 const s=session();if(!s?.refresh_token)return null;
 try{const data=await api("/auth/v1/token?grant_type=refresh_token",{method:"POST",body:JSON.stringify({refresh_token:s.refresh_token})});setSession(data);return data}catch(e){setSession(null);return null}
}
async function loadTransactions(){
 const data=await api("/rest/v1/transactions?select=id,title,amount,type,description,transaction_date,created_at&order=created_at.desc");
 transactions=(data||[]).map(x=>({id:x.id,title:x.title,amount:Number(x.amount),income:x.type==="income",category:x.description||"عمومی",date:x.transaction_date||x.created_at}));
 render();
}
function fmt(n){return new Intl.NumberFormat("fa-IR").format(Math.round(n||0))}
function parseDate(s){const d=new Date(s);return isNaN(d)?new Date():d}
function monthMatch(s){const d=parseDate(s),now=new Date();return d.getMonth()===now.getMonth()&&d.getFullYear()===now.getFullYear()}
function render(){
 const income=transactions.filter(x=>x.income).reduce((a,x)=>a+x.amount,0),expense=transactions.filter(x=>!x.income).reduce((a,x)=>a+x.amount,0);
 $("balance").textContent=fmt(income-expense)+" تومان";$("income").textContent=fmt(income);$("expense").textContent=fmt(expense);
 const mi=transactions.filter(x=>x.income&&monthMatch(x.date)).reduce((a,x)=>a+x.amount,0),me=transactions.filter(x=>!x.income&&monthMatch(x.date)).reduce((a,x)=>a+x.amount,0);
 $("monthIncome").textContent=fmt(mi);$("monthExpense").textContent=fmt(me);$("monthProfit").textContent=fmt(mi-me);
 const max=Math.max(mi,me,1);$("incomeBar").style.width=(mi/max*100)+"%";$("expenseBar").style.width=(me/max*100)+"%";
 const q=$("search").value.trim().toLowerCase(),filter=$("filter").value;
 const shown=transactions.map((x,i)=>({x,i})).filter(o=>{const x=o.x,t=(x.title+" "+(x.category||"")).toLowerCase();return(!q||t.includes(q))&&(filter==="all"||(filter==="income"&&x.income)||(filter==="expense"&&!x.income))});
 $("count").textContent="("+fmt(shown.length)+")";
 const box=$("transactions");if(!shown.length){box.innerHTML='<div class="card">تراکنشی با این فیلتر پیدا نشد.</div>';return}
 box.innerHTML=shown.map(o=>{const x=o.x;return '<div class="tx"><div class="tx-top"><div><div class="tx-title">'+esc(x.title)+'</div><div class="meta">'+esc(x.category||"عمومی")+" • "+esc(formatDate(x.date))+'</div></div><div class="amount '+(x.income?"in":"out")+'">'+(x.income?"+":"-")+fmt(x.amount)+'</div></div><div class="tx-actions"><button onclick="editTx('+o.i+')">ویرایش</button><button onclick="deleteTx('+o.i+')">حذف</button></div></div>'}).join("");
}
function formatDate(s){const d=parseDate(s);return isNaN(d.getTime())?s:d.toLocaleDateString("fa-IR")}
function esc(s){return String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[c]))}
function openForm(income,index){mode=income;editId=index===null?null:transactions[index].id;$("dialogTitle").textContent=index===null?(income?"ثبت درآمد":"ثبت هزینه"):"ویرایش تراکنش";const x=index===null?null:transactions[index];$("title").value=x?x.title:"";$("amount").value=x?x.amount:"";$("category").value=x?x.category:"";$("dialog").showModal()}
function editTx(i){openForm(transactions[i].income,i)}
async function deleteTx(i){if(!confirm("این تراکنش حذف شود؟"))return;try{await api("/rest/v1/transactions?id=eq."+encodeURIComponent(transactions[i].id),{method:"DELETE"});await loadTransactions()}catch(e){alert(e.message)}}
$("incomeBtn").onclick=()=>openForm(true,null);$("expenseBtn").onclick=()=>openForm(false,null);$("cancelBtn").onclick=()=>$("dialog").close();$("search").oninput=render;$("filter").onchange=render;
$("form").addEventListener("submit",async e=>{e.preventDefault();const title=$("title").value.trim(),amount=Number(String($("amount").value).replace(/[^0-9]/g,"")),category=$("category").value.trim()||"عمومی";if(!title||!amount||amount<1){alert("عنوان و مبلغ را درست وارد کنید.");return}
 try{const body={type:mode?"income":"expense",amount,title,description:category,transaction_date:new Date().toISOString().slice(0,10)};
 if(editId===null){const s=session();body.user_id=s.user.id;await api("/rest/v1/transactions",{method:"POST",headers:{Prefer:"return=minimal"},body:JSON.stringify(body)})}
 else await api("/rest/v1/transactions?id=eq."+encodeURIComponent(editId),{method:"PATCH",headers:{Prefer:"return=minimal"},body:JSON.stringify(body)});
 $("dialog").close();editId=null;await loadTransactions()}catch(e){alert(e.message)}});
function download(name,content,type){const blob=new Blob([content],{type}),url=URL.createObjectURL(blob),a=document.createElement("a");a.href=url;a.download=name;a.click();URL.revokeObjectURL(url)}
$("exportBtn").onclick=()=>{const rows=[["عنوان","مبلغ","نوع","دسته‌بندی","تاریخ"],...transactions.map(x=>[x.title,x.amount,x.income?"درآمد":"هزینه",x.category||"عمومی",formatDate(x.date)])];const table='<html><head><meta charset="utf-8"></head><body><table border="1">'+rows.map(r=>"<tr>"+r.map(v=>"<td>"+esc(v)+"</td>").join("")+"</tr>").join("")+"</table></body></html>";download("hesabyar-transactions.xls","\\ufeff"+table,"application/vnd.ms-excel;charset=utf-8")};
$("backupBtn").onclick=()=>download("hesabyar-backup.json",JSON.stringify(transactions,null,2),"application/json;charset=utf-8");
$("clearBtn").onclick=async()=>{if(!confirm("همه تراکنش‌های حساب شما حذف شوند؟ این کار قابل برگشت نیست."))return;try{await api("/rest/v1/transactions?id=gt.0",{method:"DELETE"});await loadTransactions()}catch(e){alert(e.message)}};
$("logoutBtn").onclick=()=>{setSession(null);transactions=[];showAuth();};
$("loginTab").onclick=()=>setAuthMode("login");$("signupTab").onclick=()=>setAuthMode("signup");
function setAuthMode(modeName){authMode=modeName;$("loginTab").className=modeName==="login"?"":"outline";$("signupTab").className=modeName==="signup"?"":"outline";$("authSubmit").textContent=modeName==="login"?"ورود":"ثبت‌نام";$("authMessage").textContent=""}
$("authForm").addEventListener("submit",async e=>{e.preventDefault();$("authSubmit").disabled=true;$("authMessage").textContent="در حال اتصال...";try{const email=$("authEmail").value.trim(),password=$("authPassword").value;if(authMode==="login"){await signIn(email,password)}else{const s=await signUp(email,password);if(!s){$("authMessage").textContent="ثبت‌نام انجام شد. اکنون با ایمیل و رمز عبور وارد شوید.";setAuthMode("login");return}}await showApp()}catch(e){$("authMessage").textContent=e.message}finally{$("authSubmit").disabled=false}});
function showAuth(){$("authScreen").classList.remove("hidden");$("appScreen").classList.add("hidden")}
async function showApp(){const s=session();if(!s){showAuth();return} $("authScreen").classList.add("hidden");$("appScreen").classList.remove("hidden");$("userEmail").textContent="• "+(s.user?.email||"");$("status").textContent="در حال دریافت اطلاعات...";try{await loadTransactions();$("status").textContent=""}catch(e){$("status").textContent=e.message;if(String(e.message).toLowerCase().includes("jwt")||String(e.message).includes("جلسه")){if(await refreshSession())return showApp();setSession(null);showAuth()}}}
(async()=>{if(session())await showApp();else showAuth()})();