const KEY="hesabyar_web_transactions_v2";
let transactions=load(),mode=true,editIndex=null;

function load(){
 try{return JSON.parse(localStorage.getItem(KEY)||"null")||JSON.parse(localStorage.getItem("hesabyar_web_transactions_v1")||"null")||[
 {title:"فروش روزانه",amount:8500000,income:true,category:"فروش",date:new Date().toLocaleString("fa-IR")},
 {title:"خرید مواد اولیه",amount:3200000,income:false,category:"خرید",date:new Date().toLocaleString("fa-IR")}
 ]}catch(e){return[]}
}
function save(){localStorage.setItem(KEY,JSON.stringify(transactions))}
function fmt(n){return new Intl.NumberFormat("fa-IR").format(Math.round(n||0))}
function parseDate(s){const d=new Date(s);return isNaN(d)?new Date():d}
function monthMatch(s){const d=parseDate(s),now=new Date();return d.getMonth()===now.getMonth()&&d.getFullYear()===now.getFullYear()}
function render(){
 const income=transactions.filter(x=>x.income).reduce((a,x)=>a+Number(x.amount),0);
 const expense=transactions.filter(x=>!x.income).reduce((a,x)=>a+Number(x.amount),0);
 document.getElementById("balance").textContent=fmt(income-expense)+" تومان";
 document.getElementById("income").textContent=fmt(income);
 document.getElementById("expense").textContent=fmt(expense);

 const mi=transactions.filter(x=>x.income&&monthMatch(x.date)).reduce((a,x)=>a+Number(x.amount),0);
 const me=transactions.filter(x=>!x.income&&monthMatch(x.date)).reduce((a,x)=>a+Number(x.amount),0);
 document.getElementById("monthIncome").textContent=fmt(mi);
 document.getElementById("monthExpense").textContent=fmt(me);
 document.getElementById("monthProfit").textContent=fmt(mi-me);
 const max=Math.max(mi,me,1);
 document.getElementById("incomeBar").style.width=(mi/max*100)+"%";
 document.getElementById("expenseBar").style.width=(me/max*100)+"%";

 const q=document.getElementById("search").value.trim().toLowerCase();
 const filter=document.getElementById("filter").value;
 const shown=transactions.map((x,i)=>({x,i})).filter(o=>{
   const x=o.x,text=(x.title+" "+(x.category||"")).toLowerCase();
   return (!q||text.includes(q))&&(filter==="all"||(filter==="income"&&x.income)||(filter==="expense"&&!x.income));
 });
 document.getElementById("count").textContent="("+fmt(shown.length)+")";
 const box=document.getElementById("transactions");
 if(!shown.length){box.innerHTML='<div class="card">تراکنشی با این فیلتر پیدا نشد.</div>';return}
 box.innerHTML=shown.map(o=>{
   const x=o.x,i=o.i;
   return '<div class="tx"><div class="tx-top"><div><div class="tx-title">'+esc(x.title)+'</div><div class="meta">'+esc(x.category||"عمومی")+" • "+esc(x.date)+'</div></div><div class="amount '+(x.income?"in":"out")+'">'+(x.income?"+":"-")+fmt(x.amount)+'</div></div><div class="tx-actions"><button onclick="editTx('+i+')">ویرایش</button><button onclick="deleteTx('+i+')">حذف</button></div></div>'
 }).join("");
}
function esc(s){return String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[c]))}
function openForm(income,index){
 mode=income;editIndex=index;
 document.getElementById("dialogTitle").textContent=index===null?(income?"ثبت درآمد":"ثبت هزینه"):"ویرایش تراکنش";
 const x=index===null?null:transactions[index];
 document.getElementById("title").value=x?x.title:"";
 document.getElementById("amount").value=x?x.amount:"";
 document.getElementById("category").value=x?x.category:"";
 document.getElementById("dialog").showModal();
}
function editTx(i){openForm(transactions[i].income,i)}
function deleteTx(i){if(confirm("این تراکنش حذف شود؟")){transactions.splice(i,1);save();render()}}
document.getElementById("incomeBtn").onclick=()=>openForm(true,null);
document.getElementById("expenseBtn").onclick=()=>openForm(false,null);
document.getElementById("cancelBtn").onclick=()=>document.getElementById("dialog").close();
document.getElementById("search").oninput=render;
document.getElementById("filter").onchange=render;
document.getElementById("form").addEventListener("submit",e=>{
 e.preventDefault();
 const title=document.getElementById("title").value.trim();
 const amount=Number(String(document.getElementById("amount").value).replace(/[^0-9]/g,""));
 const category=document.getElementById("category").value.trim()||"عمومی";
 if(!title||!amount||amount<1){alert("عنوان و مبلغ را درست وارد کنید.");return}
 const item={title,amount,income:mode,category,date:new Date().toLocaleString("fa-IR")};
 if(editIndex===null)transactions.unshift(item);else transactions[editIndex]=item;
 save();render();document.getElementById("dialog").close();editIndex=null;
});
function download(name,content,type){
 const blob=new Blob([content],{type}),url=URL.createObjectURL(blob),a=document.createElement("a");
 a.href=url;a.download=name;a.click();URL.revokeObjectURL(url);
}
document.getElementById("exportBtn").onclick=()=>{
 const rows=[["عنوان","مبلغ","نوع","دسته‌بندی","تاریخ"],...transactions.map(x=>[x.title,x.amount,x.income?"درآمد":"هزینه",x.category||"عمومی",x.date])];
 const csv="\ufeff"+rows.map(r=>r.map(v=>'"'+String(v).replace(/"/g,'""')+'"').join(",")).join("\n");
 download("hesabyar-transactions.csv",csv,"text/csv;charset=utf-8");
};
document.getElementById("backupBtn").onclick=()=>download("hesabyar-backup.json",JSON.stringify(transactions,null,2),"application/json;charset=utf-8");
document.getElementById("clearBtn").onclick=()=>{
 if(confirm("همه تراکنش‌ها حذف شوند؟ این کار قابل برگشت نیست.")){transactions=[];save();render()}
};
render();