const SUPABASE_URL="https://cwuncmdfxvanflbunlxx.supabase.co";
const SUPABASE_KEY="sb_publishable_mGd-RSSQeGsCMLrsFj5H5g_1xRESlGw";
const SESSION_KEY="hesabyar_admin_session_v1";
let rows=[];

const $=id=>document.getElementById(id);
function session(){try{return JSON.parse(localStorage.getItem(SESSION_KEY)||"null")}catch(e){return null}}
function setSession(s){if(s)localStorage.setItem(SESSION_KEY,JSON.stringify(s));else localStorage.removeItem(SESSION_KEY)}
async function refreshSession(){
  const s=session();
  if(!s?.refresh_token) return false;
  const res=await fetch(SUPABASE_URL+"/auth/v1/token?grant_type=refresh_token",{
    method:"POST",
    headers:{"apikey":SUPABASE_KEY,"Content-Type":"application/json"},
    body:JSON.stringify({refresh_token:s.refresh_token})
  });
  const text=await res.text();
  let data=null;
  try{data=text?JSON.parse(text):null}catch(e){data=null}
  if(!res.ok || !data?.access_token) return false;
  setSession(data);
  return true;
}
async function api(path,options={},retried=false){
  const s=session();
  const headers={"apikey":SUPABASE_KEY,"Content-Type":"application/json",...(options.headers||{})};
  if(s?.access_token)headers.Authorization="Bearer "+s.access_token;
  const res=await fetch(SUPABASE_URL+path,{...options,headers});
  const text=await res.text(); let data=null;
  try{data=text?JSON.parse(text):null}catch(e){data=text}
  if(!res.ok){
    const message=data?.message||data?.msg||data?.error_description||data?.error||"خطا در ارتباط با سرور";
    if(res.status===401 && !retried && await refreshSession()){
      return api(path,options,true);
    }
    throw new Error(message);
  }
  return data;
}
async function signIn(email,password){
  const data=await api("/auth/v1/token?grant_type=password",{method:"POST",body:JSON.stringify({email,password})});
  setSession(data); return data;
}
async function loadDashboard(){
  $("status").textContent="در حال دریافت اطلاعات...";
  const data=await api("/functions/v1/admin-dashboard");
  rows=data.users||[];
  $("usersCount").textContent=fmt(data.summary?.users);
  $("txCount").textContent=fmt(data.summary?.transactions);
  $("income").textContent=fmt(data.summary?.income);
  $("expense").textContent=fmt(data.summary?.expense);
  render();
  $("status").textContent="";
}
function fmt(n){return new Intl.NumberFormat("fa-IR").format(Number(n||0))}
function date(s){if(!s)return "—";const d=new Date(s);return isNaN(d)?s:d.toLocaleDateString("fa-IR")}
function postAdmin(body){
  return api("/functions/v1/admin-dashboard",{method:"POST",body:JSON.stringify(body)});
}
function render(){
  const q=$("search").value.trim().toLowerCase();
  const list=rows.filter(x=>(x.email||"").toLowerCase().includes(q));
  $("usersBody").innerHTML=list.map(x=>'<tr><td class="user-link">'+esc(x.email||"—")+'</td><td>'+date(x.created_at)+'</td><td>'+date(x.last_sign_in_at)+'</td><td>'+fmt(x.transaction_count)+'</td><td>'+fmt(x.income)+'</td><td>'+fmt(x.expense)+'</td><td><button type="button" class="details-btn" data-user-id="'+esc(x.id)+'">مشاهده جزئیات</button></td></tr>').join("");
  document.querySelectorAll(".details-btn").forEach(btn=>{
    btn.addEventListener("click",()=>openUserDetails(btn.dataset.userId));
  });
  $("empty").classList.toggle("hidden",list.length!==0);
}
function esc(s){return String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[c]))}
$("loginForm").addEventListener("submit",async e=>{
  e.preventDefault(); $("loginBtn").disabled=true; $("loginMessage").textContent="در حال ورود...";
  try{await signIn($("email").value.trim(),$("password").value);await showPanel()}
  catch(e){$("loginMessage").textContent=e.message}
  finally{$("loginBtn").disabled=false}
});
$("logoutBtn").onclick=()=>{setSession(null);location.reload()};
$("refreshBtn").onclick=()=>loadDashboard().catch(e=>$("status").textContent=e.message);
$("search").oninput=render;

function openModal(){
  $("userModal").classList.remove("hidden");
  document.body.style.overflow="hidden";
}
function closeModal(){
  $("userModal").classList.add("hidden");
  document.body.style.overflow="";
}
$("closeModal").onclick=closeModal;
$("modalBackdrop").onclick=closeModal;

async function openUserDetails(userId){
  if(!userId)return;
  const user=rows.find(x=>x.id===userId);
  if(!user)return;

  $("detailEmail").textContent=user.email||"کاربر";
  $("detailMeta").textContent="ثبت‌نام: "+date(user.created_at)+"  |  آخرین ورود: "+date(user.last_sign_in_at);
  $("detailActions").innerHTML=
    '<button type="button" class="action-btn" id="toggleUserBtn">'+(user.disabled?"فعال‌سازی کاربر":"غیرفعال کردن کاربر")+'</button>'+
    '<button type="button" class="action-btn secondary-action" id="passwordBtn">تغییر رمز عبور</button>'+
    '<button type="button" class="action-btn export-action" id="exportBtn">خروجی Excel</button>';
  $("toggleUserBtn").onclick=()=>toggleUser(user);
  $("passwordBtn").onclick=()=>changePassword(user);
  $("exportBtn").onclick=()=>exportExcel(user);
  $("detailCount").textContent=fmt(user.transaction_count);
  $("detailIncome").textContent=fmt(user.income);
  $("detailExpense").textContent=fmt(user.expense);
  $("detailBalance").textContent=fmt(Number(user.income||0)-Number(user.expense||0));
  $("detailStatus").textContent="در حال دریافت تراکنش‌ها...";
  $("detailTransactions").innerHTML="";
  $("detailEmpty").classList.add("hidden");
  openModal();

  try{
    const data=await api("/functions/v1/admin-dashboard?user_id="+encodeURIComponent(userId));
    const txs=data.transactions||[];
    $("detailStatus").textContent="";
    $("detailCount").textContent=fmt(txs.length);
    $("detailIncome").textContent=fmt(data.user?.income);
    $("detailExpense").textContent=fmt(data.user?.expense);
    $("detailBalance").textContent=fmt(Number(data.user?.income||0)-Number(data.user?.expense||0));

    $("detailTransactions").innerHTML=txs.map(t=>{
      const income=t.type==="income";
      return '<tr><td>'+esc(t.title||"—")+'</td><td>'+ (income?"درآمد":"هزینه") +'</td><td>'+fmt(t.amount)+'</td><td>'+esc(t.description||"—")+'</td><td>'+date(t.transaction_date||t.created_at)+'</td><td><button type="button" class="delete-tx" data-tx-id="'+esc(t.id)+'">حذف</button></td></tr>';
    }).join("");
    $("detailEmpty").classList.toggle("hidden",txs.length!==0);
    document.querySelectorAll(".delete-tx").forEach(btn=>{
      btn.onclick=()=>deleteTransaction(user.id,btn.dataset.txId);
    });
  }catch(e){
    $("detailStatus").textContent=e.message;
  }
}

async function toggleUser(user){
  const action=user.disabled?"فعال‌سازی":"غیرفعال کردن";
  if(!confirm("آیا مطمئن هستید که می‌خواهید این کاربر را "+action+" کنید؟")) return;
  try{
    await postAdmin({action:"set_status",user_id:user.id,disabled:!user.disabled});
    await loadDashboard();
    const fresh=rows.find(x=>x.id===user.id)||user;
    closeModal();
    openUserDetails(fresh.id);
  }catch(e){alert(e.message)}
}
async function changePassword(user){
  const password=prompt("رمز عبور جدید را وارد کنید (حداقل ۶ کاراکتر):");
  if(password===null)return;
  if(password.length<6){alert("رمز عبور باید حداقل ۶ کاراکتر باشد.");return}
  try{
    await postAdmin({action:"set_password",user_id:user.id,password});
    alert("رمز عبور با موفقیت تغییر کرد.");
  }catch(e){alert(e.message)}
}
async function deleteTransaction(userId,transactionId){
  if(!confirm("این تراکنش حذف شود؟ این عملیات قابل بازگشت نیست."))return;
  try{
    await postAdmin({action:"delete_transaction",user_id:userId,transaction_id:transactionId});
    await loadDashboard();
    await openUserDetails(userId);
  }catch(e){alert(e.message)}
}
function exportExcel(user){
  const rowsToExport=[["عنوان","نوع","مبلغ","توضیحات","تاریخ"]];
  document.querySelectorAll("#detailTransactions tr").forEach(tr=>{
    const cells=[...tr.querySelectorAll("td")].slice(0,5).map(td=>td.innerText.trim());
    if(cells.length)rowsToExport.push(cells);
  });
  const csv="\ufeff"+rowsToExport.map(row=>row.map(v=>'"'+String(v).replace(/"/g,'""')+'"').join(",")).join("\r\n");
  const blob=new Blob([csv],{type:"text/csv;charset=utf-8;"});
  const a=document.createElement("a");
  a.href=URL.createObjectURL(blob);
  a.download="hesabyar-"+(user.email||"user").replace(/[^a-z0-9._-]/gi,"_")+".csv";
  a.click();
  URL.revokeObjectURL(a.href);
}
async function showPanel(){
  const s=session(); if(!s){return}
  $("loginScreen").classList.add("hidden");$("panel").classList.remove("hidden");
  $("adminEmail").textContent=s.user?.email||"";
  try{await loadDashboard()}catch(e){$("status").textContent=e.message}
}
(async()=>{if(session())await showPanel()})();