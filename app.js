const KEY="hesabyar_web_transactions_v1";
let transactions=load();
let mode=true;
let editIndex=null;

function load(){
  try{return JSON.parse(localStorage.getItem(KEY)||"null")||[
    {title:"فروش روزانه",amount:8500000,income:true,category:"فروش",date:"امروز"},
    {title:"خرید مواد اولیه",amount:3200000,income:false,category:"خرید",date:"امروز"}
  ]}catch(e){return[]}
}
function save(){localStorage.setItem(KEY,JSON.stringify(transactions))}
function fmt(n){return new Intl.NumberFormat("fa-IR").format(n)}
function render(){
  let income=transactions.filter(x=>x.income).reduce((a,x)=>a+x.amount,0);
  let expense=transactions.filter(x=>!x.income).reduce((a,x)=>a+x.amount,0);
  document.getElementById("balance").textContent=fmt(income-expense)+" تومان";
  document.getElementById("income").textContent=fmt(income);
  document.getElementById("expense").textContent=fmt(expense);
  const box=document.getElementById("transactions");
  if(!transactions.length){box.innerHTML='<div class="card">هنوز تراکنشی ثبت نشده است.</div>';return}
  box.innerHTML=transactions.map(function(x,i){
    return '<div class="tx"><div class="tx-top"><div><div class="tx-title">'+esc(x.title)+'</div><div class="meta">'+esc(x.category||"عمومی")+" • "+esc(x.date)+'</div></div><div class="amount '+(x.income?"in":"out")+'">'+(x.income?"+":"-")+fmt(x.amount)+'</div></div><div class="tx-actions"><button onclick="editTx('+i+')">ویرایش</button><button onclick="deleteTx('+i+')">حذف</button></div></div>'
  }).join("");
}
function esc(s){return String(s).replace(/[&<>"']/g,function(c){return {"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[c]})}
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
document.getElementById("incomeBtn").onclick=function(){openForm(true,null)};
document.getElementById("expenseBtn").onclick=function(){openForm(false,null)};
document.getElementById("form").addEventListener("submit",function(e){
  e.preventDefault();
  const title=document.getElementById("title").value.trim();
  const amount=Number(String(document.getElementById("amount").value).replace(/[^0-9]/g,""));
  const category=document.getElementById("category").value.trim()||"عمومی";
  if(!title||!amount||amount<1){alert("عنوان و مبلغ را درست وارد کنید.");return}
  const item={title:title,amount:amount,income:mode,category:category,date:new Date().toLocaleString("fa-IR")};
  if(editIndex===null)transactions.unshift(item);else transactions[editIndex]=item;
  save();render();document.getElementById("dialog").close();editIndex=null;
});
render();