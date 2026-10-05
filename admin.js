const SUPABASE_URL="https://cwuncmdfxvanflbunlxx.supabase.co";
const SUPABASE_KEY="sb_publishable_mGd-RSSQeGsCMLrsFj5H5g_1xRESlGw";
const SESSION_KEY="hesabyar_admin_session_v1";
let rows=[];

const $=id=>document.getElementById(id);
function session(){try{return JSON.parse(localStorage.getItem(SESSION_KEY)||"null")}catch(e){return null}}
function setSession(s){if(s)localStorage.setItem(SESSION_KEY,JSON.stringify(s));else localStorage.removeItem(SESSION_KEY)}
async function api(path,options={}){
  const s=session();
  const headers={"apikey":SUPABASE_KEY,"Content-Type":"application/json",...(options.headers||{})};
  if(s?.access_token)headers.Authorization="Bearer "+s.access_token;
  const res=await fetch(SUPABASE_URL+path,{...options,headers});
  const text=await res.text(); let data=null;
  try{data=text?JSON.parse(text):null}catch(e){data=text}
  if(!res.ok)throw new Error(data?.message||data?.msg||data?.error_description||"خطا در ارتباط با سرور");
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
function render(){
  const q=$("search").value.trim().toLowerCase();
  const list=rows.filter(x=>(x.email||"").toLowerCase().includes(q));
  $("usersBody").innerHTML=list.map(x=>'<tr class="clickable-row" data-user-id="'+esc(x.id)+'"><td class="user-link">'+esc(x.email||"—")+'</td><td>'+date(x.created_at)+'</td><td>'+date(x.last_sign_in_at)+'</td><td>'+fmt(x.transaction_count)+'</td><td>'+fmt(x.income)+'</td><td>'+fmt(x.expense)+'</td></tr>').join("");
  document.querySelectorAll(".clickable-row").forEach(row=>{
    row.addEventListener("click",()=>openUserDetails(row.dataset.userId));
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
      return '<tr><td>'+esc(t.title||"—")+'</td><td>'+ (income?"درآمد":"هزینه") +'</td><td>'+fmt(t.amount)+'</td><td>'+esc(t.description||"—")+'</td><td>'+date(t.transaction_date||t.created_at)+'</td></tr>';
    }).join("");
    $("detailEmpty").classList.toggle("hidden",txs.length!==0);
  }catch(e){
    $("detailStatus").textContent=e.message;
  }
}

async function showPanel(){
  const s=session(); if(!s){return}
  $("loginScreen").classList.add("hidden");$("panel").classList.remove("hidden");
  $("adminEmail").textContent=s.user?.email||"";
  try{await loadDashboard()}catch(e){$("status").textContent=e.message}
}
(async()=>{if(session())await showPanel()})();